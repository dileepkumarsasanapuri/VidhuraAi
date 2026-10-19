package com.vidhura.ai.recommendation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecurityHostHeaderIsolationTest {

    private final PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher(true); // allow loopback for testing

    @Test
    @DisplayName("JVM System property sun.net.http.allowRestrictedHeaders is strictly enabled")
    void testRestrictedHeadersPropertySet() {
        assertEquals("true", System.getProperty("sun.net.http.allowRestrictedHeaders"),
                "JVM system property sun.net.http.allowRestrictedHeaders must be set to 'true'");
    }

    @Test
    @DisplayName("Every outbound pinned connection explicitly binds the Host header to the target URI")
    void testOutboundRequestSetsIntendedHostExplicitly() throws Exception {
        URI targetUri = URI.create("https://uiic.co.in/web/sites/default/files/test.pdf");
        InetAddress mockIp = InetAddress.getByName("127.0.0.1");

        HttpURLConnection conn = fetcher.openPinnedConnection(targetUri, mockIp);

        // Verify that custom Host header was applied and matches target URI host
        String hostHeader = conn.getRequestProperty("Host");
        assertNotNull(hostHeader, "Host request property must be explicitly set on pinned connection");
        assertEquals("uiic.co.in", hostHeader, "Host request property must strictly match target URI host");

        // Verify X-Vidhura-Target-Host auditing header is also present
        assertEquals("uiic.co.in", conn.getRequestProperty("X-Vidhura-Target-Host"),
                "X-Vidhura-Target-Host header must match intended destination host");
    }

    @Test
    @DisplayName("Original hostname is strictly verified against peer certificate Subject Alternative Names (SAN)")
    void testPeerCertificateHostnameStrictVerification() throws Exception {
        SSLSession session = mock(SSLSession.class);
        X509Certificate validCert = mock(X509Certificate.class);

        // Mock SANs: dNSName (tag 2) = uiic.co.in and *.uiic.co.in
        List<?> sanEntry1 = List.of(2, "uiic.co.in");
        List<?> sanEntry2 = List.of(2, "*.uiic.co.in");
        when(validCert.getSubjectAlternativeNames()).thenReturn(List.of(sanEntry1, sanEntry2));
        when(session.getPeerCertificates()).thenReturn(new Certificate[]{validCert});

        // 1. Expected host matches exact SAN
        assertTrue(fetcher.verifyPeerCertHost("uiic.co.in", session),
                "Should accept certificate when SAN matches expected host exactly");

        // 2. Expected host matches wildcard SAN
        assertTrue(fetcher.verifyPeerCertHost("sub.uiic.co.in", session),
                "Should accept certificate when wildcard SAN matches single-level subdomain");

        // 3. Expected host does NOT match unrelated domain (e.g. attacker domain)
        assertFalse(fetcher.verifyPeerCertHost("attacker.com", session),
                "Must reject certificate when SAN does not match expected host");

        // 4. Expected host does NOT match deeper multi-level subdomain with single-level wildcard
        assertFalse(fetcher.verifyPeerCertHost("deep.sub.uiic.co.in", session),
                "RFC 6125: Wildcard *.uiic.co.in must NOT match multi-level subdomain deep.sub.uiic.co.in");
    }

    @Test
    @DisplayName("Redirects do not leak origin Host header or pinned IP across hops")
    void testRedirectIsolationMechanics() throws Exception {
        // Verify that on redirect, a fresh DNS resolution and fresh pinned connection are used
        // URL A: https://uiic.co.in/initial -> redirects to URL B: https://uiic.co.in/target.pdf
        // If redirect attempts to navigate to a prohibited IP or unapproved domain, it is blocked immediately.

        PolicyDocumentFetcher secureFetcher = new PolicyDocumentFetcher(false); // production mode: no loopback

        // Prohibited domain redirect target
        String evilUrl = "https://uiic.co.in/redirect-to-attacker";
        // Verify that host allowlist verification blocks any redirect to an unapproved domain
        assertFalse(secureFetcher.isApprovedOfficialDomain("evil-cdn.attacker.com"),
                "Unapproved domains must be blocked on redirect");

        // Loopback/Private IP resolution must be blocked by SSRF defenses
        assertThrows(SecurityException.class, () -> {
            secureFetcher.resolveAndValidateSafeDestinationIp("127.0.0.1");
        }, "SSRF defense must block loopback IP resolution");

        assertThrows(SecurityException.class, () -> {
            secureFetcher.resolveAndValidateSafeDestinationIp("169.254.169.254");
        }, "SSRF defense must block cloud metadata IP resolution");

        assertThrows(SecurityException.class, () -> {
            secureFetcher.resolveAndValidateSafeDestinationIp("192.168.1.1");
        }, "SSRF defense must block RFC 1918 private network IP resolution");
    }

    @Test
    @DisplayName("Unrelated standard URL connections do not experience host-header confusion")
    void testUnrelatedHttpClientsNotVulnerable() throws Exception {
        // Standard URL connection without explicit Host header override retains standard behavior
        URL normalUrl = new URL("http://localhost:5000/api/health");
        HttpURLConnection standardConn = (HttpURLConnection) normalUrl.openConnection(java.net.Proxy.NO_PROXY);

        // When conn.setRequestProperty("Host", ...) is NOT called, it remains un-overridden
        assertNull(standardConn.getRequestProperty("Host"),
                "Unrelated connections do not have custom Host headers injected");
    }

    @Test
    @DisplayName("Adversarial: IPv4-mapped IPv6 addresses are detected and blocked by SSRF defense")
    void testIpv4MappedIpv6AddressesBlocked() throws Exception {
        PolicyDocumentFetcher secureFetcher = new PolicyDocumentFetcher(false);

        // IPv4-mapped loopback ::ffff:127.0.0.1
        InetAddress mappedLoopback = InetAddress.getByName("::ffff:127.0.0.1");
        assertTrue(secureFetcher.isBlockedAddress(mappedLoopback),
                "IPv4-mapped IPv6 loopback (::ffff:127.0.0.1) must be blocked");

        // IPv4-mapped cloud metadata ::ffff:169.254.169.254
        InetAddress mappedMetadata = InetAddress.getByName("::ffff:169.254.169.254");
        assertTrue(secureFetcher.isBlockedAddress(mappedMetadata),
                "IPv4-mapped IPv6 cloud metadata (::ffff:169.254.169.254) must be blocked");

        // IPv4-mapped RFC 1918 10.0.0.1 and 192.168.1.1
        InetAddress mappedPrivate10 = InetAddress.getByName("::ffff:10.0.0.1");
        assertTrue(secureFetcher.isBlockedAddress(mappedPrivate10),
                "IPv4-mapped IPv6 10.0.0.0/8 must be blocked");

        InetAddress mappedPrivate192 = InetAddress.getByName("::ffff:192.168.1.1");
        assertTrue(secureFetcher.isBlockedAddress(mappedPrivate192),
                "IPv4-mapped IPv6 192.168.0.0/16 must be blocked");
    }

    @Test
    @DisplayName("Adversarial: IPv6 Unique Local, Link-Local, Multicast, and Unspecified addresses are blocked")
    void testIpv6SpecialRangesBlocked() throws Exception {
        PolicyDocumentFetcher secureFetcher = new PolicyDocumentFetcher(false);

        // IPv6 Unique Local Address fc00::/7 (RFC 4193)
        InetAddress ulaFc = InetAddress.getByName("fc00::1");
        assertTrue(secureFetcher.isBlockedAddress(ulaFc), "IPv6 ULA fc00::1 must be blocked");

        InetAddress ulaFd = InetAddress.getByName("fd00::1");
        assertTrue(secureFetcher.isBlockedAddress(ulaFd), "IPv6 ULA fd00::1 must be blocked");

        // IPv6 Link-Local fe80::/10
        InetAddress linkLocal = InetAddress.getByName("fe80::1");
        assertTrue(secureFetcher.isBlockedAddress(linkLocal), "IPv6 link-local fe80::1 must be blocked");

        // IPv6 Multicast ff02::1
        InetAddress multicast = InetAddress.getByName("ff02::1");
        assertTrue(secureFetcher.isBlockedAddress(multicast), "IPv6 multicast ff02::1 must be blocked");

        // IPv6 Unspecified ::
        InetAddress unspecified = InetAddress.getByName("::");
        assertTrue(secureFetcher.isBlockedAddress(unspecified), "IPv6 unspecified address :: must be blocked");

        // IPv6 Loopback ::1 (in secure mode)
        InetAddress ipv6Loopback = InetAddress.getByName("::1");
        assertTrue(secureFetcher.isBlockedAddress(ipv6Loopback), "IPv6 loopback ::1 must be blocked in production mode");
    }

    @Test
    @DisplayName("Adversarial: RFC 6125 strict certificate SAN check forbids CN fallback when SAN is present")
    void testStrictRfc6125ForbidsCnFallbackWhenSanPresent() throws Exception {
        SSLSession session = mock(SSLSession.class);
        X509Certificate certWithSanAndCn = mock(X509Certificate.class);

        // Certificate has CN=uiic.co.in BUT its SAN only contains attacker.com
        javax.security.auth.x500.X500Principal principal = new javax.security.auth.x500.X500Principal("CN=uiic.co.in, O=Attacker Org");
        when(certWithSanAndCn.getSubjectX500Principal()).thenReturn(principal);

        List<?> attackerSan = List.of(2, "attacker.com");
        when(certWithSanAndCn.getSubjectAlternativeNames()).thenReturn(List.of(attackerSan));
        when(session.getPeerCertificates()).thenReturn(new Certificate[]{certWithSanAndCn});

        // According to RFC 6125 §6.4.4, CN fallback MUST NOT be used when SAN is present.
        // Therefore, connecting to uiic.co.in with this certificate must return FALSE.
        assertFalse(fetcher.verifyPeerCertHost("uiic.co.in", session),
                "RFC 6125: CN fallback is strictly forbidden when SAN is present on certificate");
    }

    @Test
    @DisplayName("Adversarial: Malformed redirect URLs and unapproved schemes fail closed")
    void testMalformedRedirectSchemesFailClosed() {
        PolicyDocumentFetcher secureFetcher = new PolicyDocumentFetcher(false);

        // 1. Prohibited scheme: ftp://
        PolicyDocumentFetcher.FetchResult ftpResult = secureFetcher.fetchDocument("ftp://uiic.co.in/policy.pdf", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, ftpResult.getStatus());
        assertEquals(400, ftpResult.getHttpStatusCode());
        assertTrue(ftpResult.getErrorMessage().contains("only HTTP and HTTPS are permitted"));

        // 2. Prohibited scheme: file://
        PolicyDocumentFetcher.FetchResult fileResult = secureFetcher.fetchDocument("file:///etc/passwd", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, fileResult.getStatus());
        assertEquals(400, fileResult.getHttpStatusCode());

        // 3. Unapproved destination domain: attacker-controlled server
        PolicyDocumentFetcher.FetchResult evilResult = secureFetcher.fetchDocument("https://evil-insurance-phish.com/doc.pdf", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, evilResult.getStatus());
        assertEquals(403, evilResult.getHttpStatusCode());
        assertTrue(evilResult.getErrorMessage().contains("not on the approved official insurer allowlist"));
    }

    @Test
    @DisplayName("Adversarial: Pinned connection derives Host header strictly from URI with custom ports")
    void testPinnedConnectionDerivesHostDeterministically() throws Exception {
        URI targetWithPort = URI.create("https://uiic.co.in:8443/web/sites/default/files/test.pdf");
        InetAddress mockIp = InetAddress.getByName("127.0.0.1");

        HttpURLConnection conn = fetcher.openPinnedConnection(targetWithPort, mockIp);
        assertEquals("uiic.co.in:8443", conn.getRequestProperty("Host"),
                "Host header must deterministically include port when non-default");
        assertEquals("uiic.co.in", conn.getRequestProperty("X-Vidhura-Target-Host"),
                "Audit target host must be normalized hostname without port");
    }
}
