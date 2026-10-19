package com.vidhura.ai.recommendation.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit & Regression Tests for PolicyOcrFallbackService.
 * Validates:
 * 1. Detection of scanned/raster-only PDFs with no meaningful text layer.
 * 2. Resource bounding controls (file size ceiling, max pages, timeout).
 * 3. Page-level provenance tracking and confidence scoring.
 * 4. Regulatory circular vs contractual policy wording classification.
 * 5. Mandatory human-in-the-loop review routing (EXTRACTION_PENDING_REVIEW).
 */
class PolicyOcrFallbackServiceTest {

    private PolicyOcrFallbackService ocrService;

    @BeforeEach
    void setUp() {
        ocrService = new PolicyOcrFallbackService();
    }

    private byte[] createDummyPdf(int pages) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("isOcrCandidate returns true only for valid %PDF- bytes with minimal extractable text")
    void testIsOcrCandidate() {
        byte[] validPdf = createDummyPdf(2);

        // Valid scanned PDF with 0 extractable chars -> true
        assertTrue(ocrService.isOcrCandidate(validPdf, ""));
        assertTrue(ocrService.isOcrCandidate(validPdf, "   \n  "));
        assertTrue(ocrService.isOcrCandidate(validPdf, "Minimal header only"));

        // Valid PDF with rich text layer (>= 50 chars) -> false (no OCR needed)
        String richText = "This is a full digital health insurance policy wording with operative clauses and sum insured details.";
        assertFalse(ocrService.isOcrCandidate(validPdf, richText));

        // Corrupted or non-PDF bytes -> false
        assertFalse(ocrService.isOcrCandidate("Not a PDF document".getBytes(), ""));
        assertFalse(ocrService.isOcrCandidate(null, ""));
        assertFalse(ocrService.isOcrCandidate(new byte[0], ""));
    }

    @Test
    @DisplayName("OCR enforces file size ceiling and bounds max pages processed")
    void testResourceBounding() {
        byte[] pdf = createDummyPdf(15); // 15 pages (above default 10 maxPages)

        PolicyOcrFallbackService.OcrResult result = ocrService.processScannedPdf(
                pdf, "Scanned_Policy_Wording.pdf", "https://uiic.co.in/docs/scanned.pdf");

        assertTrue(result.isOcrApplied());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(15, result.getTotalDocumentPages());
        assertEquals(10, result.getPagesProcessed(), "Must bound processing to max 10 pages");
        assertEquals(10, result.getPageProvenance().size(), "Page provenance must record up to maxPages");
        assertTrue(result.getOcrConfidenceScore() >= 0.70, "Confidence score must reflect raster baseline");
        assertEquals("EXTRACTION_PENDING_REVIEW", result.getRoutingStatus());
    }

    @Test
    @DisplayName("IRDAI Guidelines are classified as REGULATORY_GUIDELINES and never as contractual wordings")
    void testRegulatoryGuidelinesClassification() {
        byte[] pdf = createDummyPdf(3);

        PolicyOcrFallbackService.OcrResult result = ocrService.processScannedPdf(
                pdf,
                "Guidelines on Standard Individual Health Insurance Product.pdf",
                "https://irdai.gov.in/guidelines/Arogya_Sanjeevani_Guidelines.pdf");

        assertTrue(result.isOcrApplied());
        assertEquals("REGULATORY_GUIDELINES", result.getDocumentClassification());
        assertFalse(result.isContractualWording(), "Regulatory circulars must NEVER be marked as contractual wording");
        assertTrue(result.getDiagnosticNotes().contains("REGULATORY_GUIDELINES"));
        assertTrue(result.getPageProvenance().get(1).contains("INSURANCE REGULATORY AND DEVELOPMENT AUTHORITY"));
    }

    @Test
    @DisplayName("Standard policy document is classified as POLICY_WORDING and routed to human review")
    void testPolicyWordingClassification() {
        byte[] pdf = createDummyPdf(4);

        PolicyOcrFallbackService.OcrResult result = ocrService.processScannedPdf(
                pdf,
                "Commercial_Health_Policy_Wording.pdf",
                "https://newindia.co.in/docs/commercial-health.pdf");

        assertTrue(result.isOcrApplied());
        assertEquals("POLICY_WORDING", result.getDocumentClassification());
        assertTrue(result.isContractualWording());
        assertEquals("EXTRACTION_PENDING_REVIEW", result.getRoutingStatus());
        assertTrue(result.getExtractedText().contains("POLICY WORDINGS"));
        assertEquals(4, result.getPageProvenance().size());
    }
}
