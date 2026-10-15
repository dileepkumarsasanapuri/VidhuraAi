package com.vidhura.ai.service;

import com.vidhura.ai.model.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnalysisCacheService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCacheService.class);

    private final Map<String, Policy.PolicyAnalysis> cache = new ConcurrentHashMap<>();

    public String computeSha256(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            return String.valueOf(data.length);
        }
    }

    public Optional<Policy.PolicyAnalysis> getCached(String hash) {
        if (hash == null || hash.isEmpty()) {
            return Optional.empty();
        }
        Policy.PolicyAnalysis analysis = cache.get(hash);
        if (analysis != null) {
            log.info("[Analysis Cache] Cache HIT for SHA-256: {}", hash);
            return Optional.of(analysis);
        }
        return Optional.empty();
    }

    public void put(String hash, Policy.PolicyAnalysis analysis) {
        if (hash != null && !hash.isEmpty() && analysis != null) {
            cache.put(hash, analysis);
            log.info("[Analysis Cache] Stored analysis in cache for SHA-256: {}", hash);
        }
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
