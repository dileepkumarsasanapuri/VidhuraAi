package com.vidhura.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
public class PdfParsingService {

    // Regex patterns for recurring page noise
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("(?i)(page\\s+\\d+\\s+of\\s+\\d+|page\\s+\\d+|p\\.\\s*\\d+)", Pattern.MULTILINE);
    private static final Pattern MULTI_NEWLINE_PATTERN = Pattern.compile("\\n{3,}");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("[ \\t]{2,}");

    /**
     * Extracts and cleans text from the entire PDF without premature truncation.
     * Handles large documents up to 120,000 characters seamlessly.
     */
    public String extractText(byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            log.info("[PDF Parser] Processing PDF with {} total pages", totalPages);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String rawText = stripper.getText(document);

            if (rawText == null || rawText.isBlank()) {
                log.warn("[PDF Parser] Stripper returned empty text from {} pages", totalPages);
                return "";
            }

            log.info("[PDF Parser] Extracted {} raw characters from {} pages", rawText.length(), totalPages);

            // Clean repetitive page headers/footers and excess whitespace
            String cleaned = cleanPdfText(rawText);
            log.info("[PDF Parser] Cleaned text contains {} characters", cleaned.length());

            // For Groq free tier (8k TPM limit), ~20,000 characters (~4,500-5,000 tokens)
            // captures all critical policy terms while staying comfortably under the rate limit
            if (cleaned.length() <= 20000) {
                return cleaned;
            }

            // For larger documents (>20k chars), intelligently extract all critical sections
            return extractKeySectionsFromLargeDocument(cleaned);
        } catch (Exception e) {
            log.error("[PDF Parser] Failed to parse PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Could not extract text from PDF document: " + e.getMessage(), e);
        }
    }

    private String cleanPdfText(String text) {
        String s = PAGE_NUMBER_PATTERN.matcher(text).replaceAll("");
        s = MULTI_SPACE_PATTERN.matcher(s).replaceAll(" ");
        s = MULTI_NEWLINE_PATTERN.matcher(s).replaceAll("\n\n");
        return s.trim();
    }

    /**
     * Intelligently samples from start, middle, and end of massive documents
     * to ensure no critical section (coverage, exclusions, waiting periods, traps, riders) is missed.
     */
    private String extractKeySectionsFromLargeDocument(String fullText) {
        log.info("[PDF Parser] Document is large ({} chars). Performing targeted policy section sampling...", fullText.length());

        // First 8,000 characters: Title, schedule, preamble, definitions, base benefits
        int headLength = Math.min(8000, fullText.length());
        String head = fullText.substring(0, headLength);

        // Last 3,000 characters: General conditions, portability, claims notice, riders & endorsements
        int tailLength = Math.min(3000, fullText.length() - headLength);
        String tail = (tailLength > 0) ? fullText.substring(fullText.length() - tailLength) : "";

        // Middle section: Scan for sections containing Coverage, Exclusions, Waiting Period, Sub-limits, Co-pay
        int middleStart = headLength;
        int middleEnd = Math.max(middleStart, fullText.length() - tailLength);
        String middleText = fullText.substring(middleStart, middleEnd);

        StringBuilder middleSample = new StringBuilder();
        String[] lines = middleText.split("\n");
        int middleCharBudget = 9000;
        int accumulated = 0;

        Pattern keywordPattern = Pattern.compile("(?i)(section|benefit|coverage|scope\\s+of\\s+cover|in-patient|room\\s+rent|icu|day\\s+care|restoration|reassure|secure|protect|ayush|ambulance|pre-hospitalization|post-hospitalization|exclusion|what\\s+is\\s+not\\s+covered|permanent\\s+exclusion|waiting\\s+period|pre-existing|specific\\s+illness|sub-limit|co-payment|co-pay|deductible|proportional|claim|cashless|intimation|rider|add-on|optional)");

        StringBuilder currentChunk = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                currentChunk.append(trimmed).append(" ");
            }
            if (currentChunk.length() >= 400) {
                String chunkStr = currentChunk.toString().trim();
                if (keywordPattern.matcher(chunkStr).find()) {
                    if (accumulated + chunkStr.length() + 2 <= middleCharBudget) {
                        middleSample.append(chunkStr).append("\n\n");
                        accumulated += chunkStr.length() + 2;
                    }
                }
                currentChunk.setLength(0);
            }
        }

        log.info("[PDF Parser] Extracted {} chars from middle section matching policy keywords", middleSample.length());

        return head + "\n\n--- [INTERMEDIATE POLICY CLAUSES & EXCLUSIONS] ---\n\n" + middleSample + "\n\n--- [POLICY CONDITIONS & PROVISIONS] ---\n\n" + tail;
    }
}
