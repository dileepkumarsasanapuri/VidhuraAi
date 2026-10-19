package com.vidhura.ai.recommendation.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

/**
 * Downloads permitted policy documents over HTTP/HTTPS with conditional caching and strict SSRF defenses.
 *
 * Security & Reliability Features:
 * 1. SSRF & DNS Rebinding Protection: Validates that resolved IP addresses are neither loopback,
 *    site-local (RFC 1918), link-local, carrier-grade NAT, nor cloud metadata addresses (169.254.169.254).
 * 2. Safe Redirect Follower: Re-checks target IP on every redirect hop (max 3 hops) before connecting.
 * 3. Protocol Whitelist: Strictly enforces 'http' and 'https' protocols (rejects file://, gopher://, etc.).
 * 4. HTTP conditional headers (If-None-Match, If-Modified-Since) to minimize bandwidth.
 * 5. Streaming SHA-256 checksum calculation for exact binary deduplication.
 * 6. Strict byte limits (50 MB max) and PDF signature verification (%PDF-).
 */
@Slf4j
@Service
public class PolicyDocumentFetcher {

    static {
        // Enable setting Host header on pinned socket connections in HttpURLConnection
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 (VidhuraAI-DocumentDiscovery/1.0; +https://vidhura.ai; regulatory-compliance@vidhura.ai)";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB safety ceiling
    private static final int MAX_REDIRECT_HOPS = 3;

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "169.254.169.254",
            "metadata.google.internal",
            "instance-data"
    );

    @Value("${vidhura.fetcher.allow-local-loopback:false}")
    private boolean allowLocalLoopback = false;

    public PolicyDocumentFetcher() {
    }

    public PolicyDocumentFetcher(boolean allowLocalLoopback) {
        this.allowLocalLoopback = allowLocalLoopback;
    }

    public void setAllowLocalLoopback(boolean allow) {
        this.allowLocalLoopback = allow;
    }

    public enum FetchStatus {
        FETCHED,
        NOT_MODIFIED,
        FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchResult {
        private FetchStatus status;
        private int httpStatusCode;
        private byte[] documentBytes;
        private String sha256Checksum;
        private long fileSizeBytes;
        private String etag;
        private String lastModifiedHeader;
        private String contentType;
        private String errorMessage;
        private Instant fetchedAt;
    }

    /**
     * Verifies if a hostname belongs to an approved official insurer or IRDAI regulatory domain.
     */
    public boolean isApprovedOfficialDomain(String host) {
        if (host == null || host.isBlank()) return false;
        String cleanHost = host.trim().toLowerCase();
        return PolicySourceMonitor.APPROVED_OFFICIAL_DOMAINS.stream()
                .anyMatch(domain -> cleanHost.equals(domain) || cleanHost.endsWith("." + domain));
    }

    public boolean isIpAddressOrLocal(String host) {
        if (host == null) return false;
        String h = host.trim().toLowerCase();
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local")) return true;
        // Check IPv4 (numeric with dots)
        if (h.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) return true;
        // Check IPv6 (contains colons or brackets)
        if (h.contains(":") || h.startsWith("[") || h.endsWith("]")) return true;
        return false;
    }

    /**
     * Checks if a target host resolves to a private, loopback, link-local, or cloud metadata IP address.
     */
    public boolean isBlockedHostOrIp(String host) {
        try {
            resolveAndValidateSafeDestinationIp(host);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isBlockedAddress(InetAddress addr) {
        if (addr == null) return true;
        if (addr.isLoopbackAddress()) {
            return !allowLocalLoopback;
        }
        if (addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()) {
            return true; // Always block site-local, link-local, and private networks
        }
        byte[] bytes = addr.getAddress();

        // IPv4 evaluation (4 bytes)
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            if (b0 == 127) return !allowLocalLoopback; // 127.0.0.0/8 loopback
            if (b0 == 169 && b1 == 254) return true;   // Cloud metadata (169.254.169.254) / link-local always blocked
            if (b0 == 10) return true;                 // RFC 1918 10.0.0.0/8 always blocked
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true; // RFC 1918 172.16.0.0/12 always blocked
            if (b0 == 192 && b1 == 168) return true;   // RFC 1918 192.168.0.0/16 always blocked
            if (b0 == 0) return true;                  // 0.0.0.0/8 current network
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return true; // Carrier-grade NAT 100.64.0.0/10 always blocked
            if (b0 >= 224) return true;                // Multicast & Reserved (224.0.0.0/4 and 240.0.0.0/4)
        }

        // IPv6 evaluation (16 bytes)
        if (bytes.length == 16) {
            // Check for IPv4-mapped IPv6 addresses (::ffff:x.x.x.x)
            boolean isIpv4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) { isIpv4Mapped = false; break; }
            }
            if (isIpv4Mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                byte[] v4Bytes = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                try {
                    InetAddress v4Addr = InetAddress.getByAddress(v4Bytes);
                    return isBlockedAddress(v4Addr);
                } catch (Exception ignored) {
                    return true;
                }
            }

            int b0 = bytes[0] & 0xFF;
            // Check IPv6 Unique Local Address fc00::/7 (RFC 4193: covers fc00:: and fd00::)
            if ((b0 & 0xFE) == 0xFC) {
                return true;
            }
            // Check IPv6 Link-Local fe80::/10
            if (b0 == 0xFE && (bytes[1] & 0xC0) == 0x80) {
                return true;
            }
            // Check IPv6 Multicast ff00::/8
            if (b0 == 0xFF) {
                return true;
            }

            // Check IPv6 Loopback (::1) and Unspecified (::)
            boolean allZeroExceptLast = true;
            for (int i = 0; i < 15; i++) {
                if (bytes[i] != 0) { allZeroExceptLast = false; break; }
            }
            if (allZeroExceptLast) {
                if (bytes[15] == 1) return !allowLocalLoopback; // ::1
                if (bytes[15] == 0) return true;                // ::
            }
        }

        return false;
    }

    public InetAddress resolveAndValidateSafeDestinationIp(String host) throws SecurityException {
        if (host == null || host.isBlank()) {
            throw new SecurityException("Host is required.");
        }
        String cleanHost = host.trim().toLowerCase();
        if (BLOCKED_HOSTNAMES.contains(cleanHost) || cleanHost.endsWith(".localhost") || cleanHost.endsWith(".local")) {
            if ("localhost".equals(cleanHost) || "127.0.0.1".equals(cleanHost) || "::1".equals(cleanHost)) {
                if (!allowLocalLoopback) {
                    throw new SecurityException("Target URL host resolves to a private or restricted internal network: " + cleanHost);
                }
            } else {
                throw new SecurityException("Target URL host resolves to a private or restricted internal network: " + cleanHost);
            }
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(cleanHost);
            if (addresses == null || addresses.length == 0) {
                throw new SecurityException("DNS resolution returned no addresses for host: " + cleanHost);
            }

            for (InetAddress addr : addresses) {
                if (isBlockedAddress(addr)) {
                    throw new SecurityException("Target URL host resolves to a private or restricted internal network: " + addr.getHostAddress());
                }
            }

            return addresses[0];
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            log.warn("[DocumentFetcher] DNS resolution failed for host '{}': {}", host, e.getMessage());
            throw new SecurityException("DNS resolution failed for host: " + host, e);
        }
    }

    public HttpURLConnection openPinnedConnection(URI uri, InetAddress pinnedIp) throws Exception {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 80);

        String pathAndQuery = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            pathAndQuery += "?" + uri.getRawQuery();
        }

        // Direct socket connection target using validated destination IP
        URL pinnedUrl = new URL(scheme, pinnedIp.getHostAddress(), port, pathAndQuery);
        HttpURLConnection conn = (HttpURLConnection) pinnedUrl.openConnection(java.net.Proxy.NO_PROXY);

        // Pin Host HTTP header to preserve server-side virtual hosting and routing
        conn.setRequestProperty("Host", uri.getPort() != -1 ? host + ":" + uri.getPort() : host);
        conn.setRequestProperty("X-Vidhura-Target-Host", host);

        if (conn instanceof javax.net.ssl.HttpsURLConnection httpsConn) {
            // Configure TLS:
            // 1. Hostname verification against original host domain matching Subject Alternative Names (SAN) and CN
            httpsConn.setHostnameVerifier((hostname, session) -> {
                return verifyPeerCertHost(host, session);
            });

            // 2. SNI Server Name Indication with original host domain
            try {
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getDefault();
                javax.net.ssl.SSLParameters sslParams = sslContext.getDefaultSSLParameters();
                sslParams.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(host)));
                httpsConn.setSSLSocketFactory(new PinnedSSLSocketFactory(sslContext.getSocketFactory(), sslParams, host));
            } catch (Exception e) {
                log.warn("[DocumentFetcher] Could not configure SNI for {}: {}", host, e.getMessage());
                throw new SecurityException("TLS SNI initialization failed for host: " + host, e);
            }
        }

        return conn;
    }

    /**
     * Verifies the peer certificate identity against the expected host according to RFC 6125:
     * - Checks Subject Alternative Names of type dNSName (tag 2).
     * - If dNSName SANs are present, Common Name (CN) fallback is strictly FORBIDDEN.
     * - CN fallback is only permitted if no dNSName SANs exist on the certificate.
     * - Wildcard certificates (*.domain.com) match exactly one level of subdomain.
     * - Returns false on any certificate parsing or mismatch error (fail-closed).
     */
    boolean verifyPeerCertHost(String expectedHost, javax.net.ssl.SSLSession session) {
        if (expectedHost == null || session == null) return false;
        String host = expectedHost.toLowerCase().trim();
        try {
            java.security.cert.Certificate[] certs = session.getPeerCertificates();
            if (certs == null || certs.length == 0) return false;
            if (certs[0] instanceof java.security.cert.X509Certificate x509) {
                // 1. Check Subject Alternative Names (type 2 = dNSName)
                java.util.Collection<java.util.List<?>> sans = x509.getSubjectAlternativeNames();
                boolean hasDnsSans = false;
                if (sans != null) {
                    for (java.util.List<?> entry : sans) {
                        if (entry != null && entry.size() >= 2 && Integer.valueOf(2).equals(entry.get(0))) {
                            hasDnsSans = true;
                            String san = String.valueOf(entry.get(1)).toLowerCase().trim();
                            if (hostMatches(host, san)) return true;
                        }
                    }
                }

                // 2. Fallback to Common Name (CN) ONLY IF NO dNSName SANs were present (RFC 6125 §6.4.4)
                if (!hasDnsSans) {
                    String dn = x509.getSubjectX500Principal().getName();
                    for (String part : dn.split(",")) {
                        String p = part.trim();
                        if (p.toUpperCase().startsWith("CN=")) {
                            String cn = p.substring(3).trim().toLowerCase();
                            if (hostMatches(host, cn)) return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[DocumentFetcher] Error verifying peer certificate for host '{}': {}", host, e.getMessage());
            return false;
        }
        return false;
    }

    boolean hostMatches(String host, String pattern) {
        if (pattern == null || host == null) return false;
        String h = host.toLowerCase().trim();
        String p = pattern.toLowerCase().trim();
        if (p.equals(h)) return true;
        if (p.startsWith("*.")) {
            String suffix = p.substring(2);
            // Wildcard matches exactly one subdomain segment: e.g. foo.domain.com, not bar.foo.domain.com or domain.com
            if (h.endsWith("." + suffix)) {
                String prefix = h.substring(0, h.length() - suffix.length() - 1);
                return !prefix.isEmpty() && !prefix.contains(".");
            }
        }
        return false;
    }

    private static class PinnedSSLSocketFactory extends javax.net.ssl.SSLSocketFactory {
        private final javax.net.ssl.SSLSocketFactory delegate;
        private final javax.net.ssl.SSLParameters parameters;
        private final String originalHost;

        public PinnedSSLSocketFactory(javax.net.ssl.SSLSocketFactory delegate, javax.net.ssl.SSLParameters parameters, String originalHost) {
            this.delegate = delegate;
            this.parameters = parameters;
            this.originalHost = originalHost;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(s, originalHost, port, autoClose);
            if (socket instanceof javax.net.ssl.SSLSocket sslSocket) {
                sslSocket.setSSLParameters(parameters);
            }
            return socket;
        }

        @Override
        public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(originalHost, port);
            if (socket instanceof javax.net.ssl.SSLSocket sslSocket) {
                sslSocket.setSSLParameters(parameters);
            }
            return socket;
        }

        @Override
        public java.net.Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(originalHost, port, localHost, localPort);
            if (socket instanceof javax.net.ssl.SSLSocket sslSocket) {
                sslSocket.setSSLParameters(parameters);
            }
            return socket;
        }

        @Override
        public java.net.Socket createSocket(InetAddress host, int port) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(host, port);
            if (socket instanceof javax.net.ssl.SSLSocket sslSocket) {
                sslSocket.setSSLParameters(parameters);
            }
            return socket;
        }

        @Override
        public java.net.Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(address, port, localAddress, localPort);
            if (socket instanceof javax.net.ssl.SSLSocket sslSocket) {
                sslSocket.setSSLParameters(parameters);
            }
            return socket;
        }
    }

    /**
     * Downloads document from url with conditional cache headers and SSRF defense.
     */
    public FetchResult fetchDocument(String urlStr, String previousEtag, String previousLastMod) {
        return fetchDocumentWithRedirectHops(urlStr, previousEtag, previousLastMod, 0);
    }

    private FetchResult fetchDocumentWithRedirectHops(
            String urlStr, String previousEtag, String previousLastMod, int hopCount) {

        Instant now = Instant.now();
        if (urlStr == null || urlStr.isBlank()) {
            return FetchResult.builder()
                    .status(FetchStatus.FAILED)
                    .httpStatusCode(400)
                    .errorMessage("URL is required.")
                    .fetchedAt(now)
                    .build();
        }

        if (hopCount > MAX_REDIRECT_HOPS) {
            log.warn("[DocumentFetcher] Maximum redirect limit ({}) exceeded for URL: {}", MAX_REDIRECT_HOPS, urlStr);
            return FetchResult.builder()
                    .status(FetchStatus.FAILED)
                    .httpStatusCode(310)
                    .errorMessage("Too many redirects (exceeded max " + MAX_REDIRECT_HOPS + " hops).")
                    .fetchedAt(now)
                    .build();
        }

        URI uri;
        InetAddress destinationIp;
        try {
            uri = URI.create(urlStr.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                log.warn("[DocumentFetcher] Rejected invalid protocol '{}' for URL: {}", scheme, urlStr);
                return FetchResult.builder()
                        .status(FetchStatus.FAILED)
                        .httpStatusCode(400)
                        .errorMessage("Invalid URL scheme: only HTTP and HTTPS are permitted.")
                        .fetchedAt(now)
                        .build();
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new SecurityException("Host is required.");
            }
            String cleanHost = host.trim().toLowerCase();

            // 1. Direct SSRF IP & Localhost check: If host is an IP address, localhost, or private alias,
            // evaluate SSRF restrictions immediately so private/loopback/metadata destinations fail with clear SSRF error.
            if (BLOCKED_HOSTNAMES.contains(cleanHost) || isIpAddressOrLocal(cleanHost)) {
                destinationIp = resolveAndValidateSafeDestinationIp(host);
            }

            // 2. Insurer domain allowlist check: For external domains, verify allowlist BEFORE DNS resolution
            // to prevent DNS query leakage or attacker-controlled DNS rebinding probes.
            if (!allowLocalLoopback && !isApprovedOfficialDomain(host)) {
                log.warn("[DocumentFetcher] Access denied: Host '{}' is not on the approved insurer allowlist for URL: {}", host, urlStr);
                return FetchResult.builder()
                        .status(FetchStatus.FAILED)
                        .httpStatusCode(403)
                        .errorMessage("Access denied: Host '" + host + "' is not on the approved official insurer allowlist.")
                        .fetchedAt(now)
                        .build();
            }

            destinationIp = resolveAndValidateSafeDestinationIp(host);

        } catch (SecurityException se) {
            log.warn("[DocumentFetcher] SSRF Protection: Blocked prohibited destination host for URL '{}': {}",
                    urlStr, se.getMessage());
            return FetchResult.builder()
                    .status(FetchStatus.FAILED)
                    .httpStatusCode(403)
                    .errorMessage("Access denied: " + se.getMessage())
                    .fetchedAt(now)
                    .build();
        } catch (Exception e) {
            return FetchResult.builder()
                    .status(FetchStatus.FAILED)
                    .httpStatusCode(400)
                    .errorMessage("Malformed URL: " + e.getMessage())
                    .fetchedAt(now)
                    .build();
        }

        HttpURLConnection conn = null;
        try {
            conn = openPinnedConnection(uri, destinationIp);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/pdf,application/octet-stream,*/*");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false); // Security: Follow manually with SSRF checks on each target

            // Conditional headers
            if (previousEtag != null && !previousEtag.isBlank()) {
                conn.setRequestProperty("If-None-Match", previousEtag);
            }
            if (previousLastMod != null && !previousLastMod.isBlank()) {
                conn.setRequestProperty("If-Modified-Since", previousLastMod);
            }

            conn.connect();
            int statusCode = conn.getResponseCode();

            // 1. Check HTTP 304 Not Modified
            if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                log.info("[DocumentFetcher] HTTP 304 Not Modified for URL: {}", urlStr);
                return FetchResult.builder()
                        .status(FetchStatus.NOT_MODIFIED)
                        .httpStatusCode(statusCode)
                        .etag(previousEtag)
                        .lastModifiedHeader(previousLastMod)
                        .fetchedAt(now)
                        .build();
            }

            // 2. Check Redirects (301, 302, 303, 307, 308) with strict destination domain and IP validation
            if (statusCode == HttpURLConnection.HTTP_MOVED_PERM
                    || statusCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || statusCode == HttpURLConnection.HTTP_SEE_OTHER
                    || statusCode == 307
                    || statusCode == 308) {

                String redirectLocation = conn.getHeaderField("Location");
                if (redirectLocation == null || redirectLocation.isBlank()) {
                    return FetchResult.builder()
                            .status(FetchStatus.FAILED)
                            .httpStatusCode(statusCode)
                            .errorMessage("Redirect status code returned without Location header.")
                            .fetchedAt(now)
                            .build();
                }

                URI redirectedUri = uri.resolve(redirectLocation);
                String redirHost = redirectedUri.getHost();
                if (!allowLocalLoopback && !isApprovedOfficialDomain(redirHost)) {
                    log.warn("[DocumentFetcher] Security violation: Blocked redirect to unapproved domain '{}' from URL: {}", redirHost, urlStr);
                    return FetchResult.builder()
                            .status(FetchStatus.FAILED)
                            .httpStatusCode(403)
                            .errorMessage("Security violation: Redirect target '" + redirHost + "' is not on the approved insurer allowlist.")
                            .fetchedAt(now)
                            .build();
                }

                log.info("[DocumentFetcher] Following safe redirect ({}) to: {}", statusCode, redirectedUri);
                boolean sameHost = redirHost != null && redirHost.equalsIgnoreCase(uri.getHost());
                String nextEtag = sameHost ? previousEtag : null;
                String nextLastMod = sameHost ? previousLastMod : null;
                return fetchDocumentWithRedirectHops(redirectedUri.toString(), nextEtag, nextLastMod, hopCount + 1);
            }

            // 3. Check HTTP 200 OK
            if (statusCode == HttpURLConnection.HTTP_OK) {
                String etag = conn.getHeaderField("ETag");
                String lastModified = conn.getHeaderField("Last-Modified");
                String contentType = conn.getContentType();

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");

                try (InputStream in = conn.getInputStream()) {
                    byte[] data = new byte[8192];
                    int nRead;
                    long totalBytes = 0;
                    while ((nRead = in.read(data, 0, data.length)) != -1) {
                        totalBytes += nRead;
                        if (totalBytes > MAX_FILE_SIZE_BYTES) {
                            return FetchResult.builder()
                                    .status(FetchStatus.FAILED)
                                    .httpStatusCode(413)
                                    .errorMessage("Document exceeded maximum allowed size of 50 MB.")
                                    .fetchedAt(now)
                                    .build();
                        }
                        buffer.write(data, 0, nRead);
                        sha256Digest.update(data, 0, nRead);
                    }
                }

                byte[] bytes = buffer.toByteArray();
                String checksum = HexFormat.of().formatHex(sha256Digest.digest());

                log.info("[DocumentFetcher] Successfully downloaded {} bytes (SHA-256={}) from {}",
                        bytes.length, checksum, urlStr);

                return FetchResult.builder()
                        .status(FetchStatus.FETCHED)
                        .httpStatusCode(statusCode)
                        .documentBytes(bytes)
                        .sha256Checksum(checksum)
                        .fileSizeBytes(bytes.length)
                        .etag(etag)
                        .lastModifiedHeader(lastModified)
                        .contentType(contentType)
                        .fetchedAt(now)
                        .build();
            }

            // 4. Other HTTP status codes
            log.warn("[DocumentFetcher] HTTP {} received for URL: {}", statusCode, urlStr);
            return FetchResult.builder()
                    .status(FetchStatus.FAILED)
                    .httpStatusCode(statusCode)
                    .errorMessage("HTTP request failed with status code: " + statusCode)
                    .fetchedAt(now)
                    .build();

        } catch (Exception e) {
            log.error("[DocumentFetcher] Failed to fetch document from {}: {}", urlStr, e.getMessage());
            return FetchResult.builder()
                    .status(FetchStatus.FAILED)
                    .httpStatusCode(500)
                    .errorMessage("Connection or I/O failure: " + e.getMessage())
                    .fetchedAt(now)
                    .build();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Verifies if byte payload contains valid PDF signature magic bytes (%PDF-).
     */
    public boolean isPdfContent(byte[] bytes) {
        if (bytes == null || bytes.length < 5) return false;
        String header = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
        return header.startsWith("%PDF-");
    }

    /**
     * Computes SHA-256 hash string for an arbitrary byte array.
     */
    public static String computeSha256(byte[] bytes) {
        if (bytes == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
