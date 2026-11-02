package com.vidhura.ai.recommendation.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Persistent Artifact Storage Service.
 *
 * Implements immutable, hash-addressed, atomic-write storage for acquired policy
 * documents and statutory evidence disclosures.
 *
 * Key Properties:
 * 1. Hash-Addressed: Stored files are addressed by their exact SHA-256 digest (<sha256>.<ext>).
 * 2. Atomic Writes: Payloads are written to an isolated temporary file and renamed via Files.move with ATOMIC_MOVE.
 * 3. Duplicate Suppression: If an identical artifact exists with matching byte length and hash, rewriting is skipped.
 * 4. Safe Path Canonicalization: Guards against directory traversal (../) attacks.
 * 5. Multi-Location Resolution: Resolves historical hashes across persistent storage and bundled classpath resources.
 */
@Slf4j
@Service
public class PersistentArtifactStorageService {

    public static final String DEFAULT_STORAGE_PATH = "data/evidence/persistent_storage/";
    public static final List<String> PACKAGED_RESOURCE_DIRS = List.of(
            "src/main/resources/evidence/policies/",
            "src/main/resources/evidence/statutory/",
            "src/main/resources/evidence/cache/"
    );

    @Value("${vidhura.evidence.storage-path:data/evidence/persistent_storage/}")
    private String storageBasePath = DEFAULT_STORAGE_PATH;

    @Data
    @Builder
    public static class StoredArtifact {
        private String sha256;
        private String storagePath;
        private long byteSize;
        private String mediaType;
        private Instant storedAt;
        private boolean alreadyExisted;
    }

    public PersistentArtifactStorageService() {
    }

    public PersistentArtifactStorageService(String storageBasePath) {
        this.storageBasePath = storageBasePath;
    }

    public void setStorageBasePath(String storageBasePath) {
        this.storageBasePath = storageBasePath;
    }

    public String getStorageBasePath() {
        return storageBasePath;
    }

    /**
     * Stores an artifact payload to persistent disk storage using hash-addressed atomic writes.
     *
     * @param data Raw payload bytes.
     * @param mediaType MIME type (e.g., "application/pdf", "text/html").
     * @param claimedSha256 Optional claimed hash; if provided, must match computed bytes.
     * @return StoredArtifact metadata.
     */
    public StoredArtifact storeArtifact(byte[] data, String mediaType, String claimedSha256) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Cannot store empty or null artifact payload.");
        }

        String computedHash = computeSha256(data);
        if (claimedSha256 != null && !claimedSha256.isBlank()) {
            String cleanClaimed = claimedSha256.replaceAll("(?i)^sha256:", "").trim().toLowerCase();
            if (!computedHash.equalsIgnoreCase(cleanClaimed)) {
                throw new IllegalArgumentException(String.format(
                        "Payload byte digest (%s) does not match claimed hash (%s)", computedHash, cleanClaimed));
            }
        }

        Path baseDir = Paths.get(storageBasePath).toAbsolutePath().normalize();
        if (!Files.exists(baseDir)) {
            try {
                Files.createDirectories(baseDir);
            } catch (IOException e) {
                throw new IOException("Failed to initialize persistent artifact storage directory: " + baseDir, e);
            }
        }

        String extension = determineExtension(mediaType);
        String targetFilename = computedHash + extension;
        Path targetPath = resolveStoragePath(targetFilename);

        // Duplicate suppression: if already stored and identical, return existing reference
        if (Files.exists(targetPath) && Files.size(targetPath) == data.length) {
            String existingHash = computeFileSha256(targetPath.toFile());
            if (existingHash.equalsIgnoreCase(computedHash)) {
                log.debug("[ArtifactStorage] Duplicate suppressed: artifact {} already present at {}", computedHash, targetPath);
                return StoredArtifact.builder()
                        .sha256(computedHash)
                        .storagePath(targetPath.toString())
                        .byteSize(data.length)
                        .mediaType(mediaType)
                        .storedAt(Files.getLastModifiedTime(targetPath).toInstant())
                        .alreadyExisted(true)
                        .build();
            }
        }

        // Atomic write via temporary file
        String tempFilename = "." + computedHash + "_" + UUID.randomUUID() + ".tmp";
        Path tempPath = baseDir.resolve(tempFilename).normalize();

        try {
            Files.write(tempPath, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            // Atomic move to target filename
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("[ArtifactStorage] Persisted immutable artifact {} ({} bytes) -> {}",
                    computedHash, data.length, targetPath);

            return StoredArtifact.builder()
                    .sha256(computedHash)
                    .storagePath(targetPath.toString())
                    .byteSize(data.length)
                    .mediaType(mediaType)
                    .storedAt(Instant.now())
                    .alreadyExisted(false)
                    .build();

        } catch (IOException ex) {
            // Cleanup temp file on failure
            try {
                Files.deleteIfExists(tempPath);
            } catch (Exception ignored) { }
            throw new IOException("Failed to persist artifact to " + targetPath + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Resolves and validates a storage path within the configured base directory,
     * strictly rejecting directory traversal (..) and escape attempts.
     */
    public Path resolveStoragePath(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be null or blank");
        }
        if (filename.contains("..") || filename.startsWith("/") || filename.startsWith("\\")) {
            throw new SecurityException("Path traversal attempt detected: " + filename);
        }
        Path baseDir = Paths.get(storageBasePath).toAbsolutePath().normalize();
        Path resolved = baseDir.resolve(filename).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new SecurityException("Path traversal attempt detected: " + filename);
        }
        return resolved;
    }

    /**
     * Resolves an artifact by its SHA-256 hash across persistent storage and packaged directories.
     */
    public Optional<File> resolveArtifact(String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            return Optional.empty();
        }
        String cleanHash = sha256.replaceAll("(?i)^sha256:", "").trim().toLowerCase();

        // 1. Check persistent storage directory
        Path baseDir = Paths.get(storageBasePath).toAbsolutePath().normalize();
        if (Files.exists(baseDir)) {
            for (String ext : List.of(".pdf", ".html", ".bin", ".txt")) {
                Path candidate = baseDir.resolve(cleanHash + ext).normalize();
                if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                    return Optional.of(candidate.toFile());
                }
            }
        }

        // 2. Check packaged resource directories
        for (String dirPath : PACKAGED_RESOURCE_DIRS) {
            File dir = new File(dirPath);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            // Check filename prefix or direct hash
                            if (f.getName().toLowerCase().contains(cleanHash)) {
                                return Optional.of(f);
                            }
                            String fileHash = computeFileSha256(f);
                            if (cleanHash.equalsIgnoreCase(fileHash)) {
                                return Optional.of(f);
                            }
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    public boolean hasArtifact(String sha256OrPath) {
        if (sha256OrPath == null || sha256OrPath.isBlank()) return false;
        File f = new File(sha256OrPath);
        if (f.exists() && f.isFile()) return true;
        return resolveArtifact(sha256OrPath).isPresent();
    }


    /**
     * Verifies that a file on disk matches its claimed SHA-256 digest.
     */
    public boolean verifyFileHash(File file, String expectedSha256) {
        if (file == null || !file.exists() || !file.isFile() || expectedSha256 == null) {
            return false;
        }
        String cleanExpected = expectedSha256.replaceAll("(?i)^sha256:", "").trim().toLowerCase();
        String actual = computeFileSha256(file);
        return actual.equalsIgnoreCase(cleanExpected);
    }


    private String determineExtension(String mediaType) {
        if (mediaType == null) return ".bin";
        String lower = mediaType.toLowerCase();
        if (lower.contains("pdf")) return ".pdf";
        if (lower.contains("html")) return ".html";
        if (lower.contains("json")) return ".json";
        if (lower.contains("text")) return ".txt";
        return ".bin";
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 computation failure", e);
        }
    }

    public static String computeFileSha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString().toLowerCase();
        } catch (Exception e) {
            log.warn("[ArtifactStorage] Error computing SHA-256 for file {}: {}", file.getName(), e.getMessage());
            return "";
        }
    }
}
