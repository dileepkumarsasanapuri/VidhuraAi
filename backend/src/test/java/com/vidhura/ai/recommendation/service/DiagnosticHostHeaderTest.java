package com.vidhura.ai.recommendation.service;

import java.io.IOException;
import java.net.*;
import javax.net.ssl.*;

public class DiagnosticHostHeaderTest {
    public static void main(String[] args) throws Exception {
        String testUrl = "https://www.hdfcergo.com/";
        URI uri = URI.create(testUrl);
        InetAddress pinnedIp = InetAddress.getByName(uri.getHost());
        System.out.println("Testing " + uri.getHost() + " pinned to IP: " + pinnedIp.getHostAddress());

        // Test 3: URL has original hostname, but SocketFactory connects to pinnedIp!
        testPinnedSocket(uri, pinnedIp);
    }

    private static void testPinnedSocket(URI uri, InetAddress pinnedIp) {
        System.out.println("\n--- URL with Original Host, Socket to Pinned IP ---");
        try {
            // URL has original hostname -> Host header will be original hostname!
            URL url = uri.toURL();
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection(Proxy.NO_PROXY);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            SSLContext sslContext = SSLContext.getDefault();
            SSLParameters sslParams = sslContext.getDefaultSSLParameters();
            sslParams.setServerNames(java.util.List.of(new SNIHostName(uri.getHost())));

            conn.setHostnameVerifier((h, s) -> {
                System.out.println("HostnameVerifier invoked for: " + h);
                return true;
            });

            conn.setSSLSocketFactory(new PinnedToIpSSLSocketFactory(sslContext.getSocketFactory(), sslParams, pinnedIp, uri.getHost()));

            conn.connect();
            System.out.println("Response Code: " + conn.getResponseCode());
            System.out.println("Response Message: " + conn.getResponseMessage());
            System.out.println("Content-Type: " + conn.getContentType());
            System.out.println("Response Headers:");
            conn.getHeaderFields().forEach((k, v) -> System.out.println("  " + k + ": " + v));
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    private static class PinnedToIpSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final SSLParameters params;
        private final InetAddress pinnedIp;
        private final String originalHost;

        public PinnedToIpSSLSocketFactory(SSLSocketFactory delegate, SSLParameters params, InetAddress pinnedIp, String originalHost) {
            this.delegate = delegate;
            this.params = params;
            this.pinnedIp = pinnedIp;
            this.originalHost = originalHost;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            System.out.println("createSocket(Socket, host=" + host + ", port=" + port + ")");
            Socket socket = delegate.createSocket(s, originalHost, port, autoClose);
            if (socket instanceof SSLSocket ssl) ssl.setSSLParameters(params);
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            System.out.println("createSocket(host=" + host + ", port=" + port + ") -> connecting to pinned IP " + pinnedIp);
            Socket plainSocket = new Socket();
            plainSocket.connect(new InetSocketAddress(pinnedIp, port), 10000);
            Socket sslSocket = delegate.createSocket(plainSocket, originalHost, port, true);
            if (sslSocket instanceof SSLSocket ssl) ssl.setSSLParameters(params);
            return sslSocket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            System.out.println("createSocket(host=" + host + ", port=" + port + ", localHost, localPort) -> connecting to pinned IP");
            Socket plainSocket = new Socket();
            plainSocket.bind(new InetSocketAddress(localHost, localPort));
            plainSocket.connect(new InetSocketAddress(pinnedIp, port), 10000);
            Socket sslSocket = delegate.createSocket(plainSocket, originalHost, port, true);
            if (sslSocket instanceof SSLSocket ssl) ssl.setSSLParameters(params);
            return sslSocket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            System.out.println("createSocket(InetAddress=" + host + ", port=" + port + ") -> connecting to pinned IP");
            Socket plainSocket = new Socket();
            plainSocket.connect(new InetSocketAddress(pinnedIp, port), 10000);
            Socket sslSocket = delegate.createSocket(plainSocket, originalHost, port, true);
            if (sslSocket instanceof SSLSocket ssl) ssl.setSSLParameters(params);
            return sslSocket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            System.out.println("createSocket(InetAddress, port, localAddress, localPort) -> connecting to pinned IP");
            Socket plainSocket = new Socket();
            plainSocket.bind(new InetSocketAddress(localAddress, localPort));
            plainSocket.connect(new InetSocketAddress(pinnedIp, port), 10000);
            Socket sslSocket = delegate.createSocket(plainSocket, originalHost, port, true);
            if (sslSocket instanceof SSLSocket ssl) ssl.setSSLParameters(params);
            return sslSocket;
        }
    }
}
