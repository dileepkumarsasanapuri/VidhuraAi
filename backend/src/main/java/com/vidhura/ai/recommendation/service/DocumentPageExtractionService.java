package com.vidhura.ai.recommendation.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Phase 6.3: Page-Oriented Document Processing and Text Extraction Service.
 *
 * Extracts text page-by-page from original immutable policy PDFs, segments clause boundaries,
 * detects tables and headings, and writes derived text to external storage.
 */
@Slf4j
@Service
public class DocumentPageExtractionService {

    private final Path storageRoot;
    private final Path extractedRoot;

    // Pattern for detecting section headings like "SECTION 1", "1.1", "Clause 4", etc.
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(SECTION\\s+\\d+|CLAUSE\\s+\\d+|\\d+\\.\\d+|PART\\s+[A-Z]|SCHEDULE\\s+[A-Z0-9]+|BENEFIT\\s+\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for table rows with columns separated by multiple spaces or tabs
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile(
            "^(.+?)\\s{3,}(.+?)(?:\\s{3,}(.+?))?$"
    );

    public DocumentPageExtractionService(
            @Value("${vidhura.policy.storage.root:E:\\VidhuraData\\policies}") String configuredRoot,
            @Value("${vidhura.policy.extraction.root:}") String configuredExtractedRoot) {
        String effectiveRoot = System.getenv("VIDHURA_POLICY_STORAGE_ROOT");
        if (effectiveRoot == null || effectiveRoot.isBlank()) {
            effectiveRoot = configuredRoot;
        }
        this.storageRoot = Path.of(effectiveRoot).toAbsolutePath().normalize();

        String effectiveExtracted = System.getenv("VIDHURA_POLICY_EXTRACTION_ROOT");
        if (effectiveExtracted == null || effectiveExtracted.isBlank()) {
            effectiveExtracted = configuredExtractedRoot != null && !configuredExtractedRoot.isBlank()
                    ? configuredExtractedRoot
                    : this.storageRoot.resolve("extracted").toString();
        }
        this.extractedRoot = Path.of(effectiveExtracted).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.extractedRoot);
            log.info("[PageExtractionService] Extracted artifacts root: {}", this.extractedRoot);
        } catch (IOException e) {
            log.error("[PageExtractionService] Failed to initialize extracted storage root: {}", e.getMessage());
        }
    }

    /**
     * Extracts text page by page from an immutable original PDF file.
     */
    public DocumentPagesResult extractPages(File pdfFile, String expectedSha256) throws IOException {
        if (!pdfFile.exists() || pdfFile.length() == 0) {
            throw new IllegalArgumentException("PDF file does not exist or is empty: " + pdfFile.getAbsolutePath());
        }

        // 1. Verify SHA-256 integrity
        byte[] fileBytes = Files.readAllBytes(pdfFile.toPath());
        String computedSha256 = computeSha256(fileBytes);
        if (expectedSha256 != null && !expectedSha256.isBlank()) {
            if (!computedSha256.equalsIgnoreCase(expectedSha256.trim())) {
                throw new SecurityException(String.format("Cryptographic hash mismatch for %s: Expected %s but found %s",
                        pdfFile.getName(), expectedSha256, computedSha256));
            }
        }

        Path docExtractedDir = this.extractedRoot.resolve(computedSha256);
        Files.createDirectories(docExtractedDir);

        List<PageContent> pages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int scannedPageCount = 0;
        int emptyPageCount = 0;

        try (PDDocument doc = Loader.loadPDF(fileBytes)) {
            int totalPages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(doc);

                boolean isEmpty = pageText == null || pageText.trim().isEmpty();
                boolean isScanned = !isEmpty && pageText.trim().length() < 40; // Scanned page has negligible extractable glyphs

                if (isEmpty) {
                    emptyPageCount++;
                    warnings.add("Page " + pageNum + ": completely empty or unrenderable");
                } else if (isScanned) {
                    scannedPageCount++;
                    warnings.add("Page " + pageNum + ": scanned page with minimal text (" + pageText.trim().length() + " chars)");
                }

                // Segment clauses & detect tables
                List<ClauseSegment> segments = segmentPageText(pageText, pageNum);

                // Write page text to external storage
                Path pageFile = docExtractedDir.resolve("page_" + pageNum + ".txt");
                Files.writeString(pageFile, pageText != null ? pageText : "");

                pages.add(PageContent.builder()
                        .pageNumber(pageNum)
                        .rawText(pageText != null ? pageText.trim() : "")
                        .characterCount(pageText != null ? pageText.trim().length() : 0)
                        .empty(isEmpty)
                        .scanned(isScanned)
                        .segments(segments)
                        .pageArtifactPath(pageFile.toString())
                        .build());
            }

            log.info("[PageExtraction] Processed {} pages for {}: {} scanned, {} empty",
                    totalPages, computedSha256, scannedPageCount, emptyPageCount);

            return DocumentPagesResult.builder()
                    .documentSha256(computedSha256)
                    .totalPages(totalPages)
                    .scannedPagesCount(scannedPageCount)
                    .emptyPagesCount(emptyPageCount)
                    .pages(pages)
                    .warnings(warnings)
                    .extractedStorageDir(docExtractedDir.toString())
                    .extractedAt(Instant.now())
                    .build();
        }
    }

    /**
     * Segments raw page text into structured blocks, headings, paragraphs, and tables.
     */
    public List<ClauseSegment> segmentPageText(String pageText, int pageNum) {
        if (pageText == null || pageText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<ClauseSegment> segments = new ArrayList<>();
        String[] lines = pageText.split("\\r?\\n");
        String currentHeading = "Page " + pageNum;
        StringBuilder currentBlock = new StringBuilder();
        int segmentIndex = 0;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (HEADING_PATTERN.matcher(line).find() || (line.length() < 70 && line.equals(line.toUpperCase()) && line.matches(".*[A-Z].*"))) {
                // Flush previous block
                if (currentBlock.length() > 0) {
                    segmentIndex++;
                    segments.add(createSegment(currentBlock.toString(), currentHeading, pageNum, segmentIndex));
                    currentBlock.setLength(0);
                }
                currentHeading = line;
            } else {
                if (currentBlock.length() > 0) {
                    currentBlock.append(" ");
                }
                currentBlock.append(line);
            }
        }

        if (currentBlock.length() > 0) {
            segmentIndex++;
            segments.add(createSegment(currentBlock.toString(), currentHeading, pageNum, segmentIndex));
        }

        return segments;
    }

    private ClauseSegment createSegment(String text, String heading, int pageNum, int index) {
        boolean isTable = TABLE_ROW_PATTERN.matcher(text).find() || text.contains(" | ");
        return ClauseSegment.builder()
                .segmentId("P" + pageNum + "_S" + index)
                .pageNumber(pageNum)
                .heading(heading)
                .content(text)
                .table(isTable)
                .characterCount(text.length())
                .build();
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    public Path getExtractedRoot() {
        return extractedRoot;
    }

    @Data
    @Builder
    public static class DocumentPagesResult {
        private String documentSha256;
        private int totalPages;
        private int scannedPagesCount;
        private int emptyPagesCount;
        private List<PageContent> pages;
        private List<String> warnings;
        private String extractedStorageDir;
        private Instant extractedAt;
    }

    @Data
    @Builder
    public static class PageContent {
        private int pageNumber;
        private String rawText;
        private int characterCount;
        private boolean empty;
        private boolean scanned;
        private List<ClauseSegment> segments;
        private String pageArtifactPath;
    }

    @Data
    @Builder
    public static class ClauseSegment {
        private String segmentId;
        private int pageNumber;
        private String heading;
        private String content;
        private boolean table;
        private int characterCount;
    }
}
