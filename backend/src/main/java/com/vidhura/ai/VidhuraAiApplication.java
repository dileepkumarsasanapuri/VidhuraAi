package com.vidhura.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VidhuraAiApplication {

    static {
        // Enable setting Host header on pinned socket connections in HttpURLConnection
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    public static void main(String[] args) {
        SpringApplication.run(VidhuraAiApplication.class, args);
    }
}
