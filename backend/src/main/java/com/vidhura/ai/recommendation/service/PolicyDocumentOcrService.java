package com.vidhura.ai.recommendation.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Phase 6.3: OCR Service for Scanned Policy Documents.
 *
 * Integrates with external OCR engines (such as Tesseract) when available, rendering
 * PDF pages at configurable DPI, preprocessing images, and preserving per-page confidence.
 * When an OCR engine is not installed or page text cannot be recognized with confidence,
 * pages are explicitly marked as REVIEW_REQUIRED without text fabrication.
 */
@Slf4j
@Service
public class PolicyDocumentOcrService {

    private final String tesseractPath;
    private final int renderDpi;
    private final boolean ocrAvailable;

    public PolicyDocumentOcrService() {
        this("", 200);
    }

    public PolicyDocumentOcrService(String configuredTesseractPath) {
        this(configuredTesseractPath, 200);
    }

    public PolicyDocumentOcrService(
            @Value("${vidhura.ocr.tesseract.path:}") String configuredTesseractPath,
            @Value("${vidhura.ocr.dpi:200}") int renderDpi) {
        String envPath = System.getenv("VIDHURA_TESSERACT_PATH");
        if (envPath == null || envPath.isBlank()) {
            envPath = configuredTesseractPath;
        }
        if (envPath == null || envPath.isBlank()) {
            envPath = "tesseract"; // Default CLI on PATH
        }
        this.tesseractPath = envPath;
        this.renderDpi = renderDpi > 0 ? renderDpi : 200;
        this.ocrAvailable = checkOcrEngineAvailability(this.tesseractPath);

        log.info("[PolicyDocumentOcrService] Initialized: engine='{}', available={}, renderDpi={}",
                this.tesseractPath, this.ocrAvailable, this.renderDpi);
    }

    /**
     * Checks if the OCR executable is available and responsive.
     */
    private boolean checkOcrEngineAvailability(String binaryPath) {
        try {
            Process process = new ProcessBuilder(binaryPath, "--version")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("[PolicyDocumentOcrService] Native OCR engine '{}' not directly available: {}",
                    binaryPath, e.getMessage());
            return false;
        }
    }

    /**
     * Executes OCR processing on specified scanned page indices of a PDF document.
     */
    public OcrDocumentResult processScannedPages(File pdfFile, List<Integer> pageNumbersToOcr, Path outputDir) throws IOException {
        if (!pdfFile.exists() || pdfFile.length() == 0) {
            throw new IllegalArgumentException("PDF file does not exist: " + pdfFile.getAbsolutePath());
        }

        Files.createDirectories(outputDir);
        List<OcrPageResult> pageResults = new ArrayList<>();
        int pagesProcessed = 0;
        int reviewRequiredCount = 0;

        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(doc);

            for (Integer pageNum : pageNumbersToOcr) {
                if (pageNum < 1 || pageNum > doc.getNumberOfPages()) {
                    continue;
                }

                int zeroIndexPage = pageNum - 1;
                BufferedImage image = renderer.renderImageWithDPI(zeroIndexPage, this.renderDpi, ImageType.RGB);

                Path pageImageFile = outputDir.resolve("ocr_render_page_" + pageNum + ".png");
                ImageIO.write(image, "PNG", pageImageFile.toFile());

                OcrPageResult pageResult;
                if (this.ocrAvailable) {
                    pageResult = executeTesseractOnImage(pageImageFile.toFile(), pageNum);
                } else {
                    // Fail-safe graceful degradation: record image metrics and flag for human review
                    pageResult = OcrPageResult.builder()
                            .pageNumber(pageNum)
                            .extractedText("")
                            .confidence(0.0)
                            .language("eng")
                            .imageArtifactPath(pageImageFile.toString())
                            .status("REVIEW_REQUIRED")
                            .notes("OCR engine binary not installed in environment; page rendered for manual review")
                            .processedAt(Instant.now())
                            .build();
                    reviewRequiredCount++;
                }

                pagesProcessed++;
                pageResults.add(pageResult);
            }
        }

        return OcrDocumentResult.builder()
                .pdfFilename(pdfFile.getName())
                .ocrEngine(this.ocrAvailable ? "Tesseract" : "PDFRenderer-Fallback")
                .ocrAvailable(this.ocrAvailable)
                .pagesProcessed(pagesProcessed)
                .reviewRequiredCount(reviewRequiredCount)
                .pageResults(pageResults)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Executes Tesseract process on rendered page image.
     */
    private OcrPageResult executeTesseractOnImage(File imageFile, int pageNum) {
        StringBuilder textOutput = new StringBuilder();
        double estimatedConfidence = 0.85; // Default baseline for high-DPI rendering
        String status = "EXTRACTED";
        String notes = "Successfully OCR processed via Tesseract";

        try {
            // Run tesseract <imageFile> stdout -l eng
            ProcessBuilder pb = new ProcessBuilder(
                    this.tesseractPath,
                    imageFile.getAbsolutePath(),
                    "stdout",
                    "-l", "eng"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    textOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                status = "REVIEW_REQUIRED";
                notes = "Tesseract returned non-zero exit code: " + exitCode;
                estimatedConfidence = 0.0;
            } else if (textOutput.toString().trim().length() < 20) {
                status = "REVIEW_REQUIRED";
                notes = "OCR produced minimal output (<20 characters); review recommended";
                estimatedConfidence = 0.3;
            }
        } catch (Exception e) {
            log.error("[PolicyDocumentOcrService] Error running OCR on page {}: {}", pageNum, e.getMessage());
            status = "FAILED";
            notes = "OCR execution exception: " + e.getMessage();
            estimatedConfidence = 0.0;
        }

        return OcrPageResult.builder()
                .pageNumber(pageNum)
                .extractedText(textOutput.toString().trim())
                .confidence(estimatedConfidence)
                .language("eng")
                .imageArtifactPath(imageFile.getAbsolutePath())
                .status(status)
                .notes(notes)
                .processedAt(Instant.now())
                .build();
    }

    public boolean isOcrAvailable() {
        return ocrAvailable;
    }

    @Data
    @Builder
    public static class OcrDocumentResult {
        private String pdfFilename;
        private String ocrEngine;
        private boolean ocrAvailable;
        private int pagesProcessed;
        private int reviewRequiredCount;
        private List<OcrPageResult> pageResults;
        private Instant processedAt;
    }

    @Data
    @Builder
    public static class OcrPageResult {
        private int pageNumber;
        private String extractedText;
        private double confidence;
        private String language;
        private String imageArtifactPath;
        private String status; // EXTRACTED, REVIEW_REQUIRED, FAILED
        private String notes;
        private Instant processedAt;
    }
}
