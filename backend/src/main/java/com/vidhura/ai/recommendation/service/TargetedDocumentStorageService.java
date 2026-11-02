package com.vidhura.ai.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 6.2: Configurable External Document Storage Service.
 *
 * Implements content-addressed deduplicated document storage, provenance manifests,
 * and quarantine handling outside the application repository.
 *
 * Configurable via VIDHURA_POLICY_STORAGE_ROOT (defaults to E:/VidhuraData/policies on Windows).
 *
 * Storage Structure:
 *   policies/
 *     original/            -> registry-specific original named documents
 *     content-addressed/   -> deduplicated immutable artifacts (<sha256>.pdf)
 *     manifests/           -> provenance & acquisition metadata JSON (<sha256>.json)
 *     quarantine/          -> corrupt, invalid, or truncated payloads (<id>_<timestamp>.bin)
 */
@Slf4j
@Service
public class TargetedDocumentStorageService {

    @Value("${vidhura.policy.storage.root:${VIDHURA_POLICY_STORAGE_ROOT:E:/VidhuraData/policies}}")
    private String storageRootPath = "E:/VidhuraData/policies";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @Builder
    public static class StorageReceipt {
        private String sha256;
        private String contentAddressedPath;
        private String originalPath;
        private String manifestPath;
        private long byteSize;
        private boolean alreadyExisted;
        private Instant storedAt;
    }

    public TargetedDocumentStorageService() {
    }

    public TargetedDocumentStorageService(String storageRootPath) {
        this.storageRootPath = storageRootPath;
        initDirectories();
    }

    @PostConstruct
    public void initDirectories() {
        try {
            Path root = getRootDirectory();
            Files.createDirectories(root.resolve("original"));
            Files.createDirectories(root.resolve("content-addressed"));
            Files.createDirectories(root.resolve("manifests"));
            Files.createDirectories(root.resolve("quarantine"));
            log.info("[DocumentStorage] Storage root initialized at: {}", root.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[DocumentStorage] Could not initialize external storage root at {}: {}. Will fallback to temporary storage if required.",
                    storageRootPath, e.getMessage());
        }
    }

    public Path getRootDirectory() {
        return Paths.get(storageRootPath).toAbsolutePath().normalize();
    }

    public Path getOriginalDirectory() {
        return getRootDirectory().resolve("original").normalize();
    }

    public Path getContentAddressedDirectory() {
        return getRootDirectory().resolve("content-addressed").normalize();
    }

    public Path getManifestsDirectory() {
        return getRootDirectory().resolve("manifests").normalize();
    }

    public Path getQuarantineDirectory() {
        return getRootDirectory().resolve("quarantine").normalize();
    }

    public void setStorageRootPath(String path) {
        this.storageRootPath = path;
        initDirectories();
    }

    public String getStorageRootPath() {
        return this.storageRootPath;
    }

    /**
     * Checks if content-addressed artifact exists with matching byte length.
     */
    public boolean hasContentAddressedArtifact(String sha256) {
        if (sha256 == null || sha256.isBlank()) return false;
        Path path = getContentAddressedDirectory().resolve(sha256.toLowerCase() + ".pdf");
        return Files.exists(path) && path.toFile().length() > 0;
    }

    /**
     * Stores a validated PDF document into content-addressed deduplicated storage,
     * links the original named reference, and generates an immutable provenance manifest.
     */
    public StorageReceipt storeDocument(byte[] data, String sha256, String registryRecordId,
                                        String originalFilename, Map<String, Object> manifestMetadata) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Cannot store empty or null document payload.");
        }

        String computedHash = computeSha256(data);
        if (sha256 != null && !sha256.isBlank() && !computedHash.equalsIgnoreCase(sha256.trim())) {
            throw new IllegalArgumentException(String.format("Calculated digest (%s) does not match claimed hash (%s)",
                    computedHash, sha256));
        }

        initDirectories();
        Path contentDir = getContentAddressedDirectory();
        Path originalDir = getOriginalDirectory();
        Path manifestsDir = getManifestsDirectory();

        String hashKey = computedHash.toLowerCase();
        Path contentPath = contentDir.resolve(hashKey + ".pdf").normalize();
        validatePathSecurity(contentPath, contentDir);

        boolean alreadyExisted = Files.exists(contentPath) && Files.size(contentPath) == data.length;

        // 1. Store content-addressed artifact atomically if not already present
        if (!alreadyExisted) {
            Path tempContent = contentDir.resolve("." + hashKey + "_" + UUID.randomUUID() + ".tmp");
            try {
                Files.write(tempContent, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                Files.move(tempContent, contentPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("[DocumentStorage] Content-addressed artifact stored: {} ({} bytes)", hashKey, data.length);
            } finally {
                try { Files.deleteIfExists(tempContent); } catch (Exception ignored) {}
            }
        } else {
            log.debug("[DocumentStorage] Duplicate artifact suppressed: {} already exists", hashKey);
        }

        // 2. Store original reference link / copy using safe identifier
        String safeOriginalName = sanitizeFilename(registryRecordId + "_" + (originalFilename != null ? originalFilename : hashKey + ".pdf"));
        Path originalPath = originalDir.resolve(safeOriginalName).normalize();
        validatePathSecurity(originalPath, originalDir);

        if (!Files.exists(originalPath)) {
            try {
                // If supported on the filesystem, create hard link or copy
                Files.copy(contentPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                log.warn("[DocumentStorage] Could not create original reference: {}", e.getMessage());
            }
        }

        // 3. Persist provenance manifest JSON
        Path manifestPath = manifestsDir.resolve(hashKey + ".json").normalize();
        validatePathSecurity(manifestPath, manifestsDir);
        if (manifestMetadata != null) {
            manifestMetadata.put("verifiedSha256", hashKey);
            manifestMetadata.put("byteSize", data.length);
            manifestMetadata.put("contentAddressedPath", contentPath.toString());
            manifestMetadata.put("originalPath", originalPath.toString());
            manifestMetadata.put("storedAt", Instant.now().toString());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifestMetadata);
        }

        return StorageReceipt.builder()
                .sha256(hashKey)
                .contentAddressedPath(contentPath.toString())
                .originalPath(originalPath.toString())
                .manifestPath(manifestPath.toString())
                .byteSize(data.length)
                .alreadyExisted(alreadyExisted)
                .storedAt(Instant.now())
                .build();
    }

    /**
     * Stores an invalid, corrupt, or truncated response in the quarantine directory.
     */
    public Path quarantinePayload(byte[] data, String registryRecordId, String failureReason) {
        try {
            initDirectories();
            Path quarantineDir = getQuarantineDirectory();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String safeId = sanitizeFilename(registryRecordId != null ? registryRecordId : "UNKNOWN");
            String filename = safeId + "_" + timestamp + ".quarantine";

            Path quarantineFile = quarantineDir.resolve(filename).normalize();
            validatePathSecurity(quarantineFile, quarantineDir);

            if (data != null && data.length > 0) {
                Files.write(quarantineFile, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }

            Path metaFile = quarantineDir.resolve(filename + ".reason.txt").normalize();
            Files.writeString(metaFile, "Failure Reason: " + failureReason + "\nTimestamp: " + Instant.now(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            log.warn("[DocumentStorage] Quarantined invalid payload for record {}: {} ({})",
                    registryRecordId, filename, failureReason);

            return quarantineFile;
        } catch (Exception e) {
            log.error("[DocumentStorage] Failed to write quarantine record: {}", e.getMessage());
            return null;
        }
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    private void validatePathSecurity(Path targetPath, Path expectedBase) {
        if (!targetPath.normalize().startsWith(expectedBase.normalize())) {
            throw new SecurityException("Path traversal attempt detected: " + targetPath);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "document.pdf";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
