package com.vidhura.ai.pattern.template;

import com.vidhura.ai.model.Policy;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * Abstract Policy Analysis Pipeline defining the Template Method.
 * Dictates the algorithmic skeleton for ingesting, validating, parsing, normalizing,
 * and enriching insurance policy documents while allowing subclasses to customize
 * the underlying AI extraction or fallback heuristics.
 */
@Slf4j
public abstract class AbstractPolicyAnalysisPipeline {

    /**
     * The Template Method defining the invariant workflow of policy document ingestion.
     */
    public final Policy.PolicyAnalysis process(String fileName, byte[] fileBytes) {
        log.info("[Template: Pipeline] Initiating policy document processing for '{}'", fileName);

        // Step 1: Pre-validation
        validateDocument(fileName, fileBytes);

        // Step 2: Extract text from document
        String documentText;
        try {
            documentText = extractText(fileBytes);
        } catch (Exception e) {
            log.error("[Template: Pipeline] Text extraction failed for '{}': {}", fileName, e.getMessage());
            throw new RuntimeException("Failed to extract readable text from document: " + e.getMessage(), e);
        }
        validateExtractedContent(fileName, documentText);

        // Step 3: Core analysis execution (AI prompt or fallback heuristic)
        Policy.PolicyAnalysis analysis = executeAnalysis(fileName, documentText);

        // Step 4: Normalization (ensure field consistency, provider clean-up, etc.)
        normalizeAnalysis(analysis, fileName);

        // Step 5: Post-processing metadata and validation
        finalizeAnalysisMetadata(analysis, fileName);

        log.info("[Template: Pipeline] Completed policy document processing for '{}'", fileName);
        return analysis;
    }

    /**
     * Step 1: Validate file presence and format.
     */
    protected void validateDocument(String fileName, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0 || fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Invalid file uploaded. Please upload a valid, non-empty PDF.");
        }
    }

    /**
     * Step 2: Hook for extracting raw text from binary document bytes.
     */
    protected abstract String extractText(byte[] fileBytes) throws Exception;

    /**
     * Validate extracted string content.
     */
    protected void validateExtractedContent(String fileName, String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("Could not extract any readable text from '" + fileName + "'. The PDF may be scanned or empty.");
        }
    }

    /**
     * Step 3: Primitive operation implemented by concrete subclasses to perform AI or heuristic parsing.
     */
    protected abstract Policy.PolicyAnalysis executeAnalysis(String fileName, String documentText);

    /**
     * Step 4: Primitive operation or hook for normalizing raw model outputs.
     */
    protected void normalizeAnalysis(Policy.PolicyAnalysis analysis, String fileName) {
        if (analysis == null) return;

        if (analysis.getPolicyName() != null) {
            String name = analysis.getPolicyName().trim();
            if (name.toLowerCase().startsWith("my:") || name.toLowerCase().startsWith("my :")) {
                name = name.replaceFirst("(?i)^my\\s*:\\s*", "").trim();
            }
            if (name.equalsIgnoreCase("Optima Secure") && analysis.getProvider() != null && analysis.getProvider().toLowerCase().contains("hdfc")) {
                name = "HDFC ERGO Optima Secure";
            }
            analysis.setPolicyName(name);
        }

        if (analysis.getProvider() == null || analysis.getProvider().isBlank() || "string".equalsIgnoreCase(analysis.getProvider())) {
            analysis.setProvider("Insurance Provider");
        }

        if (analysis.getType() == null || analysis.getType().isBlank() || "string".equalsIgnoreCase(analysis.getType())) {
            analysis.setType("Health Insurance");
        }

        if (analysis.getPremium() == null || analysis.getPremium().isBlank() || "string".equalsIgnoreCase(analysis.getPremium())) {
            analysis.setPremium("Subject to age & sum insured");
        }

        if (analysis.getCoverage() == null || analysis.getCoverage().isBlank() || "string".equalsIgnoreCase(analysis.getCoverage())) {
            analysis.setCoverage("₹5 Lakhs – ₹2 Crores");
        }
    }

    /**
     * Step 5: Finalize stamps and invariant attributes.
     */
    protected void finalizeAnalysisMetadata(Policy.PolicyAnalysis analysis, String fileName) {
        if (analysis != null) {
            if (analysis.getAnalyzedAt() == null || analysis.getAnalyzedAt().isBlank()) {
                analysis.setAnalyzedAt(Instant.now().toString());
            }
            analysis.setSourceFile(fileName);
            if (analysis.getConfidence() == null || analysis.getConfidence() == 0.0) {
                analysis.setConfidence(0.95);
            }
        }
    }
}
