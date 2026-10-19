package com.vidhura.ai.recommendation.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import javax.net.ssl.*;

public class FindRealOfficialPdfLinks {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    public static void main(String[] args) throws Exception {
        // Let's test official portals that returned 200 OK or 302
        testPortalLinks("https://nationalinsurance.nic.co.in/");
        testPortalLinks("https://uiic.co.in/web");
        testPortalLinks("https://irdai.gov.in/");
        testPortalLinks("https://www.nivabupa.com/");
    }

    private static void testPortalLinks(String targetUrl) {
        System.out.println("\n=======================================================");
        System.out.println("SCANNING FOR PDF LINKS ON: " + targetUrl);
        try {
            URI uri = URI.create(targetUrl);
            String host = uri.getHost();
            InetAddress ip = InetAddress.getByName(host);

            URL url = new URL(uri.getScheme(), ip.getHostAddress(), 443, uri.getRawPath() != null ? uri.getRawPath() : "/");
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection(Proxy.NO_PROXY);
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Host", host);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            SSLContext sslContext = SSLContext.getDefault();
            SSLParameters sslParams = sslContext.getDefaultSSLParameters();
            sslParams.setServerNames(java.util.List.of(new SNIHostName(host)));
            conn.setHostnameVerifier((h, s) -> true);
            conn.setSSLSocketFactory(new PinnedSSLSocketFactory(sslContext.getSocketFactory(), sslParams, host));

            conn.connect();
            int code = conn.getResponseCode();
            System.out.println("HTTP Response Code: " + code);

            if (code == 200) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1 && baos.size() < 1024 * 1024) {
                        baos.write(buf, 0, r);
                    }
                }
                String html = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                System.out.println("Downloaded HTML length: " + html.length() + " chars");

                Matcher m = Pattern.compile("(?i)href\\s*=\\s*[\"']([^\"'#\\s>]+\\.pdf[^\"'#\\s>]*)[\"']").matcher(html);
                int count = 0;
                while (m.find() && count < 10) {
                    count++;
                    String link = m.group(1);
                    System.out.println("  PDF link " + count + ": " + link);
                }
                if (count == 0) {
                    System.out.println("  No direct .pdf links found on root page.");
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
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
        @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            Socket socket = delegate.createSocket(s, originalHost, port, autoClose);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
        @Override public Socket createSocket(String host, int port) throws IOException {
            Socket socket = delegate.createSocket(originalHost, port);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
        @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            Socket socket = delegate.createSocket(originalHost, port, localHost, localPort);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket socket = delegate.createSocket(host, port);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
        @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            Socket socket = delegate.createSocket(address, port, localAddress, localPort);
            if (socket instanceof SSLSocket sslSocket) sslSocket.setSSLParameters(parameters);
            return socket;
        }
    }
}
