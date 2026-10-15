package com.vidhura.ai.service;

import com.vidhura.ai.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisCacheServiceTest {

    private AnalysisCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new AnalysisCacheService();
    }

    @Test
    void testComputeSha256Consistent() {
        byte[] data1 = "Sample PDF text content for Vidhura AI".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "Sample PDF text content for Vidhura AI".getBytes(StandardCharsets.UTF_8);
        byte[] data3 = "Different PDF content".getBytes(StandardCharsets.UTF_8);

        String hash1 = cacheService.computeSha256(data1);
        String hash2 = cacheService.computeSha256(data2);
        String hash3 = cacheService.computeSha256(data3);

        assertNotNull(hash1);
        assertEquals(64, hash1.length()); // SHA-256 is 64 hex characters
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
    }

    @Test
    void testPutAndGetCachedAnalysis() {
        String hash = "dummy_sha256_hash_12345";
        Policy.PolicyAnalysis analysis = Policy.PolicyAnalysis.builder()
                .policyName("HDFC ERGO Optima Secure")
                .provider("HDFC ERGO")
                .type("Health Insurance")
                .build();

        // Initially empty
        Optional<Policy.PolicyAnalysis> emptyOpt = cacheService.getCached(hash);
        assertFalse(emptyOpt.isPresent());

        // Put and retrieve
        cacheService.put(hash, analysis);
        Optional<Policy.PolicyAnalysis> hitOpt = cacheService.getCached(hash);
        assertTrue(hitOpt.isPresent());
        assertEquals("HDFC ERGO Optima Secure", hitOpt.get().getPolicyName());
        assertEquals(1, cacheService.size());
    }
}
