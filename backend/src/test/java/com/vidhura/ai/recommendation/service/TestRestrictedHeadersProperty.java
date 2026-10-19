package com.vidhura.ai.recommendation.service;

import java.io.InputStream;
import java.net.*;
import javax.net.ssl.*;

public class TestRestrictedHeadersProperty {
    public static void main(String[] args) throws Exception {
        System.out.println("Property sun.net.http.allowRestrictedHeaders = " + System.getProperty("sun.net.http.allowRestrictedHeaders"));

        String testHost = "www.hdfcergo.com";
        InetAddress pinnedIp = InetAddress.getByName(testHost);
        System.out.println("Connecting to IP " + pinnedIp.getHostAddress() + " with Host: " + testHost);

        URL pinnedUrl = new URL("https", pinnedIp.getHostAddress(), 443, "/");
        HttpsURLConnection conn = (HttpsURLConnection) pinnedUrl.openConnection(Proxy.NO_PROXY);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Host", testHost);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        SSLContext sslContext = SSLContext.getDefault();
        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        sslParams.setServerNames(java.util.List.of(new SNIHostName(testHost)));

        conn.setHostnameVerifier((h, s) -> true);
        conn.setSSLSocketFactory(new SSLSocketFactory() {
            private final SSLSocketFactory delegate = sslContext.getSocketFactory();
            @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
            @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
            @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
                Socket sock = delegate.createSocket(s, testHost, port, autoClose);
                if (sock instanceof SSLSocket ssl) ssl.setSSLParameters(sslParams);
                return sock;
            }
            @Override public Socket createSocket(String host, int port) throws java.io.IOException {
                Socket sock = delegate.createSocket(testHost, port);
                if (sock instanceof SSLSocket ssl) ssl.setSSLParameters(sslParams);
                return sock;
            }
            @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws java.io.IOException {
                Socket sock = delegate.createSocket(testHost, port, localHost, localPort);
                if (sock instanceof SSLSocket ssl) ssl.setSSLParameters(sslParams);
                return sock;
            }
            @Override public Socket createSocket(InetAddress host, int port) throws java.io.IOException {
                Socket sock = delegate.createSocket(host, port);
                if (sock instanceof SSLSocket ssl) ssl.setSSLParameters(sslParams);
                return sock;
            }
            @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws java.io.IOException {
                Socket sock = delegate.createSocket(address, port, localAddress, localPort);
                if (sock instanceof SSLSocket ssl) ssl.setSSLParameters(sslParams);
                return sock;
            }
        });

        conn.connect();
        int code = conn.getResponseCode();
        System.out.println("Response Code: " + code + " " + conn.getResponseMessage());
        conn.getHeaderFields().forEach((k, v) -> {
            if (k != null && (k.toLowerCase().contains("server") || k.toLowerCase().contains("cf-") || k.toLowerCase().contains("x-") || k.toLowerCase().contains("location"))) {
                System.out.println("  " + k + ": " + v);
            }
        });
    }
}
