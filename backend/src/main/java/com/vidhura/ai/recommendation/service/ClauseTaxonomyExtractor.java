package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.PolicyDocumentExtraction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 6.4: Clause Taxonomy and Evidence-Hardened Contract Term Extractor.
 *
 * Implements:
 * - Evidence-role classification: DEFINITION, OPERATIVE_CLAUSE, BENEFIT_LIMIT, POLICY_SCHEDULE, EXCLUSION, WAITING_PERIOD, CONDITION, EXAMPLE, GENERAL_INFORMATION, UNKNOWN
 * - Definition vs. Operative clause segregation: Prevents generic definitions from satisfying benefit limits.
 * - Multi-candidate tracking and cross-page conflict detection with candidate preservation.
 * - Enriched IRDAI exclusion standardization (Excl01, Excl02, Excl03).
 * - Versioned taxonomy: v1.1.0-evidence-hardened (backward-compatible with v1.0.0-deterministic).
 */
@Slf4j
@Service
public class ClauseTaxonomyExtractor {

    public static final String ENGINE_VERSION = "v1.1.0-evidence-hardened";
    public static final String LEGACY_ENGINE_VERSION = "v1.0.0-deterministic";

    // --- Regex Patterns for Clause Extraction ---
    private static final Pattern ROOM_RENT_PATTERN = Pattern.compile(
            "(?i)(?:room(?:\\s+rent)?,?\\s*(?:boarding(?:\\s*and\\s*nursing)?(?:\\s*expenses)?)?)\\s*[:\\-]?\\s*(?:is\\s+)?(?:admissible\\s+)?(?:up\\s+to\\s+|capped\\s+at\\s+|limit(?:ed)?\\s+to\\s+)?([^.:\\n]{3,150}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ICU_PATTERN = Pattern.compile(
            "(?i)(?:icu|intensive\\s+care\\s+unit)\\s*(?:charges|expenses)?\\s*[:\\-]?\\s*(?:are\\s+)?(?:admissible\\s+)?(?:up\\s+to\\s+|capped\\s+at\\s+|limit(?:ed)?\\s+to\\s+)?([^.:\\n]{3,150}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COPAY_PATTERN = Pattern.compile(
            "(?i)(?:co-?payment|co-?pay)\\s*[:\\-]?\\s*(?:of\\s+)?([^.:\\n]{2,120}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PED_WAITING_PATTERN = Pattern.compile(
            "(?i)(?:pre-?existing(?:\\s+diseases?)?|ped|code[\\s\\-]*excl01)[^.;]{0,250}?(?:waiting\\s+period|shall\\s+be\\s+excluded\\s+until(?:\\s+the\\s+expiry\\s+of)?|excluded\\s+until|covered\\s+after)[^.;\\d]{0,50}[:\\-]?\\s*(\\d+)\\s*(months?|years?|days?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern INITIAL_WAITING_PATTERN = Pattern.compile(
            "(?i)(?:initial\\s+waiting\\s+period|first\\s+30\\s+days|30[\\s\\-]*days?\\s+waiting|waiting\\s+period\\s+of\\s+30\\s+days|code[\\s\\-]*excl03)[^.:\\n\\d]{0,40}[:\\-]?\\s*(\\d+)\\s*(days?|months?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SPECIFIC_DISEASE_WAITING_PATTERN = Pattern.compile(
            "(?i)(?:specific(?:\\s+listed)?\\s+(?:diseases?|illnesses?|procedures?)|specified\\s+disease|named\\s+illnesses?|code[\\s\\-]*excl02)[^.:\\n]{0,80}?(?:waiting\\s+period|shall\\s+be\\s+excluded\\s+until(?:\\s+the\\s+expiry\\s+of)?|covered\\s+after)[^.:\\n\\d]{0,30}[:\\-]?\\s*(\\d+)\\s*(months?|years?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PRE_HOSP_PATTERN = Pattern.compile(
            "(?i)(?:pre-?hospitali[sz]ation)[^.:\\n\\d]{0,40}[:\\-]?\\s*(\\d+)\\s*days?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern POST_HOSP_PATTERN = Pattern.compile(
            "(?i)(?:post-?hospitali[sz]ation)[^.:\\n\\d]{0,40}[:\\-]?\\s*(\\d+)\\s*days?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RESTORATION_PATTERN = Pattern.compile(
            "(?i)(?:restoration|reinstatement|reset|reload)\\s+(?:of\\s+)?(?:basic\\s+)?sum\\s+insured[^.:\\n]{0,40}[:\\-]?\\s*([^.\\n]{10,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NCB_PATTERN = Pattern.compile(
            "(?i)(?:cumulative\\s+bonus|no\\s+claim\\s+bonus|ncb)\\s*[:\\-]?\\s*(?:of\\s+)?([^.:\\n]{2,120}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DAYCARE_PATTERN = Pattern.compile(
            "(?i)(?:day\\s*care\\s+procedures?|day\\s*care\\s+treatments?)[^.:\\n]{0,40}[:\\-]?\\s*([^.\\n]{10,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SUM_INSURED_PATTERN = Pattern.compile(
            "(?i)(?:sum\\s+insured(?:\\s+options?|\\s+range|\\s+tiers?|\\s+amounts?)?|schedule\\s+of\\s+benefits)[^.:\\n]{0,40}?[:\\-]?\\s*((?:(?:rs\\.?|inr)?\\s*\\d+(?:[,.]\\d+)?(?:\\s*(?:lakhs?|crores?|k|l|cr))?(?:\\s*(?:to|\\-|/|,)\\s*(?:rs\\.?|inr)?\\s*\\d+(?:[,.]\\d+)?(?:\\s*(?:lakhs?|crores?|k|l|cr))?)+)|up\\s+to\\s+[^.\\n]{5,100}|[^.\\n]{5,150}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DOMICILIARY_PATTERN = Pattern.compile(
            "(?i)(?:domiciliary\\s+hospitali[sz]ation)[^.:\\n]{0,40}?[:\\-]?\\s*([^.\\n]{5,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AMBULANCE_PATTERN = Pattern.compile(
            "(?i)(?:road\\s+ambulance|emergency\\s+ambulance|ambulance\\s+cover|ambulance\\s+charges)[^.:\\n]{0,40}?[:\\-]?\\s*([^.\\n]{5,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AYUSH_PATTERN = Pattern.compile(
            "(?i)(?:ayush(?:\\s+treatment|\\s+coverage)?|ayurvedic,?\\s*yoga)[^.:\\n]{0,40}?[:\\-]?\\s*([^.\\n]{5,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ORGAN_DONOR_PATTERN = Pattern.compile(
            "(?i)(?:organ\\s+donor(?:\\s+expenses)?)[^.:\\n]{0,40}?[:\\-]?\\s*([^.\\n]{5,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MODERN_TREATMENT_PATTERN = Pattern.compile(
            "(?i)(?:modern\\s+treatments?|advanced\\s+medical\\s+procedures?)[^.:\\n]{0,40}?[:\\-]?\\s*([^.\\n]{5,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MATERNITY_PATTERN = Pattern.compile(
            "(?i)(?:maternity(?:\\s+expenses|\\s+benefit|\\s+cover)?|newborn\\s+baby)[^.:\\n]{0,40}?[:\\-]?\\s*([^.\\n]{5,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MATERNITY_WAITING_PATTERN = Pattern.compile(
            "(?i)(?:maternity)[^.:\\n\\d]{0,40}(?:waiting\\s+period)[^.:\\n\\d]{0,30}[:\\-]?\\s*(\\d+)\\s*(months?|years?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DEDUCTIBLE_PATTERN = Pattern.compile(
            "(?i)(?:annual\\s+aggregate\\s+deductible|aggregate\\s+deductible|voluntary\\s+deductible|deductible)[^.:\\n]{0,40}[:\\-]?\\s*([^.\\n]{10,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONSUMABLES_PATTERN = Pattern.compile(
            "(?i)(?:consumables?(?:\\s+cover|\\s+expenses)?|non-?medical\\s+expenses?)[^.:\\n]{0,40}[:\\-]?\\s*([^.\\n]{10,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PERMANENT_EXCLUSION_PATTERN = Pattern.compile(
            "(?i)(?:permanent\\s+exclusions?|standard\\s+exclusions?|general\\s+exclusions?)[^.:\\n]{0,30}[:\\-]?\\s*([^.\\n]{10,250}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OPTIONAL_COVERS_PATTERN = Pattern.compile(
            "(?i)(?:optional\\s+covers?|optional\\s+benefits?|add-?on\\s+covers?|riders?)[^.:\\n]{0,40}[:\\-]?\\s*([^.\\n]{10,200}\\.?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern POLICY_DURATION_PATTERN = Pattern.compile(
            "(?i)(?:policy\\s+period|policy\\s+tenure|duration\\s+of\\s+policy)[^.:\\n\\d]{0,40}[:\\-]?\\s*(\\d+)\\s*(years?|months?)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Executes category-aware evidence-hardened clause extraction across all pages of a document.
     */
    public List<PolicyDocumentExtraction> extractTaxonomyClauses(
            DocumentPageExtractionService.DocumentPagesResult documentPages,
            String registryRecordId,
            String revisionId,
            String canonicalUin,
            String productCategory,
            String runId) {
        return extractTaxonomyClauses(documentPages, registryRecordId, revisionId, canonicalUin, productCategory, runId, null, null);
    }

    /**
     * Overloaded version accepting policy and insurer metadata.
     */
    public List<PolicyDocumentExtraction> extractTaxonomyClauses(
            DocumentPageExtractionService.DocumentPagesResult documentPages,
            String registryRecordId,
            String revisionId,
            String canonicalUin,
            String productCategory,
            String runId,
            String productName,
            String insurerName) {

        List<PolicyDocumentExtraction> extractions = new ArrayList<>();
        String category = productCategory != null ? productCategory.toUpperCase().trim() : "INDIVIDUAL";
        String sha256 = documentPages.getDocumentSha256();
        String shaShort = sha256.length() >= 12 ? sha256.substring(0, 12) : sha256;

        boolean isMedicalCategory = isMedicalPolicyCategory(category);

        // Multi-candidate map: fieldName -> list of candidate extractions across all pages
        Map<String, List<CandidateExtraction>> candidatesByField = new LinkedHashMap<>();

        for (DocumentPageExtractionService.PageContent page : documentPages.getPages()) {
            String text = page.getRawText();
            if (text == null || text.isBlank() || page.isScanned()) {
                continue;
            }

            int pageNum = page.getPageNumber();
            String pageHeading = extractPageHeading(text);
            String tableContext = extractTableContext(text, pageNum);
            String productVariant = extractProductVariant(text);

            // 1. HOSPITALIZATION: Room Rent
            if (isMedicalCategory) {
                Matcher m = ROOM_RENT_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    BigDecimal numeric = extractPercentageOrAmount(val);
                    String unit = val.contains("%") ? "PERCENTAGE" : "TEXT";
                    String role = classifyEvidenceRole(text, clause, pageHeading, "ROOM_RENT_LIMIT");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("ROOM_RENT_LIMIT").category("HOSPITALIZATION").typedVal(val)
                            .numericVal(numeric).unit(unit).clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 2. HOSPITALIZATION: ICU Charges
            if (isMedicalCategory) {
                Matcher m = ICU_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    BigDecimal numeric = extractPercentageOrAmount(val);
                    String unit = val.contains("%") ? "PERCENTAGE" : "TEXT";
                    String role = classifyEvidenceRole(text, clause, pageHeading, "ICU_CHARGES_LIMIT");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("ICU_CHARGES_LIMIT").category("HOSPITALIZATION").typedVal(val)
                            .numericVal(numeric).unit(unit).clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 3. COST_SHARING: Co-payment
            {
                Matcher m = COPAY_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    BigDecimal numeric = extractPercentageOrAmount(val);
                    String unit = "PERCENTAGE";
                    String role = classifyEvidenceRole(text, clause, pageHeading, "COPAY_PERCENTAGE");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("COPAY_PERCENTAGE").category("COST_SHARING").typedVal(val)
                            .numericVal(numeric).unit(unit).clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 4. WAITING_PERIOD: Pre-Existing Diseases (PED)
            if (isMedicalCategory) {
                Matcher m = PED_WAITING_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    int qty = Integer.parseInt(m.group(1));
                    String unitStr = m.group(2).toLowerCase();
                    int months = unitStr.startsWith("year") ? qty * 12 : qty;
                    String role = classifyEvidenceRole(text, clause, pageHeading, "WAITING_PERIOD_PED_MONTHS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("WAITING_PERIOD_PED_MONTHS").category("WAITING_PERIOD").typedVal(months + " Months")
                            .numericVal(BigDecimal.valueOf(months)).unit("MONTHS").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 5. WAITING_PERIOD: Initial 30 Days
            if (isMedicalCategory) {
                Matcher m = INITIAL_WAITING_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    int days = Integer.parseInt(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "WAITING_PERIOD_INITIAL_DAYS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("WAITING_PERIOD_INITIAL_DAYS").category("WAITING_PERIOD").typedVal(days + " Days")
                            .numericVal(BigDecimal.valueOf(days)).unit("DAYS").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 6. WAITING_PERIOD: Specific Diseases
            if (isMedicalCategory) {
                Matcher m = SPECIFIC_DISEASE_WAITING_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    int qty = Integer.parseInt(m.group(1));
                    String unitStr = m.group(2).toLowerCase();
                    int months = unitStr.startsWith("year") ? qty * 12 : qty;
                    String role = classifyEvidenceRole(text, clause, pageHeading, "WAITING_PERIOD_SPECIFIC_MONTHS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("WAITING_PERIOD_SPECIFIC_MONTHS").category("WAITING_PERIOD").typedVal(months + " Months")
                            .numericVal(BigDecimal.valueOf(months)).unit("MONTHS").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 7. Pre-Hospitalization Days
            if (isMedicalCategory) {
                Matcher m = PRE_HOSP_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    int days = Integer.parseInt(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "PRE_HOSPITALIZATION_DAYS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("PRE_HOSPITALIZATION_DAYS").category("HOSPITALIZATION").typedVal(days + " Days")
                            .numericVal(BigDecimal.valueOf(days)).unit("DAYS").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 8. Post-Hospitalization Days
            if (isMedicalCategory) {
                Matcher m = POST_HOSP_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    int days = Integer.parseInt(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "POST_HOSPITALIZATION_DAYS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("POST_HOSPITALIZATION_DAYS").category("HOSPITALIZATION").typedVal(days + " Days")
                            .numericVal(BigDecimal.valueOf(days)).unit("DAYS").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 9. Restoration of Sum Insured
            if (isMedicalCategory) {
                Matcher m = RESTORATION_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    BigDecimal numeric = extractPercentageOrAmount(val);
                    String role = classifyEvidenceRole(text, clause, pageHeading, "RESTORATION_BENEFIT");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("RESTORATION_BENEFIT").category("ADDITIONAL_BENEFITS").typedVal(val)
                            .numericVal(numeric).unit(val.contains("%") ? "PERCENTAGE" : "TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 10. No-Claim Bonus / Cumulative Bonus
            {
                Matcher m = NCB_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    BigDecimal numeric = extractPercentageOrAmount(val);
                    String unit = val.contains("%") ? "PERCENTAGE" : "TEXT";
                    String role = classifyEvidenceRole(text, clause, pageHeading, "NO_CLAIM_BONUS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("NO_CLAIM_BONUS").category("ADDITIONAL_BENEFITS").typedVal(val)
                            .numericVal(numeric).unit(unit).clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 11. Day Care Procedures
            if (isMedicalCategory) {
                Matcher m = DAYCARE_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "DAY_CARE_PROCEDURES");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("DAY_CARE_PROCEDURES").category("HOSPITALIZATION").typedVal(val)
                            .numericVal(null).unit("TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 12. Sum Insured Options
            {
                Matcher m = SUM_INSURED_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "SUM_INSURED_TIERS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("SUM_INSURED_TIERS").category("BASIC_INFO").typedVal(val)
                            .numericVal(null).unit("CURRENCY_INR").currency("INR").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 13. Domiciliary Hospitalization
            if (isMedicalCategory) {
                Matcher m = DOMICILIARY_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    BigDecimal numeric = extractPercentageOrAmount(val);
                    String unit = val.contains("%") || val.toLowerCase().contains("percent") ? "PERCENTAGE" : "AMOUNT";
                    String role = classifyEvidenceRole(text, clause, pageHeading, "DOMICILIARY_HOSPITALIZATION");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("DOMICILIARY_HOSPITALIZATION").category("HOSPITALIZATION").typedVal(val)
                            .numericVal(numeric).unit(unit).clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 14. Road Ambulance
            if (isMedicalCategory) {
                Matcher m = AMBULANCE_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "AMBULANCE_COVER");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("AMBULANCE_COVER").category("ADDITIONAL_BENEFITS").typedVal(val)
                            .numericVal(null).unit("TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 15. AYUSH Coverage
            if (isMedicalCategory) {
                Matcher m = AYUSH_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "AYUSH_COVER");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("AYUSH_COVER").category("ADDITIONAL_BENEFITS").typedVal(val)
                            .numericVal(null).unit("TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 16. Organ Donor Expenses
            if (isMedicalCategory) {
                Matcher m = ORGAN_DONOR_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "ORGAN_DONOR_EXPENSES");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("ORGAN_DONOR_EXPENSES").category("ADDITIONAL_BENEFITS").typedVal(val)
                            .numericVal(null).unit("TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 17. Modern Treatments
            if (isMedicalCategory) {
                Matcher m = MODERN_TREATMENT_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "MODERN_TREATMENTS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("MODERN_TREATMENTS").category("ADDITIONAL_BENEFITS").typedVal(val)
                            .numericVal(null).unit("TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 18. Maternity Benefit
            if (isMedicalCategory) {
                Matcher m = MATERNITY_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "MATERNITY_BENEFIT");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("MATERNITY_BENEFIT").category("ADDITIONAL_BENEFITS").typedVal(val)
                            .numericVal(null).unit("TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 19. Maternity Waiting Period
            if (isMedicalCategory) {
                Matcher m = MATERNITY_WAITING_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    int qty = Integer.parseInt(m.group(1));
                    String unitStr = m.group(2).toLowerCase();
                    int months = unitStr.startsWith("year") ? qty * 12 : qty;
                    String role = classifyEvidenceRole(text, clause, pageHeading, "WAITING_PERIOD_MATERNITY_MONTHS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("WAITING_PERIOD_MATERNITY_MONTHS").category("WAITING_PERIOD").typedVal(months + " Months")
                            .numericVal(BigDecimal.valueOf(months)).unit("MONTHS").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 20. Deductible
            {
                Matcher m = DEDUCTIBLE_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "DEDUCTIBLE_AMOUNT");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("DEDUCTIBLE_AMOUNT").category("COST_SHARING").typedVal(val)
                            .numericVal(null).unit("CURRENCY_INR").currency("INR").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 21. Consumables Cover
            if (isMedicalCategory) {
                Matcher m = CONSUMABLES_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "CONSUMABLES_COVER");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("CONSUMABLES_COVER").category("ADDITIONAL_BENEFITS").typedVal(val)
                            .numericVal(null).unit("TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 22. Permanent Exclusions
            {
                Matcher m = PERMANENT_EXCLUSION_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "PERMANENT_EXCLUSIONS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("PERMANENT_EXCLUSIONS").category("EXCLUSIONS").typedVal(val)
                            .numericVal(null).unit("TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 23. Optional Covers & Riders
            {
                Matcher m = OPTIONAL_COVERS_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    String val = cleanVerbatimClause(m.group(1));
                    String role = classifyEvidenceRole(text, clause, pageHeading, "OPTIONAL_COVERS_RIDERS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("OPTIONAL_COVERS_RIDERS").category("OPTIONAL_BENEFITS").typedVal(val)
                            .numericVal(null).unit("TEXT").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }

            // 24. Policy Duration
            {
                Matcher m = POLICY_DURATION_PATTERN.matcher(text);
                if (m.find()) {
                    String clause = cleanVerbatimClause(m.group(0));
                    int qty = Integer.parseInt(m.group(1));
                    String unitStr = m.group(2).toLowerCase();
                    int years = unitStr.startsWith("month") ? Math.max(1, qty / 12) : qty;
                    String role = classifyEvidenceRole(text, clause, pageHeading, "POLICY_DURATION_YEARS");
                    String surrounding = extractSurroundingContext(text, clause);

                    recordCandidate(candidatesByField, CandidateExtraction.builder()
                            .fieldName("POLICY_DURATION_YEARS").category("BASIC_INFO").typedVal(years + " Years")
                            .numericVal(BigDecimal.valueOf(years)).unit("YEARS").clause(clause).pageNum(pageNum)
                            .heading(pageHeading).evidenceRole(role).surroundingContext(surrounding)
                            .tableContext(tableContext).productVariant(productVariant).build());
                }
            }
        }

        // --- Adjudicate Candidates, Evidence Roles & Conflict Detection ---
        for (Map.Entry<String, List<CandidateExtraction>> entry : candidatesByField.entrySet()) {
            String fieldName = entry.getKey();
            List<CandidateExtraction> candidates = entry.getValue();
            if (candidates == null || candidates.isEmpty()) continue;

            // Separate operative candidates from definitions and illustrations
            List<CandidateExtraction> operativeCandidates = new ArrayList<>();
            List<CandidateExtraction> nonOperativeCandidates = new ArrayList<>();

            for (CandidateExtraction c : candidates) {
                if ("DEFINITION".equals(c.getEvidenceRole()) || "EXAMPLE".equals(c.getEvidenceRole()) || "GENERAL_INFORMATION".equals(c.getEvidenceRole())) {
                    nonOperativeCandidates.add(c);
                } else {
                    operativeCandidates.add(c);
                }
            }

            CandidateExtraction primary;
            boolean conflictDetected = false;
            List<PolicyDocumentExtraction.ConflictingCandidate> conflictingList = new ArrayList<>();

            if (!operativeCandidates.isEmpty()) {
                // Sort operative candidates: prioritize POLICY_SCHEDULE and BENEFIT_LIMIT over OPERATIVE_CLAUSE
                operativeCandidates.sort((a, b) -> Integer.compare(getRolePriority(a.getEvidenceRole()), getRolePriority(b.getEvidenceRole())));
                primary = operativeCandidates.get(0);

                // Check for value conflicts among operative candidates
                for (int i = 1; i < operativeCandidates.size(); i++) {
                    CandidateExtraction other = operativeCandidates.get(i);
                    boolean valuesMatch = Objects.equals(primary.getNumericVal(), other.getNumericVal()) &&
                            (primary.getTypedVal() != null && primary.getTypedVal().equalsIgnoreCase(other.getTypedVal()));
                    if (!valuesMatch) {
                        conflictDetected = true;
                        conflictingList.add(PolicyDocumentExtraction.ConflictingCandidate.builder()
                                .candidateValue(other.getTypedVal())
                                .numericValue(other.getNumericVal())
                                .unit(other.getUnit())
                                .evidenceRole(other.getEvidenceRole())
                                .pageNumber(other.getPageNum())
                                .clauseText(other.getClause())
                                .conflictReason("Operative value mismatch between Page " + primary.getPageNum() + " and Page " + other.getPageNum())
                                .build());
                    }
                }

                // Attach non-operative candidates as reference
                for (CandidateExtraction def : nonOperativeCandidates) {
                    conflictingList.add(PolicyDocumentExtraction.ConflictingCandidate.builder()
                            .candidateValue(def.getTypedVal())
                            .numericValue(def.getNumericVal())
                            .unit(def.getUnit())
                            .evidenceRole(def.getEvidenceRole())
                            .pageNumber(def.getPageNum())
                            .clauseText(def.getClause())
                            .conflictReason("Definition or illustrative clause preserved for reference")
                            .build());
                }
            } else {
                // Only definition/non-operative candidates exist
                primary = nonOperativeCandidates.get(0);
            }

            PolicyDocumentExtraction ext = buildExtraction(
                    sha256, shaShort, registryRecordId, revisionId, canonicalUin,
                    primary.getCategory(), fieldName, primary.getTypedVal(), primary.getNumericVal(),
                    primary.getUnit(), primary.getCurrency(), primary.getClause(), primary.getPageNum(),
                    primary.getHeading(), primary.getEvidenceRole(), primary.getSurroundingContext(),
                    primary.getTableContext(), primary.getProductVariant(), productName, insurerName, runId
            );

            if (conflictDetected) {
                ext.setHasConflict(true);
                ext.setConflictingCandidates(conflictingList);
                ext.setExtractionStatus("REVIEW_REQUIRED");
                ext.setConditions("Conflict detected across pages - requires human reviewer adjudication.");
            } else if ("DEFINITION".equals(primary.getEvidenceRole())) {
                ext.setExtractionStatus("REVIEW_REQUIRED");
                ext.setConditions("Generic definition clause detected - cannot be verified as operative coverage without schedule confirmation.");
                ext.setConfidenceScore(0.65);
                if ("COPAY_PERCENTAGE".equals(fieldName)) {
                    ext.setNumericValue((Double) null);
                    ext.setUnit("DEFINITION");
                    ext.setConditions("Co-payment definition without specified percentage in policy terms; check schedule.");
                }
            } else if ("EXAMPLE".equals(primary.getEvidenceRole())) {
                ext.setExtractionStatus("REVIEW_REQUIRED");
                ext.setConditions("Illustrative example clause; not an operative contract benefit limit.");
                ext.setConfidenceScore(0.50);
            }

            // Adaptive Quality Control: Benefit description unlimited coverage guard
            if (ext.getCandidateValue() != null && ext.getNumericValue() == null) {
                String lowerVal = ext.getCandidateValue().toLowerCase();
                if ((lowerVal.contains("covered") || lowerVal.contains("reasonable") || lowerVal.contains("admissible"))
                        && !lowerVal.contains("unlimited") && !lowerVal.contains("no limit") && !lowerVal.contains("no capping")) {
                    if ("ROOM_RENT_LIMIT".equals(fieldName) || "ICU_CHARGES_LIMIT".equals(fieldName)) {
                        ext.setConditions("General benefit description without explicit numeric limit or capping statement; schedule verification required.");
                        ext.setExtractionStatus("REVIEW_REQUIRED");
                    }
                }
            }

            if (!conflictingList.isEmpty()) {
                ext.setConflictingCandidates(conflictingList);
            }

            extractions.add(ext);
        }

        // Category-aware NOT_APPLICABLE vs NOT_FOUND explicit handling
        if (!isMedicalCategory) {
            extractions.add(buildNotApplicableExtraction(sha256, shaShort, registryRecordId, revisionId, canonicalUin,
                    "HOSPITALIZATION", "ROOM_RENT_LIMIT", "Not applicable to product category: " + category, productName, insurerName, runId));
            extractions.add(buildNotApplicableExtraction(sha256, shaShort, registryRecordId, revisionId, canonicalUin,
                    "HOSPITALIZATION", "ICU_CHARGES_LIMIT", "Not applicable to product category: " + category, productName, insurerName, runId));
            extractions.add(buildNotApplicableExtraction(sha256, shaShort, registryRecordId, revisionId, canonicalUin,
                    "WAITING_PERIOD", "WAITING_PERIOD_PED_MONTHS", "Not applicable to product category: " + category, productName, insurerName, runId));
            extractions.add(buildNotApplicableExtraction(sha256, shaShort, registryRecordId, revisionId, canonicalUin,
                    "HOSPITALIZATION", "DAY_CARE_PROCEDURES", "Not applicable to product category: " + category, productName, insurerName, runId));
            extractions.add(buildNotApplicableExtraction(sha256, shaShort, registryRecordId, revisionId, canonicalUin,
                    "HOSPITALIZATION", "PRE_HOSPITALIZATION_DAYS", "Not applicable to product category: " + category, productName, insurerName, runId));
            extractions.add(buildNotApplicableExtraction(sha256, shaShort, registryRecordId, revisionId, canonicalUin,
                    "HOSPITALIZATION", "POST_HOSPITALIZATION_DAYS", "Not applicable to product category: " + category, productName, insurerName, runId));
        }

        return extractions;
    }

    private void recordCandidate(Map<String, List<CandidateExtraction>> candidatesByField, CandidateExtraction candidate) {
        candidatesByField.computeIfAbsent(candidate.getFieldName(), k -> new ArrayList<>()).add(candidate);
    }

    private int getRolePriority(String role) {
        if ("POLICY_SCHEDULE".equalsIgnoreCase(role)) return 1;
        if ("BENEFIT_LIMIT".equalsIgnoreCase(role)) return 2;
        if ("WAITING_PERIOD".equalsIgnoreCase(role)) return 3;
        if ("EXCLUSION".equalsIgnoreCase(role)) return 4;
        if ("OPERATIVE_CLAUSE".equalsIgnoreCase(role)) return 5;
        if ("CONDITION".equalsIgnoreCase(role)) return 6;
        if ("DEFINITION".equalsIgnoreCase(role)) return 10;
        if ("EXAMPLE".equalsIgnoreCase(role)) return 11;
        if ("GENERAL_INFORMATION".equalsIgnoreCase(role)) return 12;
        return 20;
    }

    /**
     * Determines whether text represents an operative clause, definition, benefit limit, schedule, or example.
     */
    public String classifyEvidenceRole(String text, String clause, String heading, String fieldName) {
        if (clause == null) return "UNKNOWN";
        String lowerClause = clause.toLowerCase();
        String lowerText = text != null ? text.toLowerCase() : "";
        String lowerHeading = heading != null ? heading.toLowerCase() : "";

        // 1. Examples & Illustrations
        if (lowerClause.contains("illustration") || lowerClause.contains("for example") || lowerClause.contains("e.g.")
                || lowerText.contains("for example") || lowerText.contains("illustration") || lowerHeading.contains("illustration")) {
            return "EXAMPLE";
        }

        // 2. Definitions
        if (lowerHeading.contains("definition") || lowerHeading.contains("meaning of words") || lowerHeading.contains("section 1: definitions")
                || lowerClause.startsWith("means ") || lowerClause.contains(" means a ") || lowerClause.contains(" means any ")
                || lowerClause.contains("is defined as") || lowerClause.contains(" refers to ")
                || lowerClause.contains("means medical treatment") || lowerClause.contains("means a cost sharing")) {
            return "DEFINITION";
        }

        // 3. Waiting Periods
        if (fieldName.startsWith("WAITING_PERIOD") || lowerClause.contains("waiting period")
                || lowerClause.contains("shall be excluded until") || lowerClause.contains("first thirty days")) {
            return "WAITING_PERIOD";
        }

        // 4. Policy Schedule / Tables
        if (lowerHeading.contains("schedule") || lowerHeading.contains("table") || lowerText.contains("policy schedule")
                || lowerText.contains("schedule of benefits") || lowerText.contains("table of benefits")
                || lowerClause.contains("schedule") || lowerClause.contains("table") || (text != null && text.contains("|"))) {
            return "POLICY_SCHEDULE";
        }

        // 5. Exclusions
        if (fieldName.contains("EXCLUSION") || (lowerHeading.contains("exclusion") && !fieldName.startsWith("ROOM") && !fieldName.startsWith("ICU") && !fieldName.startsWith("COPAY"))
                || lowerClause.contains("code- excl0") || lowerClause.contains("shall not be liable")) {
            return "EXCLUSION";
        }

        // 6. Conditions
        if (lowerHeading.contains("condition") || lowerClause.startsWith("provided that") || lowerClause.startsWith("subject to")
                || lowerClause.contains("pre-condition") || lowerClause.contains("admissible only if")) {
            return "CONDITION";
        }

        // 7. General Information
        if (lowerHeading.contains("ombudsman") || lowerHeading.contains("grievance") || lowerHeading.contains("preamble")
                || lowerHeading.contains("cancellation") || lowerHeading.contains("free look") || lowerHeading.contains("contact")) {
            return "GENERAL_INFORMATION";
        }

        // 8. Benefit Limits
        if (fieldName.equals("ROOM_RENT_LIMIT") || fieldName.equals("ICU_CHARGES_LIMIT") || fieldName.equals("COPAY_PERCENTAGE")
                || fieldName.equals("DEDUCTIBLE_AMOUNT") || fieldName.equals("RESTORATION_BENEFIT") || fieldName.equals("SUM_INSURED_TIERS")
                || lowerClause.contains("up to") || lowerClause.contains("capped at") || lowerClause.contains("limit of")
                || lowerClause.contains("maximum of") || lowerClause.contains("% of sum insured") || lowerClause.contains("sub-limit") || lowerClause.contains("sublimit")) {
            return "BENEFIT_LIMIT";
        }

        // 9. Operative Clauses
        return "OPERATIVE_CLAUSE";
    }

    private String extractPageHeading(String pageText) {
        if (pageText == null || pageText.isBlank()) return "";
        String[] lines = pageText.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isBlank() && trimmed.length() > 3 && trimmed.length() < 80) {
                if (trimmed.toUpperCase().contains("SECTION") || trimmed.toUpperCase().contains("SCHEDULE")
                        || trimmed.toUpperCase().contains("DEFINITION") || trimmed.toUpperCase().contains("BENEFIT")
                        || trimmed.toUpperCase().contains("EXCLUSION") || trimmed.toUpperCase().contains("TABLE")) {
                    return trimmed;
                }
            }
        }
        return "General Clause Terms";
    }

    private String extractSurroundingContext(String pageText, String matchText) {
        if (pageText == null || matchText == null || matchText.isBlank()) return "";
        int idx = pageText.indexOf(matchText);
        if (idx < 0) return cleanVerbatimClause(matchText);
        int start = Math.max(0, idx - 120);
        int end = Math.min(pageText.length(), idx + matchText.length() + 120);
        return cleanVerbatimClause(pageText.substring(start, end));
    }

    private String extractTableContext(String pageText, int pageNum) {
        if (pageText == null) return null;
        if (pageText.contains("|") || pageText.toLowerCase().contains("schedule of benefits")) {
            String[] lines = pageText.split("\n");
            for (String l : lines) {
                if (l.contains("|") || l.toLowerCase().contains("sum insured") || l.toLowerCase().contains("plan")) {
                    return cleanVerbatimClause(l);
                }
            }
            return "Table structure detected on page " + pageNum;
        }
        return null;
    }

    private String extractProductVariant(String pageText) {
        if (pageText == null) return null;
        Matcher m = Pattern.compile("(?i)\\b(?:plan\\s+[a-z0-9]|silver|gold|platinum|diamond|optima|premier|basic|comprehensive)\\b").matcher(pageText);
        if (m.find()) {
            return cleanVerbatimClause(m.group(0));
        }
        return null;
    }

    private boolean isMedicalPolicyCategory(String category) {
        if (category == null) return true;
        String cat = category.toUpperCase().trim();
        return !cat.equals("PERSONAL_ACCIDENT") &&
                !cat.equals("TRAVEL") &&
                !cat.equals("BENEFIT_BASED") &&
                !cat.equals("RIDER_ADDON");
    }

    private PolicyDocumentExtraction buildExtraction(
            String sha256, String shaShort, String regId, String revId, String uin,
            String category, String fieldName, String typedVal, BigDecimal numVal, String unit,
            String currency, String clause, int pageNum, String heading, String evidenceRole,
            String surroundingContext, String tableContext, String productVariant,
            String productName, String insurerName, String runId) {

        String docKey = revId != null && !revId.isBlank() ? revId : shaShort;
        String id = String.format("EXT_%s_%s_P%d", docKey, fieldName, pageNum);

        return PolicyDocumentExtraction.builder()
                .id(id)
                .registryRecordId(regId)
                .documentRevisionId(revId)
                .documentSha256(sha256)
                .canonicalPolicyUin(uin)
                .productName(productName)
                .insurerName(insurerName)
                .productVariant(productVariant)
                .taxonomyCategory(category)
                .extractedFieldName(fieldName)
                .typedFieldValue(typedVal)
                .numericValue(numVal)
                .unit(unit)
                .currency(currency)
                .originalClauseText(clause)
                .originalPageNumber(pageNum)
                .clauseNumberOrHeading(heading)
                .evidenceRole(evidenceRole != null ? evidenceRole : "UNKNOWN")
                .surroundingContext(surroundingContext)
                .tableContext(tableContext)
                .extractionMethod("PDF_TEXT")
                .extractionEngineVersion(ENGINE_VERSION)
                .confidenceScore(0.95)
                .extractionStatus("EXTRACTED")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .extractionRunId(runId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private PolicyDocumentExtraction buildNotApplicableExtraction(
            String sha256, String shaShort, String regId, String revId, String uin,
            String category, String fieldName, String reason, String productName, String insurerName, String runId) {

        String docKey = revId != null && !revId.isBlank() ? revId : shaShort;
        String id = String.format("EXT_%s_%s_NA", docKey, fieldName);

        return PolicyDocumentExtraction.builder()
                .id(id)
                .registryRecordId(regId)
                .documentRevisionId(revId)
                .documentSha256(sha256)
                .canonicalPolicyUin(uin)
                .productName(productName)
                .insurerName(insurerName)
                .taxonomyCategory(category)
                .extractedFieldName(fieldName)
                .typedFieldValue("NOT_APPLICABLE")
                .conditions(reason)
                .evidenceRole("CONDITION")
                .originalPageNumber(0)
                .clauseNumberOrHeading("Category Inapplicability Rule")
                .extractionMethod("RULE_BASED")
                .extractionEngineVersion(ENGINE_VERSION)
                .confidenceScore(1.0)
                .extractionStatus("NOT_APPLICABLE")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .extractionRunId(runId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private String cleanVerbatimClause(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ").trim();
    }

    private BigDecimal extractPercentageOrAmount(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        // 1. Percentage
        Matcher mPct = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%").matcher(trimmed);
        if (mPct.find()) {
            try {
                return new BigDecimal(mPct.group(1));
            } catch (Exception ignored) {}
        }
        // 2. Fixed amount (e.g. Rs. 5,000 or INR 5000 or ₹5,000)
        Matcher mAmt = Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d+)?)").matcher(trimmed);
        if (mAmt.find()) {
            try {
                return new BigDecimal(mAmt.group(1).replace(",", ""));
            } catch (Exception ignored) {}
        }
        // 3. Explicit Nil / Zero
        if (trimmed.equalsIgnoreCase("nil") || trimmed.equalsIgnoreCase("none") || trimmed.toLowerCase().startsWith("nil ")) {
            return BigDecimal.ZERO;
        }
        return null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateExtraction {
        private String fieldName;
        private String category;
        private String typedVal;
        private BigDecimal numericVal;
        private String unit;
        private String currency;
        private String clause;
        private int pageNum;
        private String heading;
        private String evidenceRole;
        private String surroundingContext;
        private String tableContext;
        private String productVariant;
    }
}
