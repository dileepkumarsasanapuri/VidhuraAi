package com.vidhura.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyAnalyzerServiceTest {

    @Test
    void testLegacyModelNameResolution() {
        PolicyAnalyzerService service = new PolicyAnalyzerService(new PdfParsingService());
        ReflectionTestUtils.setField(service, "hfModel", "meta-llama/Meta-Llama-3-8B-Instruct");
        
        String resolved = (String) ReflectionTestUtils.invokeMethod(service, "resolveModel");
        assertEquals("meta-llama/Llama-3.3-70B-Instruct", resolved);
    }

    @Test
    void testHeuristicAnalysisExtraction() {
        PolicyAnalyzerService service = new PolicyAnalyzerService(new PdfParsingService());
        String mockPdfText = "HDFC ERGO Optima Secure Health Insurance Policy\n"
                + "Provider: HDFC ERGO General Insurance Company Limited\n"
                + "Sum Insured: INR 15,00,000. Annual Premium: INR 16,800.\n"
                + "Exclusions: Cosmetic surgery, dental care without hospital admission.\n"
                + "Waiting period: 30 days initial waiting period. 24 months for pre-existing diseases.";

        com.vidhura.ai.model.Policy.PolicyAnalysis result = service.buildHeuristicAnalysis("hdfc_optima.pdf", mockPdfText);

        assertNotNull(result);
        assertTrue(result.getPolicyName().contains("Optima Secure"));
        assertTrue(result.getProvider().contains("HDFC ERGO"));
        assertNotNull(result.getClauses());
        assertFalse(result.getClauses().isEmpty());
        assertNotNull(result.getExclusions());
        assertFalse(result.getExclusions().isEmpty());
        assertNotNull(result.getHiddenConditions());
        assertFalse(result.getHiddenConditions().isEmpty());
    }

    @Test
    void testAnalyzePolicyGracefullyHandlesPdfAndExtractsData() throws Exception {
        byte[] pdfBytes;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("HDFC ERGO Optima Secure Health Insurance Policy");
                cs.newLineAtOffset(0, -20);
                cs.showText("Provider: HDFC ERGO General Insurance");
                cs.newLineAtOffset(0, -20);
                cs.showText("Sum Insured: INR 15,00,000. Annual Premium: INR 16,800.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Exclusions: Cosmetic surgery, dental care without hospital admission.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Waiting period: 30 days initial waiting period. 24 months for pre-existing diseases.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Benefits: Road ambulance covered. 100% restoration recharge benefit. AYUSH treatment.");
                cs.endText();
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        PolicyAnalyzerService service = new PolicyAnalyzerService(new PdfParsingService());

        // analyzePolicy should never crash even without a paid token; it falls back to heuristic extraction
        com.vidhura.ai.model.Policy.PolicyAnalysis result = service.analyzePolicy("hdfc_optima.pdf", pdfBytes);

        assertNotNull(result);
        assertTrue(result.getPolicyName().toLowerCase().contains("hdfc") || result.getPolicyName().toLowerCase().contains("optima"),
                "Should extract HDFC Optima from document: " + result.getPolicyName());
        assertNotNull(result.getClauses());
        assertTrue(result.getClauses().size() >= 3);
        assertNotNull(result.getExclusions());
        assertTrue(result.getExclusions().size() >= 3);
        assertNotNull(result.getScores());
    }
}
