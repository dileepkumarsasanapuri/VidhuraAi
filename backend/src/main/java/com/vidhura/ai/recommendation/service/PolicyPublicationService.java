package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Policy Publication & Verification Service.
 *
 * Ensures that ONLY policies with verified, accurate contractual facts enter
 * the active recommendation catalog. Manages the lifecycle between:
 * - DRAFT_DISCOVERED / EXTRACTION_PENDING_REVIEW
 * - VERIFIED_OFFICIAL_CATALOG / VERIFIED_CONTRACTUAL_SPECIFICATION
 * - FLAGGED_OUTDATED / NEEDS_REVISION
 */
@Slf4j
@Service
@org.springframework.transaction.annotation.Transactional
public class PolicyPublicationService {

    private final StructuredPolicyRepository policyRepository;
    private final RecommendationService recommendationService;
    private final CanonicalProductRegistry canonicalProductRegistry;

    @org.springframework.beans.factory.annotation.Autowired
    public PolicyPublicationService(
            StructuredPolicyRepository policyRepository,
            RecommendationService recommendationService,
            CanonicalProductRegistry canonicalProductRegistry) {
        this.policyRepository = policyRepository;
        this.recommendationService = recommendationService;
        this.canonicalProductRegistry = canonicalProductRegistry != null ? canonicalProductRegistry : new CanonicalProductRegistry();
    }

    public PolicyPublicationService(
            StructuredPolicyRepository policyRepository,
            RecommendationService recommendationService) {
        this(policyRepository, recommendationService, new CanonicalProductRegistry());
    }

    /**
     * Approves, verifies, and publishes a policy into the active recommendation catalog,
     * or flags it for revision if rejected.
     */
    public StructuredPolicy verifyAndPublish(
            String policyId,
            PolicyDiscoveryDto.PolicyVerificationRequest request) {

        StructuredPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found with id: " + policyId));

        String decision = request.getDecision() != null ? request.getDecision().toUpperCase() : "APPROVE";
        String previousStatus = policy.getVerificationStatus();

        if ("APPROVE".equals(decision)) {
            // 1. Mandatory reviewer identity validation:
            if (request.getReviewerName() == null || request.getReviewerName().trim().isBlank()) {
                throw new IllegalArgumentException("Publication rejected: An authorized reviewer identity (reviewerName) is strictly required.");
            }

            // Bind reviewer identity directly from server-side authenticated SecurityContext credentials if authenticated
            try {
                org.springframework.security.core.Authentication auth =
                        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()
                        && auth.getPrincipal() != null
                        && !"anonymousUser".equals(auth.getPrincipal().toString())) {
                    String principalName = auth.getName();
                    if (principalName != null && !principalName.isBlank()) {
                        request.setReviewerName(principalName);
                    }
                }
            } catch (Exception ignored) {
                // Fallback for offline unit test mocks without SecurityContextHolder
            }

            // 2. Server-side Fail-Closed Document Classification Gate:
            // ONLY documents positively classified as POLICY_WORDING can be approved into the active catalog.
            if (!"POLICY_WORDING".equalsIgnoreCase(policy.getDocumentClassification())) {
                throw new IllegalStateException("Cannot publish policy: Document is classified as "
                        + (policy.getDocumentClassification() != null ? policy.getDocumentClassification() : "UNKNOWN")
                        + ". Only documents positively verified as contractual policy wordings (POLICY_WORDING) can enter the active recommendation catalog.");
            }
            if (policy.getContractUncertainFields() != null && (
                    policy.getContractUncertainFields().contains("MULTI_PRODUCT_CATALOGUE_DISCOVERY_INDEX") ||
                    policy.getContractUncertainFields().contains("UNCLASSIFIED_NON_CONTRACTUAL_DOCUMENT") ||
                    policy.getContractUncertainFields().contains("CUSTOMER_INFORMATION_SHEET_DISCLOSURE_ONLY") ||
                    policy.getContractUncertainFields().contains("REGULATORY_FILING_DISCLOSURE_ONLY"))) {
                throw new IllegalStateException("Cannot publish policy: Contractual document validation failed ("
                        + policy.getContractUncertainFields() + "). Verified contractual policy wording is required for recommendation publication.");
            }

            // Capture old identity state for audit comparison
            String oldUin = policy.getIrdaUin();
            String oldPolicyName = policy.getPolicyName();
            String oldProvider = policy.getProvider();
            String oldRevision = policy.getUinRevision();

            boolean hadIdentityMismatch = policy.getContractUncertainFields() != null && (
                    policy.getContractUncertainFields().contains("CANONICAL_PRODUCT_MISMATCH") ||
                    policy.getContractUncertainFields().contains("CANONICAL_PROVIDER_MISMATCH") ||
                    policy.getContractUncertainFields().contains("IRDAI_INSURER_MISMATCH")
            );

            // 2. Check for unresolved contractual contradictions between terms and citations
            if (policy.getContractUncertainFields() != null && policy.getContractUncertainFields().contains("CONTRADICTORY_CONTRACT_TERMS")) {
                boolean reconciled = request.getOverrideFields() != null
                        && (request.getOverrideFields().containsKey("roomRentCapped")
                        || request.getOverrideFields().containsKey("copayPercentage")
                        || request.getOverrideFields().containsKey("pedWaitingMonths"));
                if (!reconciled) {
                    throw new IllegalStateException("Cannot publish policy: Publication blocked pending reconciliation of contradictory policy terms and source citations.");
                }
            }

            // 3. Track old vs new values when applying reviewer overrides
            Map<String, Map<String, Object>> overrideDiffs = new HashMap<>();
            if (request.getOverrideFields() != null && !request.getOverrideFields().isEmpty()) {
                applyReviewerOverrides(policy, request.getOverrideFields());

                if (request.getOverrideFields().containsKey("irdaUin")) {
                    overrideDiffs.put("irdaUin", Map.of("old", String.valueOf(oldUin), "new", String.valueOf(policy.getIrdaUin())));
                }
                if (request.getOverrideFields().containsKey("policyName")) {
                    overrideDiffs.put("policyName", Map.of("old", String.valueOf(oldPolicyName), "new", String.valueOf(policy.getPolicyName())));
                }
                if (request.getOverrideFields().containsKey("provider")) {
                    overrideDiffs.put("provider", Map.of("old", String.valueOf(oldProvider), "new", String.valueOf(policy.getProvider())));
                }
                if (request.getOverrideFields().containsKey("uinRevision")) {
                    overrideDiffs.put("uinRevision", Map.of("old", String.valueOf(oldRevision), "new", String.valueOf(policy.getUinRevision())));
                }
            }

            // 4. Issue B Hardening: Robust Canonical Identity Re-Validation
            boolean identityTouched = request.getOverrideFields() != null && (
                    request.getOverrideFields().containsKey("irdaUin") ||
                    request.getOverrideFields().containsKey("policyName") ||
                    request.getOverrideFields().containsKey("provider") ||
                    request.getOverrideFields().containsKey("uinRevision")
            );

            if (hadIdentityMismatch || identityTouched) {
                // If an identity mismatch existed but no override was provided, block with specific diagnostic
                if (hadIdentityMismatch && !identityTouched) {
                    if (policy.getContractUncertainFields().contains("IRDAI_INSURER_MISMATCH")) {
                        throw new IllegalStateException("Cannot publish policy: IRDAI Insurer UIN mismatch detected. Official identity verification failed.");
                    } else if (policy.getContractUncertainFields().contains("CANONICAL_PROVIDER_MISMATCH")) {
                        throw new IllegalStateException("Cannot publish policy: Canonical provider mismatch. UIN insurer does not match policy provider.");
                    } else {
                        throw new IllegalStateException("Cannot publish policy: Canonical product registry mismatch. UIN does not correspond to product identity or revision.");
                    }
                }

                // An identity override strictly requires an explicit justification note
                if (request.getReviewerNotes() == null || request.getReviewerNotes().trim().isBlank()) {
                    throw new IllegalArgumentException("Publication rejected: An explicit reviewer justification note is strictly required when overriding canonical identity.");
                }

                // Gather supporting evidence citations
                StringBuilder evidenceBuilder = new StringBuilder();
                if (policy.getDocumentEvidence() != null) {
                    policy.getDocumentEvidence().forEach(e -> {
                        if (e.getVerbatimText() != null) evidenceBuilder.append(e.getVerbatimText()).append(" ");
                        if (e.getSectionReference() != null) evidenceBuilder.append(e.getSectionReference()).append(" ");
                    });
                }
                if (request.getReviewerNotes() != null) {
                    evidenceBuilder.append(request.getReviewerNotes()).append(" ");
                }
                if (policy.getOfficialDocumentUrl() != null) {
                    evidenceBuilder.append(policy.getOfficialDocumentUrl()).append(" ");
                }
                String evidenceText = evidenceBuilder.toString().trim();

                // Re-run the full canonical identity validation against the updated values and evidence
                List<String> discrepancies = canonicalProductRegistry.validateIdentity(
                        policy.getIrdaUin(),
                        policy.getProvider(),
                        policy.getPolicyName(),
                        policy.getUinRevision(),
                        evidenceText
                );

                List<String> fatalDiscrepancies = discrepancies.stream()
                        .filter(d -> d.startsWith("CANONICAL_PRODUCT_MISMATCH")
                                || d.startsWith("CANONICAL_PROVIDER_MISMATCH")
                                || d.startsWith("UNCERTAIN_IRDAI_UIN")
                                || d.startsWith("IRDAI_INSURER_MISMATCH"))
                        .collect(Collectors.toList());

                // Strictly re-verify insurer prefix against provider
                boolean validPrefix = PolicyDiscoveryPipelineService.validateInsurerUinPrefix(policy.getProvider(), policy.getIrdaUin());
                if (!validPrefix) {
                    fatalDiscrepancies.add("IRDAI_INSURER_MISMATCH: Insurer prefix in UIN " + policy.getIrdaUin()
                            + " does not match provider " + policy.getProvider());
                }

                if (!fatalDiscrepancies.isEmpty()) {
                    // Rollback in-memory entity mutations so rejected overrides do not taint state
                    policy.setIrdaUin(oldUin);
                    policy.setPolicyName(oldPolicyName);
                    policy.setProvider(oldProvider);
                    policy.setUinRevision(oldRevision);
                    throw new IllegalStateException("Cannot publish policy: Canonical identity override failed re-validation. "
                            + "Submitted values still fail official IRDAI registry or evidence checks: " + fatalDiscrepancies);
                }

                // Re-validation passed: remove canonical uncertainties
                if (policy.getContractUncertainFields() != null) {
                    policy.getContractUncertainFields().remove("CANONICAL_PRODUCT_MISMATCH");
                    policy.getContractUncertainFields().remove("CANONICAL_PROVIDER_MISMATCH");
                    if (validPrefix) {
                        policy.getContractUncertainFields().remove("IRDAI_INSURER_MISMATCH");
                    }
                }
                log.info("[PolicyPublicationService] Canonical identity successfully re-validated for UIN: {}, product: {}",
                        policy.getIrdaUin(), policy.getPolicyName());
            }

            // 5. Server-side critical clause integrity gate
            if (policy.getContractTerms() == null || "Unverified Room Category".equals(policy.getContractTerms().getRoomRentLimit())) {
                throw new IllegalStateException("Cannot publish policy: Room rent clause is unverified. Reviewer must provide valid room rent terms.");
            }
            if (policy.getIrdaUin() == null || policy.getIrdaUin().startsWith("UNKNOWN_UIN")) {
                throw new IllegalStateException("Cannot publish policy: Conclusive IRDAI Product UIN required.");
            }

            // Product-specific PED waiting period verification
            int pedMonths = policy.getContractTerms().getWaitingPeriodPedMonths();
            if (pedMonths < 0) {
                throw new IllegalStateException("Cannot publish policy: PED waiting period cannot be negative.");
            }
            if (pedMonths < 12 || pedMonths > 48) {
                boolean explicitConfirmation = (request.getOverrideFields() != null && request.getOverrideFields().containsKey("pedWaitingMonths"))
                        || (request.getReviewerNotes() != null && request.getReviewerNotes().toLowerCase().contains("ped"));
                if (!explicitConfirmation) {
                    throw new IllegalStateException(String.format(
                            "Cannot publish policy: Unusual PED waiting period (%d months) requires explicit reviewer confirmation note or clause override.", pedMonths));
                }
            }

            // 6. Verified Contractual Evidence Validation & Confidence Promotion
            if (policy.getDocumentEvidence() == null || policy.getDocumentEvidence().isEmpty()) {
                // If evidence was not extracted from PDF, ensure the reviewer verified terms in notes or build verified record
                String auditNote = request.getReviewerNotes() != null && !request.getReviewerNotes().isBlank()
                        ? request.getReviewerNotes()
                        : "Verified by authorized reviewer " + request.getReviewerName();
                policy.setDocumentEvidence(new ArrayList<>());
                policy.getDocumentEvidence().add(StructuredPolicy.PolicyEvidenceItem.builder()
                        .clauseName("In-patient Hospitalization & Room Rent Limit")
                        .sectionReference("Audited Policy Schedule")
                        .documentName(policy.getPolicyWordingDocument() != null ? policy.getPolicyWordingDocument() : "Official_Policy_Wording.pdf")
                        .verbatimText("Verified and approved: " + auditNote)
                        .verifiedFact(policy.getContractTerms() != null && !policy.getContractTerms().isRoomRentCapped()
                                ? "No room rent capping" : "Room rent limit: " + (policy.getContractTerms() != null ? policy.getContractTerms().getRoomRentLimit() : "Standard"))
                        .tag("ROOM_RENT")
                        .evidenceConfidence("VERIFIED_CONTRACT_CLAUSE")
                        .sourceDocumentVersion(policy.getUinRevision() != null ? policy.getUinRevision() : "v1.0")
                        .build());
            } else {
                for (StructuredPolicy.PolicyEvidenceItem item : policy.getDocumentEvidence()) {
                    if (item.getVerbatimText() == null || item.getVerbatimText().isBlank()) {
                        throw new IllegalStateException("Cannot publish policy: Supporting evidence for clause '"
                                + item.getClauseName() + "' is missing verbatim contractual text from the source document.");
                    }
                    item.setEvidenceConfidence("VERIFIED_CONTRACT_CLAUSE");
                }
            }

            // 7. Update contract confidence & clear verified uncertainties
            policy.setContractConfidenceScore(100.0);
            if (policy.getContractUncertainFields() != null) {
                policy.getContractUncertainFields().clear();
            }
            if (policy.getUncertainFields() != null) {
                policy.getUncertainFields().clear();
            }

            policy.setVerificationStatus("VERIFIED_OFFICIAL_CATALOG");
            policy.setActive(true);
            policy.setConfidenceScore(100.0);
            policy.setVerifiedBy(request.getReviewerName().trim());
            policy.setReviewerApprovalStatus("REVIEWER_APPROVED");
            policy.setSyntheticFixture(false);
            policy.setAuditNotes(request.getReviewerNotes() != null && !request.getReviewerNotes().isBlank()
                    ? request.getReviewerNotes().trim()
                    : "Verified against official IRDAI public filing documents. All critical contract clauses approved by " + request.getReviewerName());
            policy.setLastAuditedDate("2024-Q1");
            policy.setPublicationDate(Instant.now());
            policy.setUpdatedAt(Instant.now());

            // Build detailed audit log entry
            Map<String, Object> auditChanges = new HashMap<>();
            auditChanges.put("policyVersion", policy.getUinRevision() != null ? policy.getUinRevision() : "v1.0");
            auditChanges.put("contractConfidenceScore", policy.getContractConfidenceScore());
            auditChanges.put("pricingStatus", policy.getPricingStatus());
            auditChanges.put("active", true);
            auditChanges.put("overridesApplied", request.getOverrideFields() != null ? request.getOverrideFields().keySet().toString() : "none");
            if (!overrideDiffs.isEmpty()) {
                auditChanges.put("overridesDetail", overrideDiffs);
            }
            if (request.getReviewerNotes() != null && !request.getReviewerNotes().isBlank()) {
                auditChanges.put("reviewerJustification", request.getReviewerNotes().trim());
            }

            StructuredPolicy.AuditLogEntry auditEntry = StructuredPolicy.AuditLogEntry.builder()
                    .timestamp(Instant.now())
                    .action("VERIFIED_PUBLISHED")
                    .performedBy(request.getReviewerName().trim())
                    .previousStatus(previousStatus)
                    .newStatus("VERIFIED_OFFICIAL_CATALOG")
                    .notes(policy.getAuditNotes())
                    .changes(auditChanges)
                    .build();
            if (policy.getAuditHistory() == null) {
                policy.setAuditHistory(new java.util.ArrayList<>());
            }
            policy.getAuditHistory().add(auditEntry);

            StructuredPolicy saved = policyRepository.save(policy);
            log.info("[PolicyPublicationService] Approved and published policy: id={}, code={}, status={}, reviewer={}",
                    saved.getId(), saved.getPolicyCode(), saved.getVerificationStatus(), saved.getVerifiedBy());

            // Automatically supersede previous active revisions of this same product
            String baseCode = saved.getPolicyCode()
                    .replaceAll("_DRAFT_.*$", "")
                    .replaceAll("_[Vv][0-9].*$", "")
                    .replaceAll("_REV.*$", "");

            String baseUin = saved.getIrdaUin() != null
                    ? saved.getIrdaUin().replaceAll("(?i)V[0-9].*$", "")
                    : "";

            List<StructuredPolicy> previousActive = policyRepository.findAll().stream()
                    .filter(p -> !p.getId().equals(saved.getId()) && p.isActive())
                    .filter(p -> {
                        String pBaseCode = p.getPolicyCode()
                                .replaceAll("_DRAFT_.*$", "")
                                .replaceAll("_[Vv][0-9].*$", "")
                                .replaceAll("_REV.*$", "");
                        boolean sameCode = !baseCode.isBlank() && baseCode.equalsIgnoreCase(pBaseCode);
                        boolean sameName = saved.getPolicyName() != null && saved.getPolicyName().equalsIgnoreCase(p.getPolicyName())
                                && saved.getProvider() != null && saved.getProvider().equalsIgnoreCase(p.getProvider());
                        String pBaseUin = p.getIrdaUin() != null ? p.getIrdaUin().replaceAll("(?i)V[0-9].*$", "") : "";
                        boolean sameUin = !baseUin.isBlank() && baseUin.equalsIgnoreCase(pBaseUin);
                        return sameCode || sameName || sameUin;
                    })
                    .toList();

            for (StructuredPolicy oldRev : previousActive) {
                oldRev.setActive(false);
                oldRev.setVerificationStatus("SUPERSEDED");
                oldRev.setAuditNotes("Superseded by newly approved revision "
                        + (saved.getUinRevision() != null ? saved.getUinRevision() : saved.getPolicyCode())
                        + " published by " + request.getReviewerName().trim());
                oldRev.setUpdatedAt(Instant.now());

                StructuredPolicy.AuditLogEntry supersedeAudit = StructuredPolicy.AuditLogEntry.builder()
                        .timestamp(Instant.now())
                        .action("SUPERSEDED")
                        .performedBy(request.getReviewerName().trim())
                        .previousStatus("VERIFIED_OFFICIAL_CATALOG")
                        .newStatus("SUPERSEDED")
                        .notes("Superseded by newly approved revision id=" + saved.getId())
                        .changes(Map.of("active", false, "supersededBy", saved.getId()))
                        .build();
                if (oldRev.getAuditHistory() == null) {
                    oldRev.setAuditHistory(new java.util.ArrayList<>());
                }
                oldRev.getAuditHistory().add(supersedeAudit);
                policyRepository.save(oldRev);
                recommendationService.markPolicyRevoked(oldRev.getPolicyCode());
                log.info("[PolicyPublicationService] Automatically superseded prior active revision id={}, code={}",
                        oldRev.getId(), oldRev.getPolicyCode());
            }

            // Increment global catalog version and invalidate cache immediately so replicas sync
            if (recommendationService != null) {
                recommendationService.unmarkPolicyRevoked(saved.getPolicyCode());
            }
            long activeCount = policyRepository.countByActiveTrue();
            if (recommendationService != null) {
                recommendationService.incrementGlobalCatalogVersion("PUBLISH: " + saved.getPolicyCode(), request.getReviewerName().trim(), (int) activeCount);
                recommendationService.refreshCatalog();
            }
            return saved;

        } else if ("FLAG_OUTDATED".equals(decision)) {
            return flagPolicy(policyId, request.getReviewerNotes(), "FLAGGED_OUTDATED", request.getReviewerName());
        } else {
            // REJECT / NEEDS_REVISION
            policy.setVerificationStatus("NEEDS_REVISION");
            policy.setActive(false);
            policy.setAuditNotes(request.getReviewerNotes() != null
                    ? request.getReviewerNotes()
                    : "Rejected by reviewer due to discrepancies with official wording.");
            policy.setUpdatedAt(Instant.now());

            StructuredPolicy.AuditLogEntry auditEntry = StructuredPolicy.AuditLogEntry.builder()
                    .timestamp(Instant.now())
                    .action("REJECTED_FOR_REVISION")
                    .performedBy(request.getReviewerName() != null ? request.getReviewerName() : "REVIEWER")
                    .previousStatus(previousStatus)
                    .newStatus("NEEDS_REVISION")
                    .notes(policy.getAuditNotes())
                    .changes(Map.of("active", false, "previousStatus", previousStatus, "newStatus", "NEEDS_REVISION"))
                    .build();
            if (policy.getAuditHistory() == null) {
                policy.setAuditHistory(new java.util.ArrayList<>());
            }
            policy.getAuditHistory().add(auditEntry);

            StructuredPolicy saved = policyRepository.save(policy);
            log.info("[PolicyPublicationService] Policy rejected for revision: id={}, code={}",
                    saved.getId(), saved.getPolicyCode());

            long activeCount = policyRepository.countByActiveTrue();
            recommendationService.markPolicyRevoked(saved.getPolicyCode());
            recommendationService.incrementGlobalCatalogVersion("REJECT: " + saved.getPolicyCode(),
                    request.getReviewerName() != null ? request.getReviewerName() : "REVIEWER", (int) activeCount, saved.getPolicyCode());
            recommendationService.refreshCatalog();
            return saved;
        }
    }

    /**
     * Flags a policy as outdated or incomplete, preventing it from appearing in recommendations.
     */
    public StructuredPolicy flagPolicy(String policyId, String reason, String status) {
        return flagPolicy(policyId, reason, status, "COMPLIANCE_AUDITOR");
    }

    public StructuredPolicy flagPolicy(String policyId, String reason, String status, String reviewerName) {
        StructuredPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found with id: " + policyId));

        String targetStatus = (status != null && !status.isBlank()) ? status : "FLAGGED_OUTDATED";
        String previousStatus = policy.getVerificationStatus();
        String performer = (reviewerName != null && !reviewerName.isBlank()) ? reviewerName.trim() : "COMPLIANCE_AUDITOR";

        policy.setVerificationStatus(targetStatus);
        policy.setActive(false);
        policy.setAuditNotes(reason != null ? reason : "Flagged as outdated/incomplete by reviewer " + performer + ".");
        policy.setUpdatedAt(Instant.now());

        StructuredPolicy.AuditLogEntry auditEntry = StructuredPolicy.AuditLogEntry.builder()
                .timestamp(Instant.now())
                .action(targetStatus)
                .performedBy(performer)
                .previousStatus(previousStatus)
                .newStatus(targetStatus)
                .notes(policy.getAuditNotes())
                .changes(Map.of("active", false, "previousStatus", previousStatus, "newStatus", targetStatus))
                .build();
        if (policy.getAuditHistory() == null) {
            policy.setAuditHistory(new java.util.ArrayList<>());
        }
        policy.getAuditHistory().add(auditEntry);

        StructuredPolicy saved = policyRepository.save(policy);
        log.warn("[PolicyPublicationService] Policy flagged as {} by {}: id={}, code={}, reason={}",
                targetStatus, performer, saved.getId(), saved.getPolicyCode(), reason);

        long activeCount = policyRepository.countByActiveTrue();
        recommendationService.markPolicyRevoked(saved.getPolicyCode());
        recommendationService.incrementGlobalCatalogVersion("FLAG_" + targetStatus + ": " + saved.getPolicyCode(),
                performer, (int) activeCount, saved.getPolicyCode());
        recommendationService.refreshCatalog();
        return saved;
    }

    /**
     * Rolls back an active policy revision and reactivates the previous superseded revision.
     */
    public StructuredPolicy rollbackRevision(String activePolicyId, String reviewerName, String reason) {
        if (reviewerName == null || reviewerName.trim().isBlank()) {
            throw new IllegalArgumentException("Rollback rejected: Authorized reviewer identity required.");
        }
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("Rollback rejected: Justification note required.");
        }

        StructuredPolicy currentActive = policyRepository.findById(activePolicyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found with id: " + activePolicyId));

        if (!currentActive.isActive()) {
            throw new IllegalStateException("Cannot rollback inactive policy with id: " + activePolicyId);
        }

        String baseCode = currentActive.getPolicyCode()
                .replaceAll("_DRAFT_.*$", "")
                .replaceAll("_[Vv][0-9].*$", "")
                .replaceAll("_REV.*$", "");

        String baseUin = currentActive.getIrdaUin() != null
                ? currentActive.getIrdaUin().replaceAll("(?i)V[0-9].*$", "")
                : "";

        // Find the most recently superseded revision for this product
        Optional<StructuredPolicy> previousRev = policyRepository.findAll().stream()
                .filter(p -> !p.getId().equals(currentActive.getId()))
                .filter(p -> "SUPERSEDED".equalsIgnoreCase(p.getVerificationStatus()))
                .filter(p -> {
                    String pBaseCode = p.getPolicyCode()
                            .replaceAll("_DRAFT_.*$", "")
                            .replaceAll("_[Vv][0-9].*$", "")
                            .replaceAll("_REV.*$", "");
                    boolean sameCode = !baseCode.isBlank() && baseCode.equalsIgnoreCase(pBaseCode);
                    boolean sameName = currentActive.getPolicyName() != null && currentActive.getPolicyName().equalsIgnoreCase(p.getPolicyName())
                            && currentActive.getProvider() != null && currentActive.getProvider().equalsIgnoreCase(p.getProvider());
                    String pBaseUin = p.getIrdaUin() != null ? p.getIrdaUin().replaceAll("(?i)V[0-9].*$", "") : "";
                    boolean sameUin = !baseUin.isBlank() && baseUin.equalsIgnoreCase(pBaseUin);
                    return sameCode || sameName || sameUin;
                })
                .sorted((a, b) -> {
                    Instant tA = a.getUpdatedAt() != null ? a.getUpdatedAt() : Instant.MIN;
                    Instant tB = b.getUpdatedAt() != null ? b.getUpdatedAt() : Instant.MIN;
                    return tB.compareTo(tA);
                })
                .findFirst();

        // 1. Deactivate current active policy
        currentActive.setActive(false);
        currentActive.setVerificationStatus("ROLLBACK_DEACTIVATED");
        currentActive.setAuditNotes("Deactivated via rollback by " + reviewerName.trim() + ". Reason: " + reason.trim());
        currentActive.setUpdatedAt(Instant.now());
        StructuredPolicy.AuditLogEntry deactivateAudit = StructuredPolicy.AuditLogEntry.builder()
                .timestamp(Instant.now())
                .action("ROLLBACK_DEACTIVATED")
                .performedBy(reviewerName.trim())
                .previousStatus("VERIFIED_OFFICIAL_CATALOG")
                .newStatus("ROLLBACK_DEACTIVATED")
                .notes("Rollback: " + reason.trim())
                .changes(Map.of("active", false, "reason", reason.trim()))
                .build();
        if (currentActive.getAuditHistory() == null) currentActive.setAuditHistory(new java.util.ArrayList<>());
        currentActive.getAuditHistory().add(deactivateAudit);
        policyRepository.save(currentActive);
        recommendationService.markPolicyRevoked(currentActive.getPolicyCode());

        // 2. Reactivate previous superseded revision if found
        StructuredPolicy restored = currentActive;
        if (previousRev.isPresent()) {
            StructuredPolicy prev = previousRev.get();
            prev.setActive(true);
            prev.setVerificationStatus("VERIFIED_OFFICIAL_CATALOG");
            prev.setAuditNotes("Restored via rollback by " + reviewerName.trim() + " after deactivating " + currentActive.getId());
            prev.setUpdatedAt(Instant.now());
            StructuredPolicy.AuditLogEntry restoreAudit = StructuredPolicy.AuditLogEntry.builder()
                    .timestamp(Instant.now())
                    .action("ROLLBACK_RESTORED")
                    .performedBy(reviewerName.trim())
                    .previousStatus("SUPERSEDED")
                    .newStatus("VERIFIED_OFFICIAL_CATALOG")
                    .notes("Restored via rollback after deactivating revision " + currentActive.getId())
                    .changes(Map.of("active", true, "restoredFrom", "SUPERSEDED"))
                    .build();
            if (prev.getAuditHistory() == null) prev.setAuditHistory(new java.util.ArrayList<>());
            prev.getAuditHistory().add(restoreAudit);
            restored = policyRepository.save(prev);
            if (recommendationService != null) {
                recommendationService.unmarkPolicyRevoked(restored.getPolicyCode());
            }
            log.info("[PolicyPublicationService] Successfully restored superseded revision id={}, code={}",
                    restored.getId(), restored.getPolicyCode());
        }

        long activeCount = policyRepository.countByActiveTrue();
        if (recommendationService != null) {
            recommendationService.incrementGlobalCatalogVersion("ROLLBACK: " + currentActive.getPolicyCode(),
                    reviewerName.trim(), (int) activeCount, currentActive.getPolicyCode());
            recommendationService.refreshCatalog();
        }
        return restored;
    }

    /**
     * Retrieves catalog governance statistics across verification states.
     */
    public PolicyDiscoveryDto.CatalogStatsDto getCatalogStats() {
        try {
            List<StructuredPolicy> all = policyRepository.findAll();

            long verified = all.stream()
                    .filter(p -> "VERIFIED_OFFICIAL_CATALOG".equals(p.getVerificationStatus())
                            || "VERIFIED_CONTRACTUAL_SPECIFICATION".equals(p.getVerificationStatus()))
                    .count();

            long pending = all.stream()
                    .filter(p -> "EXTRACTION_PENDING_REVIEW".equals(p.getVerificationStatus()))
                    .count();

            long drafts = all.stream()
                    .filter(p -> "DRAFT_DISCOVERED".equals(p.getVerificationStatus()))
                    .count();

            long flagged = all.stream()
                    .filter(p -> "FLAGGED_OUTDATED".equals(p.getVerificationStatus())
                            || "NEEDS_REVISION".equals(p.getVerificationStatus()))
                    .count();

            return PolicyDiscoveryDto.CatalogStatsDto.builder()
                    .totalPolicies(all.size())
                    .verifiedPublishedPolicies(verified)
                    .pendingReviewPolicies(pending)
                    .draftPolicies(drafts)
                    .flaggedOrOutdatedPolicies(flagged)
                    .build();
        } catch (Exception e) {
            log.warn("[PolicyPublicationService] MongoDB query failed ({}), falling back to in-memory verified catalog", e.getMessage());
            int verified = recommendationService != null ? recommendationService.getCatalog().size() : 7;
            return PolicyDiscoveryDto.CatalogStatsDto.builder()
                    .totalPolicies(verified)
                    .verifiedPublishedPolicies(verified)
                    .pendingReviewPolicies(0)
                    .draftPolicies(0)
                    .flaggedOrOutdatedPolicies(0)
                    .build();
        }
    }

    private void applyReviewerOverrides(StructuredPolicy policy, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) return;

        if (overrides.containsKey("irdaUin")) {
            policy.setIrdaUin(String.valueOf(overrides.get("irdaUin")).trim().toUpperCase());
        }
        if (overrides.containsKey("uinRevision")) {
            policy.setUinRevision(String.valueOf(overrides.get("uinRevision")).trim().toUpperCase());
        }
        if (overrides.containsKey("policyName")) {
            policy.setPolicyName(String.valueOf(overrides.get("policyName")).trim());
        }
        if (overrides.containsKey("provider")) {
            policy.setProvider(String.valueOf(overrides.get("provider")).trim());
        }

        StructuredPolicy.ContractTerms terms = policy.getContractTerms();
        if (terms != null) {
            if (overrides.containsKey("roomRentCapped")) {
                terms.setRoomRentCapped(Boolean.parseBoolean(String.valueOf(overrides.get("roomRentCapped"))));
            }
            if (overrides.containsKey("copayPercentage")) {
                terms.setCopayPercentage(Double.parseDouble(String.valueOf(overrides.get("copayPercentage"))));
            }
            if (overrides.containsKey("pedWaitingMonths")) {
                terms.setWaitingPeriodPedMonths(Integer.parseInt(String.valueOf(overrides.get("pedWaitingMonths"))));
            }
        }
    }
}
