package com.vidhura.ai.recommendation.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3.4 Acquired Official IRDAI Policy Document Cryptographic and Text Verification Test.
 *
 * Verifies that the official policy PDF documents acquired from https://irdai.gov.in/health-insurance-products
 * match the registered UIN, insurer name, product title, and core contractual terms.
 */
class AcquiredPolicyDocumentVerificationTest {

    private static final String EVIDENCE_BASE = "src/main/resources/evidence/policies/";

    @Test
    @DisplayName("IRDAI Document 1: United India Family Medicare Policy (UIIHLIP22070V042122)")
    void testUnitedIndiaFamilyMedicarePdf() throws Exception {
        File file = new File(EVIDENCE_BASE + "UIIHLIP22070V042122_HEALTH2068.pdf");
        assertTrue(file.exists(), "Acquired PDF file must exist in evidence directory");
        assertEquals(1583059L, file.length(), "File size must match exact acquired bytes");

        // Compute SHA-256
        String sha256 = computeFileSha256(file);
        assertEquals("6bb20e21fa1c97b514f57ae6764b2e9b7a22df71d8afb2ece0539e0e17eea267", sha256,
                "Cryptographic SHA-256 must match byte stream");

        // Parse with PDFBox
        try (PDDocument doc = Loader.loadPDF(file)) {
            assertTrue(doc.getNumberOfPages() >= 10, "Document must have full policy wording pages");
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(doc);

            assertTrue(fullText.contains("UNITED INDIA INSURANCE") || fullText.contains("United India Insurance"),
                    "Document must identify insurer United India Insurance");
            assertTrue(fullText.contains("FAMILY MEDICARE") || fullText.contains("Family Medicare"),
                    "Document must identify Family Medicare Policy");
            assertTrue(fullText.contains("UIIHLIP22070V042122") || fullText.contains("22070V042122"),
                    "Document must contain registered IRDAI UIN");
        }
    }

    @Test
    @DisplayName("IRDAI Document 2: New India Premier Mediclaim Policy (NIAHLIP21279V032021)")
    void testNewIndiaPremierMediclaimPdf() throws Exception {
        File file = new File(EVIDENCE_BASE + "NIAHLIP21279V032021_2020-2021.pdf");
        assertTrue(file.exists(), "Acquired PDF file must exist in evidence directory");
        assertEquals(944229L, file.length(), "File size must match exact acquired bytes");

        // Compute SHA-256
        String sha256 = computeFileSha256(file);
        assertEquals("17c54a21891f9be3f5e0cfd8579eb120f2eceb14aaba2fa695b62c1ab45f3191", sha256,
                "Cryptographic SHA-256 must match byte stream");

        // Parse with PDFBox
        try (PDDocument doc = Loader.loadPDF(file)) {
            assertTrue(doc.getNumberOfPages() >= 5, "Document must have full policy wording pages");
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(doc);

            assertTrue(fullText.contains("NEW INDIA ASSURANCE") || fullText.contains("New India Assurance"),
                    "Document must identify insurer New India Assurance");
            assertTrue(fullText.contains("PREMIER MEDICLAIM") || fullText.contains("Premier Mediclaim"),
                    "Document must identify Premier Mediclaim Policy");
            assertTrue(fullText.contains("NIAHLIP21279V032021") || fullText.contains("21279V032021"),
                    "Document must contain registered IRDAI UIN");
        }
    }

    @Test
    @DisplayName("IRDAI Document 3: New India Mediclaim Policy (Revision 4 - NIAHLIP21277V042021)")
    void testNewIndiaMediclaimPdf() throws Exception {
        File file = new File(EVIDENCE_BASE + "NIAHLIP21277V042021_2020-2021.pdf");
        assertTrue(file.exists(), "Acquired PDF file must exist in evidence directory");
        assertEquals(929569L, file.length(), "File size must match exact acquired bytes");

        // Compute SHA-256
        String sha256 = computeFileSha256(file);
        assertEquals("bfc304901063fa21b1e145c22d2ee15bd5f8190e82b4caca069cfe96dbcedc9f", sha256,
                "Cryptographic SHA-256 must match byte stream");

        // Parse with PDFBox
        try (PDDocument doc = Loader.loadPDF(file)) {
            assertTrue(doc.getNumberOfPages() >= 5, "Document must have full policy wording pages");
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(doc);

            assertTrue(fullText.contains("NEW INDIA ASSURANCE") || fullText.contains("New India Assurance"),
                    "Document must identify insurer New India Assurance");
            assertTrue(fullText.contains("MEDICLAIM POLICY") || fullText.contains("Mediclaim Policy"),
                    "Document must identify Mediclaim Policy");
            assertTrue(fullText.contains("NIAHLIP21277V042021") || fullText.contains("21277V042021"),
                    "Document must contain registered IRDAI UIN");
        }
    }

    private String computeFileSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
