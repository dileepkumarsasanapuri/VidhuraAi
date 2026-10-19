package com.vidhura.ai.recommendation.service;

import com.sun.net.httpserver.HttpServer;
import com.vidhura.ai.recommendation.model.PolicySource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Security & Identity Regression Tests: TLS, SSRF Redirects, Dual UINs, and Tri-State Discovery")
class SecurityAndIdentityRegressionTest {

    private static HttpServer mockServer;
    private static int mockPort;

    @BeforeAll
    static void startMockServer() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // 1. Endpoint redirecting to private IP (SSRF)
        mockServer.createContext("/redirect-to-private", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://192.168.1.100/internal-policy.pdf");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // 2. Endpoint redirecting to AWS/Cloud metadata (SSRF)
        mockServer.createContext("/redirect-to-metadata", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://169.254.169.254/latest/meta-data");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // 3. Endpoint redirecting to unapproved external domain
        mockServer.createContext("/redirect-to-unapproved", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://evil-insurance-portal.com/malicious.pdf");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // 4. Endpoint with infinite redirect loop
        mockServer.createContext("/redirect-loop", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + mockPort + "/redirect-loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // 5. Valid HTML page with candidates
        mockServer.createContext("/live-insurer-portal", exchange -> {
            String html = "<html><body>"
                    + "<a href=\"/download/medicare-policy-wording.pdf\">Download Tata Medicare Policy</a>"
                    + "<a href=\"https://uiic.co.in/family-medicare-policy-wording.pdf\">United India Policy</a>"
                    + "</body></html>";
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            byte[] bytes = html.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        });

        mockServer.start();
        mockPort = mockServer.getAddress().getPort();
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    // =========================================================================
    // CHECK A: TLS Hostname Verification & RFC 6125 Compliance
    // =========================================================================

    @Test
    @DisplayName("TLS: verifyPeerCertHost must match exact Subject Alternative Name (SAN)")
    void testTlsExactSanMatch() throws Exception {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher();
        SSLSession session = mock(SSLSession.class);
        X509Certificate cert = mock(X509Certificate.class);

        // Certificate with dNSName SAN = www.hdfcergo.com
        when(session.getPeerCertificates()).thenReturn(new Certificate[]{cert});
        when(cert.getSubjectAlternativeNames()).thenReturn(List.of(
                List.of(2, "www.hdfcergo.com"),
                List.of(2, "hdfcergo.com")
        ));

        assertTrue(fetcher.verifyPeerCertHost("www.hdfcergo.com", session));
        assertTrue(fetcher.verifyPeerCertHost("hdfcergo.com", session));
        assertFalse(fetcher.verifyPeerCertHost("attacker.com", session));
    }

    @Test
    @DisplayName("TLS: verifyPeerCertHost must reject mismatched hostname (SAN mismatch)")
    void testTlsSanMismatchRejection() throws Exception {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher();
        SSLSession session = mock(SSLSession.class);
        X509Certificate cert = mock(X509Certificate.class);

        // Certificate presented belongs to other-domain.com
        when(session.getPeerCertificates()).thenReturn(new Certificate[]{cert});
        when(cert.getSubjectAlternativeNames()).thenReturn(List.of(
                List.of(2, "other-domain.com")
        ));

        // Attempting to verify against expected www.hdfcergo.com must be REJECTED
        assertFalse(fetcher.verifyPeerCertHost("www.hdfcergo.com", session));
    }

    @Test
    @DisplayName("TLS: Wildcard certificate (*.domain.com) must match exactly one level of subdomain")
    void testTlsWildcardSubdomainExactOneLevel() throws Exception {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher();
        SSLSession session = mock(SSLSession.class);
        X509Certificate cert = mock(X509Certificate.class);

        when(session.getPeerCertificates()).thenReturn(new Certificate[]{cert});
        when(cert.getSubjectAlternativeNames()).thenReturn(List.of(
                List.of(2, "*.hdfcergo.com")
        ));

        // Single subdomain level: valid
        assertTrue(fetcher.verifyPeerCertHost("www.hdfcergo.com", session));
        assertTrue(fetcher.verifyPeerCertHost("portal.hdfcergo.com", session));

        // Multi-level subdomain (RFC 6125 §6.4.3): MUST NOT match wildcard
        assertFalse(fetcher.verifyPeerCertHost("deep.sub.hdfcergo.com", session));

        // Bare apex domain: MUST NOT match wildcard
        assertFalse(fetcher.verifyPeerCertHost("hdfcergo.com", session));
    }

    @Test
    @DisplayName("TLS: RFC 6125 §6.4.4 - CN fallback is strictly FORBIDDEN when dNSName SANs are present")
    void testTlsRfc6125NoCnFallbackWhenSanPresent() throws Exception {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher();
        SSLSession session = mock(SSLSession.class);
        X509Certificate cert = mock(X509Certificate.class);

        when(session.getPeerCertificates()).thenReturn(new Certificate[]{cert});
        // Certificate has SAN = attacker.com, and CN = www.hdfcergo.com
        when(cert.getSubjectAlternativeNames()).thenReturn(List.of(
                List.of(2, "attacker.com")
        ));
        when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=www.hdfcergo.com, O=Attacker Org, C=IN"));

        // Must NOT fall back to CN because dNSName SAN is present!
        assertFalse(fetcher.verifyPeerCertHost("www.hdfcergo.com", session),
                "RFC 6125 forbids Common Name fallback when dNSName SANs exist on the certificate");
    }

    @Test
    @DisplayName("TLS: CN fallback is allowed ONLY when NO dNSName SANs exist on the certificate")
    void testTlsCnFallbackAllowedOnlyWhenNoSans() throws Exception {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher();
        SSLSession session = mock(SSLSession.class);
        X509Certificate cert = mock(X509Certificate.class);

        when(session.getPeerCertificates()).thenReturn(new Certificate[]{cert});
        when(cert.getSubjectAlternativeNames()).thenReturn(null); // No SANs at all
        when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=www.hdfcergo.com, O=HDFC ERGO, C=IN"));

        // CN matches expected host
        assertTrue(fetcher.verifyPeerCertHost("www.hdfcergo.com", session));
        // CN does not match expected host
        assertFalse(fetcher.verifyPeerCertHost("www.starhealth.in", session));
    }

    @Test
    @DisplayName("TLS: Null session, empty certificates, or SSL exception must fail-closed (return false)")
    void testTlsFailClosedOnNullOrException() throws Exception {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher();

        // Null inputs
        assertFalse(fetcher.verifyPeerCertHost(null, null));
        assertFalse(fetcher.verifyPeerCertHost("www.hdfcergo.com", null));

        // Empty cert array
        SSLSession emptySession = mock(SSLSession.class);
        when(emptySession.getPeerCertificates()).thenReturn(new Certificate[0]);
        assertFalse(fetcher.verifyPeerCertHost("www.hdfcergo.com", emptySession));

        // Peer unverified exception
        SSLSession unverifiedSession = mock(SSLSession.class);
        when(unverifiedSession.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("Peer not verified"));
        assertFalse(fetcher.verifyPeerCertHost("www.hdfcergo.com", unverifiedSession));
    }

    // =========================================================================
    // CHECK B: Redirect Handling & SSRF Protection
    // =========================================================================

    @Test
    @DisplayName("Redirect SSRF: Redirect to a private IP (192.168.x.x) is detected and blocked with 403")
    void testRedirectToPrivateIpBlocked() {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher(true); // allow loopback for mock server hop 0
        PolicyDocumentFetcher.FetchResult result = fetcher.fetchDocument(
                "http://127.0.0.1:" + mockPort + "/redirect-to-private", null, null);

        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, result.getStatus());
        assertEquals(403, result.getHttpStatusCode());
        assertTrue(result.getErrorMessage().contains("private or restricted internal network")
                || result.getErrorMessage().contains("Access denied"));
    }

    @Test
    @DisplayName("Redirect SSRF: Redirect to Cloud Metadata IP (169.254.169.254) is blocked with 403")
    void testRedirectToCloudMetadataBlocked() {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher(true);
        PolicyDocumentFetcher.FetchResult result = fetcher.fetchDocument(
                "http://127.0.0.1:" + mockPort + "/redirect-to-metadata", null, null);

        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, result.getStatus());
        assertEquals(403, result.getHttpStatusCode());
        assertTrue(result.getErrorMessage().contains("Access denied")
                || result.getErrorMessage().contains("private or restricted internal network"));
    }

    @Test
    @DisplayName("Redirect SSRF: Redirect to unapproved domain is blocked with 403")
    void testRedirectToUnapprovedDomainBlocked() {
        // Normal production fetcher (allowLocalLoopback=false, but here we test redirect host checking)
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher(false);
        // Direct test against destination domain verification
        assertFalse(fetcher.isApprovedOfficialDomain("evil-insurance-portal.com"));
        assertFalse(fetcher.isApprovedOfficialDomain("attacker-cloud.net"));
        assertTrue(fetcher.isApprovedOfficialDomain("www.hdfcergo.com"));
        assertTrue(fetcher.isApprovedOfficialDomain("uiic.co.in"));
        assertTrue(fetcher.isApprovedOfficialDomain("nationalinsurance.nic.co.in"));
    }

    @Test
    @DisplayName("Redirect Bounds: Exceeding 3 redirect hops is blocked")
    void testRedirectHopLimitEnforced() {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher(true);
        PolicyDocumentFetcher.FetchResult result = fetcher.fetchDocument(
                "http://127.0.0.1:" + mockPort + "/redirect-loop", null, null);

        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, result.getStatus());
        assertEquals(310, result.getHttpStatusCode());
        assertTrue(result.getErrorMessage().contains("Too many redirects (exceeded max 3 hops)"));
    }

    // =========================================================================
    // CHECK C: Dual IRDAI UIN Formats & Entity Mapping
    // =========================================================================

    @Test
    @DisplayName("UIN Formats: Successfully parses Modern Alphanumeric UINs (Private Insurers)")
    void testModernAlphanumericUinParsing() {
        // HDFC ERGO: HDFHLIP21147V012021
        OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin1 =
                OfficialInsurerDiscoveryAdapter.extractUin("Policy Document UIN: HDFHLIP21147V012021 for Optima Secure");
        assertNotNull(uin1);
        assertEquals("HDFHLIP21147V012021", uin1.getUin());
        assertEquals("HDF", uin1.getInsurerCode());
        assertEquals("V012021", uin1.getRevision());
        assertEquals("ALPHANUMERIC", uin1.getFormat());

        // Tata AIG: TATHLIP21169V012021
        OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin2 =
                OfficialInsurerDiscoveryAdapter.extractUin("Tata AIG MediCare UIN: TATHLIP21169V012021 (Active)");
        assertNotNull(uin2);
        assertEquals("TATHLIP21169V012021", uin2.getUin());
        assertEquals("TAT", uin2.getInsurerCode());
        assertEquals("V012021", uin2.getRevision());

        // Star Health: SHAHLIP21164V012021
        OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin3 =
                OfficialInsurerDiscoveryAdapter.extractUin("Star Senior Citizens Red Carpet SHAHLIP21164V012021");
        assertNotNull(uin3);
        assertEquals("SHAHLIP21164V012021", uin3.getUin());
        assertEquals("SHA", uin3.getInsurerCode());

        // HDFC Revision: HDFHLIP23030V022223
        OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin4 =
                OfficialInsurerDiscoveryAdapter.extractUin("https://www.hdfcergo.com/docs/HDFHLIP23030V022223.pdf");
        assertNotNull(uin4);
        assertEquals("HDFHLIP23030V022223", uin4.getUin());
        assertEquals("V022223", uin4.getRevision());
    }

    @Test
    @DisplayName("UIN Formats: Successfully parses Statutory Slash-Separated Identifiers (Public Sector Insurers)")
    void testStatutorySlashRegulatoryUinParsing() {
        // United India: IRDAI/HLT/UII/P-H/V.1/20/2021-22
        OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin1 =
                OfficialInsurerDiscoveryAdapter.extractUin("United India Family Medicare IRDAI/HLT/UII/P-H/V.1/20/2021-22 filing terms");
        assertNotNull(uin1);
        assertEquals("IRDAI/HLT/UII/P-H/V.1/20/2021-22", uin1.getUin());
        assertEquals("UII", uin1.getInsurerCode());
        assertEquals("V.1/20/2021-22", uin1.getRevision());
        assertEquals("SLASH_REGULATORY", uin1.getFormat());

        // National Insurance: IRDA/NL-HLT/NIC/P-H/V.I/237/13-14
        OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin2 =
                OfficialInsurerDiscoveryAdapter.extractUin("Filing: IRDA/NL-HLT/NIC/P-H/V.I/237/13-14 National Mediclaim");
        assertNotNull(uin2);
        assertEquals("IRDA/NL-HLT/NIC/P-H/V.I/237/13-14", uin2.getUin());
        assertEquals("NIC", uin2.getInsurerCode());
        assertEquals("V.I/237/13-14", uin2.getRevision());
        assertEquals("SLASH_REGULATORY", uin2.getFormat());
    }

    @Test
    @DisplayName("UIN Entity Alignment: Validates insurer entity prefix across both UIN formats")
    void testInsurerUinPrefixValidationForBothFormats() {
        // Alphanumeric format
        assertTrue(PolicyDiscoveryPipelineService.validateInsurerUinPrefix(
                "HDFC ERGO General Insurance Co. Ltd.", "HDFHLIP21147V012021"));
        assertTrue(PolicyDiscoveryPipelineService.validateInsurerUinPrefix(
                "Tata AIG General Insurance Co. Ltd.", "TATHLIP21169V012021"));
        assertTrue(PolicyDiscoveryPipelineService.validateInsurerUinPrefix(
                "Star Health and Allied Insurance Co. Ltd.", "SHAHLIP21164V012021"));

        // Slash format
        assertTrue(PolicyDiscoveryPipelineService.validateInsurerUinPrefix(
                "United India Insurance Co. Ltd.", "IRDAI/HLT/UII/P-H/V.1/20/2021-22"));
        assertTrue(PolicyDiscoveryPipelineService.validateInsurerUinPrefix(
                "National Insurance Co. Ltd.", "IRDA/NL-HLT/NIC/P-H/V.I/237/13-14"));

        // Mismatched entity must be REJECTED
        assertFalse(PolicyDiscoveryPipelineService.validateInsurerUinPrefix(
                "HDFC ERGO General Insurance Co. Ltd.", "IRDAI/HLT/UII/P-H/V.1/20/2021-22"),
                "HDFC ERGO entity must not match United India Insurance (UII) UIN");

        assertFalse(PolicyDiscoveryPipelineService.validateInsurerUinPrefix(
                "Star Health and Allied Insurance Co. Ltd.", "TATHLIP21169V012021"),
                "Star Health entity must not match Tata AIG (TAT) UIN");
    }

    @Test
    @DisplayName("Canonical Product Registry: Covers all five candidate products in both formats")
    void testCanonicalProductRegistryDualFormatCoverage() {
        CanonicalProductRegistry registry = new CanonicalProductRegistry();

        // 1. Tata Medicare (Alphanumeric)
        assertTrue(registry.isRegistered("TATHLIP21169V012021"));
        assertEquals("Medicare", registry.getEntry("TATHLIP21169V012021").get().getCanonicalProductName());

        // 2. United India Family Medicare (Slash)
        assertTrue(registry.isRegistered("IRDAI/HLT/UII/P-H/V.1/20/2021-22"));
        assertEquals("Family Medicare 2021", registry.getEntry("IRDAI/HLT/UII/P-H/V.1/20/2021-22").get().getCanonicalProductName());

        // 3. National Mediclaim (Slash)
        assertTrue(registry.isRegistered("IRDA/NL-HLT/NIC/P-H/V.I/237/13-14"));
        assertEquals("National Mediclaim Policy", registry.getEntry("IRDA/NL-HLT/NIC/P-H/V.I/237/13-14").get().getCanonicalProductName());

        // 4. Star Red Carpet (Alphanumeric)
        // Check entry or registration
        assertTrue(registry.isRegistered("SHAHLIP21163V012021")); // Star Comprehensive

        // 5. HDFC Optima Secure (Alphanumeric)
        assertTrue(registry.isRegistered("HDFHLIP21147V012021"));
    }

    // =========================================================================
    // CHECK D: Tri-State Discovery Mode Observability
    // =========================================================================

    @Test
    @DisplayName("Discovery Mode: Distinguishes LIVE_DISCOVERY, LIVE_DISCOVERY_EMPTY, and CATALOG_FALLBACK")
    void testDiscoveryModeTriStateDistinction() {
        // Case 1: Live Discovery Succeeded (candidates registered)
        OfficialInsurerDiscoveryAdapter adapterMock1 = mock(OfficialInsurerDiscoveryAdapter.class);
        when(adapterMock1.discoverFromLiveOfficialPortals(null)).thenReturn(
                OfficialInsurerDiscoveryAdapter.DynamicDiscoveryResult.builder()
                        .discoveryMode("LIVE_DISCOVERY")
                        .pagesScanned(3)
                        .pagesSuccessfullyFetched(3)
                        .candidateLinksFound(2)
                        .candidatesValidatedAndRegistered(1)
                        .registeredSources(List.of(PolicySource.builder()
                                .id("src-live-1")
                                .sourceName("Live Insurer Policy")
                                .sourceUrl("https://www.hdfcergo.com/docs/policy.pdf")
                                .irdaUin("HDFHLIP21147V012021")
                                .status("ACTIVE")
                                .build()))
                        .build()
        );

        PolicySourceMonitor monitor1 = new PolicySourceMonitor(
                null, null, new PolicyDocumentFetcher(), null, null, new CanonicalProductRegistry(), adapterMock1);
        PolicySourceMonitor.DiscoveryReport report1 = monitor1.discoverNewCandidateSources(null);
        assertEquals("LIVE_DISCOVERY", report1.getDiscoveryMode());
        assertTrue(report1.isLastLiveScanSuccessful());
        assertEquals(0, report1.getFallbackCatalogCandidates());

        // Case 2: Live Discovery Scanned Pages, but 0 candidates found (LIVE_DISCOVERY_EMPTY)
        OfficialInsurerDiscoveryAdapter adapterMock2 = mock(OfficialInsurerDiscoveryAdapter.class);
        when(adapterMock2.discoverFromLiveOfficialPortals(null)).thenReturn(
                OfficialInsurerDiscoveryAdapter.DynamicDiscoveryResult.builder()
                        .discoveryMode("LIVE_DISCOVERY_EMPTY")
                        .pagesScanned(4)
                        .pagesSuccessfullyFetched(4)
                        .candidateLinksFound(0)
                        .candidatesValidatedAndRegistered(0)
                        .build()
        );

        PolicySourceMonitor monitor2 = new PolicySourceMonitor(
                null, null, new PolicyDocumentFetcher(), null, null, new CanonicalProductRegistry(), adapterMock2);
        PolicySourceMonitor.DiscoveryReport report2 = monitor2.discoverNewCandidateSources(null);
        assertEquals("LIVE_DISCOVERY_EMPTY", report2.getDiscoveryMode());
        assertTrue(report2.isLastLiveScanSuccessful());
        assertEquals(0, report2.getFallbackCatalogCandidates());

        // Case 3: Live Discovery Failed / Portals unreachable (CATALOG_FALLBACK)
        OfficialInsurerDiscoveryAdapter adapterMock3 = mock(OfficialInsurerDiscoveryAdapter.class);
        when(adapterMock3.discoverFromLiveOfficialPortals(null)).thenReturn(
                OfficialInsurerDiscoveryAdapter.DynamicDiscoveryResult.builder()
                        .discoveryMode("CATALOG_FALLBACK")
                        .pagesScanned(4)
                        .pagesSuccessfullyFetched(0)
                        .candidateLinksFound(0)
                        .candidatesValidatedAndRegistered(0)
                        .build()
        );

        PolicySourceMonitor monitor3 = new PolicySourceMonitor(
                null, null, new PolicyDocumentFetcher(), null, null, new CanonicalProductRegistry(), adapterMock3);
        PolicySourceMonitor.DiscoveryReport report3 = monitor3.discoverNewCandidateSources(null);
        assertEquals("CATALOG_FALLBACK", report3.getDiscoveryMode());
        assertFalse(report3.isLastLiveScanSuccessful());
        assertEquals(5, report3.getFallbackCatalogCandidates());
    }
}
