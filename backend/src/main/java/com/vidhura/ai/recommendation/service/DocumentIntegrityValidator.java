package com.vidhura.ai.recommendation.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 6.2: Document Integrity & Structural Validation Service.
 *
 * Validates acquired PDF payloads for:
 * 1. PDF Magic Bytes (%PDF-)
 * 2. Soft-404 / HTML error response detection
 * 3. Structural readability and decryption status via Apache PDFBox
 * 4. Actual file size limits and cryptographic SHA-256 calculation
 * 5. Extractable text layer detection (scanned vs vector)
 * 6. Document-level UIN extraction from page one and header streams
 */
@Slf4j
@Service
public class DocumentIntegrityValidator {

    public static final int MAX_DOCUMENT_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB
    public static final int MIN_DOCUMENT_SIZE_BYTES = 100; // 100 bytes minimum for valid PDF

    // Standard Alphanumeric IRDAI UIN: e.g. BAJHLIP23020V012223 or HDFHLIP23123V022223
    private static final Pattern ALPHANUMERIC_UIN_PATTERN = Pattern.compile(
            "(?i)\\b([A-Z0-9]{3,8}[A-Z0-9]{3,5}V[0-9]{6,8})\\b"
    );

    // Statutory Slash UIN: e.g. IRDA/NL-HLT/BAGI/P-H/V.I/408//13-14
    private static final Pattern SLASH_UIN_PATTERN = Pattern.compile(
            "(?i)\\b(IRDA[I]?/[A-Z0-9._/-]+)\\b"
    );

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private String sha256;
        private long byteSize;
        private int pageCount;
        private boolean hasTextLayer;
        private boolean isEncrypted;
        private String extractedUin;
        private String failureReason;
        private String integrityStatus; // PASSED, CORRUPT, TRUNCATED, NON_PDF, SIZE_EXCEEDED, ENCRYPTED, HTML_ERROR
    }

    public ValidationResult validatePdfPayload(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            return ValidationResult.builder()
                    .valid(false)
                    .failureReason("Payload is null or 0 bytes")
                    .integrityStatus("EMPTY_PAYLOAD")
                    .build();
        }

        long size = fileBytes.length;
        String sha256 = computeSha256(fileBytes);

        // 1. Size bounds check
        if (size > MAX_DOCUMENT_SIZE_BYTES) {
            return ValidationResult.builder()
                    .valid(false)
                    .sha256(sha256)
                    .byteSize(size)
                    .failureReason(String.format("File size (%d bytes) exceeds maximum configured limit of %d bytes", size, MAX_DOCUMENT_SIZE_BYTES))
                    .integrityStatus("SIZE_EXCEEDED")
                    .build();
        }

        if (size < MIN_DOCUMENT_SIZE_BYTES) {
            return ValidationResult.builder()
                    .valid(false)
                    .sha256(sha256)
                    .byteSize(size)
                    .failureReason("File size below minimum threshold for a valid PDF structure")
                    .integrityStatus("TRUNCATED")
                    .build();
        }

        // 2. HTML Error / Soft-404 detection
        String snippet = new String(fileBytes, 0, Math.min(256, fileBytes.length), StandardCharsets.UTF_8).toLowerCase();
        if (snippet.contains("<!doctype") || snippet.contains("<html") || snippet.contains("<head") || snippet.contains("<error")) {
            return ValidationResult.builder()
                    .valid(false)
                    .sha256(sha256)
                    .byteSize(size)
                    .failureReason("Payload is an HTML response masquerading as a PDF document (Soft-404 / Server Error)")
                    .integrityStatus("HTML_ERROR")
                    .build();
        }

        // 3. PDF Magic Bytes Check (%PDF-)
        if (!hasPdfMagicBytes(fileBytes)) {
            return ValidationResult.builder()
                    .valid(false)
                    .sha256(sha256)
                    .byteSize(size)
                    .failureReason("Payload missing %PDF- magic bytes header")
                    .integrityStatus("NON_PDF")
                    .build();
        }

        // 4. Structural validation & readability via Apache PDFBox
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            int pageCount = document.getNumberOfPages();
            boolean isEncrypted = document.isEncrypted();

            if (pageCount <= 0) {
                return ValidationResult.builder()
                        .valid(false)
                        .sha256(sha256)
                        .byteSize(size)
                        .failureReason("PDF document contains 0 readable pages")
                        .integrityStatus("CORRUPT")
                        .build();
            }

            // 5. Text Layer Inspection & UIN Extraction (First 3 pages)
            boolean hasTextLayer = false;
            String extractedUin = null;

            if (!isEncrypted) {
                try {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartPage(1);
                    stripper.setEndPage(Math.min(3, pageCount));
                    String text = stripper.getText(document);

                    if (text != null && text.trim().length() > 20) {
                        hasTextLayer = true;
                        extractedUin = extractUinFromText(text);
                    }
                } catch (Exception e) {
                    log.debug("[DocumentValidator] Text layer inspection encountered non-fatal parsing note: {}", e.getMessage());
                }
            }

            return ValidationResult.builder()
                    .valid(true)
                    .sha256(sha256)
                    .byteSize(size)
                    .pageCount(pageCount)
                    .hasTextLayer(hasTextLayer)
                    .isEncrypted(isEncrypted)
                    .extractedUin(extractedUin)
                    .integrityStatus("PASSED")
                    .build();

        } catch (Exception e) {
            log.warn("[DocumentValidator] PDFBox structural validation failed: {}", e.getMessage());
            return ValidationResult.builder()
                    .valid(false)
                    .sha256(sha256)
                    .byteSize(size)
                    .failureReason("PDF structure is invalid or corrupt: " + e.getMessage())
                    .integrityStatus("CORRUPT")
                    .build();
        }
    }

    private boolean hasPdfMagicBytes(byte[] bytes) {
        if (bytes.length < 5) return false;
        return bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46 && bytes[4] == 0x2D; // %PDF-
    }

    public String extractUinFromText(String text) {
        if (text == null || text.isBlank()) return null;

        // Try alphanumeric UIN first
        Matcher m1 = ALPHANUMERIC_UIN_PATTERN.matcher(text);
        if (m1.find()) {
            return m1.group(1).trim().toUpperCase();
        }

        // Fallback to statutory slash UIN
        Matcher m2 = SLASH_UIN_PATTERN.matcher(text);
        if (m2.find()) {
            return m2.group(1).trim().toUpperCase();
        }

        return null;
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
