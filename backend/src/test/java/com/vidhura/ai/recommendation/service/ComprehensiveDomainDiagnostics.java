package com.vidhura.ai.recommendation.service;

import java.net.*;
import javax.net.ssl.*;

public class ComprehensiveDomainDiagnostics {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    public static void main(String[] args) {
        String[] testUrls = {
            "https://www.hdfcergo.com/",
            "https://www.hdfcergo.com/downloads/policy-wordings",
            "https://www.starhealth.in/",
            "https://www.careinsurance.com/",
            "https://www.nivabupa.com/",
            "https://www.icicilombard.com/",
            "https://www.bajajallianz.com/",
            "https://uiic.co.in/",
            "https://nationalinsurance.nic.co.in/",
            "https://irdai.gov.in/"
        };

        for (String urlStr : testUrls) {
            diagnoseUrl(urlStr);
        }
    }

    private static void diagnoseUrl(String urlStr) {
        System.out.println("==================================================================");
        System.out.println("DIAGNOSING: " + urlStr);
        try {
            URI uri = URI.create(urlStr);
            String host = uri.getHost();
            InetAddress ip = InetAddress.getByName(host);
            System.out.println("DNS Resolved IP: " + ip.getHostAddress());

            int port = uri.getPort() != -1 ? uri.getPort() : 443;
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            URL pinnedUrl = new URL(uri.getScheme(), ip.getHostAddress(), port, path);
            HttpsURLConnection conn = (HttpsURLConnection) pinnedUrl.openConnection(Proxy.NO_PROXY);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Host", host);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            SSLContext sslContext = SSLContext.getDefault();
            SSLParameters sslParams = sslContext.getDefaultSSLParameters();
            sslParams.setServerNames(java.util.List.of(new SNIHostName(host)));

            conn.setHostnameVerifier((h, s) -> true);
            conn.setSSLSocketFactory(new PinnedSSLSocketFactory(sslContext.getSocketFactory(), sslParams, host));

            conn.connect();
            int status = conn.getResponseCode();
            String msg = conn.getResponseMessage();
            String contentType = conn.getContentType();
            String location = conn.getHeaderField("Location");
            String server = conn.getHeaderField("Server");

            System.out.println("STATUS: " + status + " (" + msg + ")");
            System.out.println("Content-Type: " + contentType);
            if (location != null) System.out.println("Location: " + location);
            if (server != null) System.out.println("Server: " + server);

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private static class PinnedSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final SSLParameters parameters;
        private final String originalHost;

        public PinnedSSLSocketFactory(SSLSocketFactory delegate, SSLParameters parameters, String originalHost) {
            this.delegate = delegate;
            this.parameters = parameters;
            this.originalHost = originalHost;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            Socket socket = delegate.createSocket(s, originalHost, port, autoClose);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
        @Override
        public Socket createSocket(String host, int port) throws java.io.IOException {
            Socket socket = delegate.createSocket(originalHost, port);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws java.io.IOException {
            Socket socket = delegate.createSocket(originalHost, port, localHost, localPort);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
        @Override
        public Socket createSocket(InetAddress host, int port) throws java.io.IOException {
            Socket socket = delegate.createSocket(host, port);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws java.io.IOException {
            Socket socket = delegate.createSocket(address, port, localAddress, localPort);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
    }
}
