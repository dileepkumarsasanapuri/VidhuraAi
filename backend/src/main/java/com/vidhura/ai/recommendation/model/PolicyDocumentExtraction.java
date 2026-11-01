package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 6.3: Structured Policy Intelligence & Document Extraction Entity.
 *
 * Represents an extracted contractual clause or attribute tied directly to its
 * source policy document, original page number, and verbatim clause text.
 *
 * Gated: Extracted attributes remain in UNVERIFIED / REVIEW_REQUIRED status until
 * authenticated reviewer sign-off occurs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "policy_document_extractions")
@CompoundIndexes({
        @CompoundIndex(name = "pde_doc_field_idx", def = "{'documentSha256': 1, 'extractedFieldName': 1}"),
        @CompoundIndex(name = "pde_reg_field_idx", def = "{'registryRecordId': 1, 'extractedFieldName': 1}"),
        @CompoundIndex(name = "pde_verif_status_idx", def = "{'verificationStatus': 1, 'extractionStatus': 1}"),
        @CompoundIndex(name = "pde_run_idx", def = "{'extractionRunId': 1}")
})
public class PolicyDocumentExtraction {

    @Id
    private String id; // Deterministic: EXT_<docSha256_short>_<fieldName>_<page>

    @Indexed
    private String registryRecordId;

    @Indexed
    private String documentRevisionId;

    @Indexed
    private String documentSha256;

    @Indexed
    private String canonicalPolicyUin;

    @Indexed
    private String taxonomyCategory; // BASIC_INFO, HOSPITALIZATION, WAITING_PERIOD, COST_SHARING, ADDITIONAL_BENEFITS, EXCLUSIONS, OPTIONAL_COVERS

    @Indexed
    private String extractedFieldName; // e.g. ROOM_RENT_LIMIT, COPAY_PERCENTAGE, WAITING_PERIOD_PED_MONTHS

    private String typedFieldValue; // String presentation value
    private BigDecimal numericValue; // Normalized decimal if applicable
    private String unit; // PERCENTAGE, CURRENCY_INR, DAYS, MONTHS, PROCEDURES_COUNT, HOURS, RATIO, TEXT
    private String currency; // INR if monetary

    private String conditions; // Specific conditions attached to clause
    private String exceptions; // Specific exclusions/exceptions attached

    private String originalClauseText; // Exact verbatim excerpt from document
    private Integer originalPageNumber; // 1-based page number
    private String clauseNumberOrHeading; // Section heading or clause number

    @Indexed
    @Builder.Default
    private String evidenceRole = "UNKNOWN"; // DEFINITION, OPERATIVE_CLAUSE, BENEFIT_LIMIT, POLICY_SCHEDULE, EXCLUSION, WAITING_PERIOD, CONDITION, EXAMPLE, GENERAL_INFORMATION, UNKNOWN

    private String productName;
    private String insurerName;
    private String productVariant;
    private String surroundingContext;
    private String tableContext;

    @Builder.Default
    private Boolean hasConflict = false;

    @Builder.Default
    private List<ConflictingCandidate> conflictingCandidates = new ArrayList<>();

    @Indexed
    @Builder.Default
    private String extractionMethod = "PDF_TEXT"; // PDF_TEXT, OCR, RULE_BASED, MODEL_ASSISTED

    @Builder.Default
    private String extractionEngineVersion = "v1.0.0-deterministic";

    private Double confidenceScore; // 0.0 to 1.0

    @Indexed
    @Builder.Default
    private String extractionStatus = "EXTRACTED"; // NOT_PROCESSED, EXTRACTED, REVIEW_REQUIRED, VERIFIED, REJECTED, NOT_FOUND, NOT_APPLICABLE, EXTRACTION_FAILED

    @Indexed
    @Builder.Default
    private String verificationStatus = "UNVERIFIED_PENDING_REVIEW"; // UNVERIFIED_PENDING_REVIEW, VERIFIED_REVIEWER, REJECTED_REVIEWER

    private String verifiedBy;
    private Instant verifiedAt;
    private String verificationNotes;

    @Builder.Default
    private List<ExtractionAuditEntry> auditTrail = new ArrayList<>();

    @Indexed
    private String extractionRunId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractionAuditEntry {
        private String action; // APPROVED, REJECTED, CORRECTION_REQUESTED, SYSTEM_EXTRACTED, CORRECTED, VALUE_UPDATED
        private String actor;
        private Instant timestamp;
        private String notes;
        private String previousValue;
        private String newValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictingCandidate {
        private String candidateValue;
        private BigDecimal numericValue;
        private String unit;
        private String evidenceRole;
        private Integer pageNumber;
        private String clauseText;
        private String conflictReason;
    }

    public String getCandidateValue() {
        return typedFieldValue;
    }

    public void setCandidateValue(String candidateValue) {
        this.typedFieldValue = candidateValue;
    }

    public String getVerbatimClauseText() {
        return originalClauseText;
    }

    public void setVerbatimClauseText(String verbatimClauseText) {
        this.originalClauseText = verbatimClauseText;
    }

    public void setNumericValue(BigDecimal val) {
        this.numericValue = val;
    }

    public void setNumericValue(Double val) {
        this.numericValue = val != null ? BigDecimal.valueOf(val) : null;
    }

    public static class PolicyDocumentExtractionBuilder {
        public PolicyDocumentExtractionBuilder candidateValue(String val) {
            this.typedFieldValue = val;
            return this;
        }

        public PolicyDocumentExtractionBuilder verbatimClauseText(String text) {
            this.originalClauseText = text;
            return this;
        }

        public PolicyDocumentExtractionBuilder numericValue(BigDecimal val) {
            this.numericValue = val;
            return this;
        }

        public PolicyDocumentExtractionBuilder numericValue(Double val) {
            this.numericValue = val != null ? BigDecimal.valueOf(val) : null;
            return this;
        }
    }
}
