package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.*;
import com.vidhura.ai.recommendation.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Phase 5.6: Policy Registry Synchronization & Migration Service.
 *
 * Provides safe migration of the existing 16-policy cohort into the MongoDB-backed registry:
 * 1. Synchronizes 13 Insurer Profiles with regulatory codes, category, and discovery URLs.
 * 2. Synchronizes 16 Canonical Structured Policies with zero duplication.
 * 3. Registers immutable PolicyDocumentRevisions with physical SHA-256 digests and source provenance.
 * 4. Tracks corresponding PolicyDiscoveryCandidates in PUBLISHED_ELIGIBLE stage.
 * 5. Registers official PolicySource discovery endpoints for automated monitoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyRegistrySyncService {

    private final StructuredPolicyRepository policyRepository;
    private final InsurerProfileRepository insurerProfileRepository;
    private final PolicyDocumentRevisionRepository revisionRepository;
    private final PolicyDiscoveryCandidateRepository candidateRepository;
    private final PolicySourceRepository policySourceRepository;
    private final PolicyIngestionService policyIngestionService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            synchronizePolicyRegistry();
        } catch (Exception e) {
            log.warn("[RegistrySync] Could not synchronize policy registry with MongoDB on startup: {}. Fallback catalog will be used.", e.getMessage());
        }
    }

    public synchronized SyncResult synchronizePolicyRegistry() {
        log.info("[RegistrySync] Starting database-backed policy registry synchronization...");
        int insurersSynced = syncInsurerProfiles();
        int policiesSynced = syncPolicyCohort();
        int sourcesSynced = syncOfficialPolicySources();

        log.info("[RegistrySync] Completed: {} insurers, {} policies/revisions, {} discovery sources synced.",
                insurersSynced, policiesSynced, sourcesSynced);

        return new SyncResult(insurersSynced, policiesSynced, sourcesSynced);
    }

    private int syncInsurerProfiles() {
        List<InsurerProfile> profiles = List.of(
                buildInsurer("HDFC_ERGO", "HDFC ERGO General Insurance Co. Ltd.", "PRIVATE_GENERAL", "146",
                        "https://www.hdfcergo.com", "https://www.hdfcergo.com/health-insurance", List.of("HDFHLIP")),
                buildInsurer("STAR_HEALTH", "Star Health and Allied Insurance Co. Ltd.", "SAHI", "129",
                        "https://www.starhealth.in", "https://www.starhealth.in/health-insurance-plans", List.of("SHAHLIP")),
                buildInsurer("CARE_HEALTH", "Care Health Insurance Ltd.", "SAHI", "148",
                        "https://www.careinsurance.com", "https://www.careinsurance.com/health-insurance-plans", List.of("CHIHLIP", "RHIHLIP")),
                buildInsurer("NIVA_BUPA", "Niva Bupa Health Insurance Co. Ltd.", "SAHI", "145",
                        "https://www.nivabupa.com", "https://www.nivabupa.com/health-insurance-plans", List.of("MAXHLIP", "NBHLIP")),
                buildInsurer("ADITYA_BIRLA", "Aditya Birla Health Insurance Co. Ltd.", "SAHI", "153",
                        "https://www.adityabirlacapital.com", "https://www.adityabirlacapital.com/health-insurance", List.of("ABHHLIP")),
                buildInsurer("ICICI_LOMBARD", "ICICI Lombard General Insurance Co. Ltd.", "PRIVATE_GENERAL", "115",
                        "https://www.icicilombard.com", "https://www.icicilombard.com/health-insurance", List.of("ICIHLIP")),
                buildInsurer("BAG", "Bajaj Allianz General Insurance Co. Ltd.", "PRIVATE_GENERAL", "113",
                        "https://www.bajajallianz.com", "https://www.bajajallianz.com/health-insurance-plans", List.of("BAJHLIP")),
                buildInsurer("TATA_AIG", "Tata AIG General Insurance Co. Ltd.", "PRIVATE_GENERAL", "108",
                        "https://www.tataaig.com", "https://www.tataaig.com/health-insurance", List.of("TATHOIP", "TATIP")),
                buildInsurer("MANIPAL_CIGNA", "ManipalCigna Health Insurance Co. Ltd.", "SAHI", "151",
                        "https://www.manipalcigna.com", "https://www.manipalcigna.com/health-insurance", List.of("MCIHLIP")),
                buildInsurer("NIA", "The New India Assurance Co. Ltd.", "PUBLIC_GENERAL", "190",
                        "https://www.newindia.co.in", "https://www.newindia.co.in/portal/product/health", List.of("NIAHLIP")),
                buildInsurer("UIIC", "United India Insurance Co. Ltd.", "PUBLIC_GENERAL", "545",
                        "https://uiic.co.in", "https://uiic.co.in/health-insurance", List.of("UIIHLIP")),
                buildInsurer("NIC", "National Insurance Co. Ltd.", "PUBLIC_GENERAL", "58",
                        "https://nationalinsurance.nic.co.in", "https://nationalinsurance.nic.co.in/health-insurance", List.of("NICHLIP")),
                buildInsurer("OIC", "The Oriental Insurance Co. Ltd.", "PUBLIC_GENERAL", "556",
                        "https://orientalinsurance.org.in", "https://orientalinsurance.org.in/health-insurance", List.of("OICHLIP"))
        );

        int count = 0;
        for (InsurerProfile profile : profiles) {
            Optional<InsurerProfile> existing = insurerProfileRepository.findByInsurerCode(profile.getInsurerCode());
            if (existing.isPresent()) {
                profile.setId(existing.get().getId());
                profile.setCreatedAt(existing.get().getCreatedAt());
            } else {
                profile.setCreatedAt(Instant.now());
            }
            profile.setUpdatedAt(Instant.now());
            insurerProfileRepository.save(profile);
            count++;
        }
        return count;
    }

    private InsurerProfile buildInsurer(String code, String name, String category, String regNo,
                                       String website, String listingUrl, List<String> uinPrefixes) {
        return InsurerProfile.builder()
                .insurerCode(code)
                .legalName(name)
                .category(category)
                .irdaiRegistrationNumber(regNo)
                .officialWebsite(website)
                .productsListingUrl(listingUrl)
                .approvedUinPrefixes(uinPrefixes)
                .active(true)
                .build();
    }

    private int syncPolicyCohort() {
        List<StructuredPolicy> cohort = policyIngestionService.getAuthenticAcquiredCatalog();
        int count = 0;

        for (StructuredPolicy policy : cohort) {
            boolean isSynthetic = policy.isSyntheticFixture() || "TEST_FIXTURE_OR_SYNTHETIC".equalsIgnoreCase(policy.getDocumentAuthenticityStatus());

            // 1. Sync StructuredPolicy in MongoDB without duplication
            Optional<StructuredPolicy> existingPolicy = policyRepository.findByPolicyCode(policy.getPolicyCode());
            if (existingPolicy.isPresent()) {
                policy.setId(existingPolicy.get().getId());
            }
            policy.setActive(!isSynthetic);
            policy.setSyntheticFixture(isSynthetic);
            policy.setDocumentAuthenticityStatus(isSynthetic ? "TEST_FIXTURE_OR_SYNTHETIC" : "VERIFIED_OFFICIAL_SOURCE");
            StructuredPolicy savedPolicy = policyRepository.save(policy);

            // 2. Sync PolicyDocumentRevision
            String uin = savedPolicy.getIrdaUin();
            String rev = savedPolicy.getUinRevision() != null ? savedPolicy.getUinRevision() : "V01";
            String hash = savedPolicy.getDocumentChecksum();
            String insurerCode = deriveInsurerCode(savedPolicy.getProvider(), savedPolicy.getPolicyCode());

            Optional<PolicyDocumentRevision> existingRev = (hash != null)
                    ? revisionRepository.findByVerifiedSha256(hash)
                    : revisionRepository.findByIrdaUinAndUinRevision(uin, rev);

            PolicyDocumentRevision docRev = existingRev.orElseGet(PolicyDocumentRevision::new);
            docRev.setIrdaUin(uin);
            docRev.setUinRevision(rev);
            docRev.setInsurerCode(insurerCode);
            docRev.setPolicyCode(savedPolicy.getPolicyCode());
            docRev.setProductName(savedPolicy.getPolicyName());
            docRev.setVariantName(savedPolicy.getPlanName());
            docRev.setDocumentArtifactId(savedPolicy.getPolicyWordingDocument());
            docRev.setVerifiedSha256(hash);
            docRev.setSourceUrl(savedPolicy.getOfficialDocumentUrl());
            docRev.setFinalAcquisitionUrl(savedPolicy.getOfficialDocumentUrl());
            docRev.setStatus(isSynthetic ? "PENDING_VERIFICATION" : "ACTIVE_REVISION");
            docRev.setDocumentAuthenticityStatus(isSynthetic ? "TEST_FIXTURE_OR_SYNTHETIC" : "VERIFIED_OFFICIAL_SOURCE");
            docRev.setDiscoveredAt(Instant.now());
            docRev.setAcquiredAt(Instant.now());
            if (docRev.getCreatedAt() == null) {
                docRev.setCreatedAt(Instant.now());
            }
            docRev.setUpdatedAt(Instant.now());
            revisionRepository.save(docRev);

            // 3. Ensure a matching PolicyDiscoveryCandidate exists with correct lifecycle stage
            Optional<PolicyDiscoveryCandidate> existingCandidate = (hash != null)
                    ? candidateRepository.findByVerifiedSha256(hash)
                    : candidateRepository.findByNormalizedProductCode(savedPolicy.getPolicyCode());

            PolicyDiscoveryCandidate candidate = existingCandidate.orElseGet(PolicyDiscoveryCandidate::new);
            candidate.setInsurerCode(insurerCode);
            candidate.setInsurerName(savedPolicy.getProvider());
            candidate.setProductName(savedPolicy.getPolicyName());
            candidate.setNormalizedProductCode(savedPolicy.getPolicyCode());
            candidate.setIrdaUin(uin);
            candidate.setUinRevision(rev);
            candidate.setVariantName(savedPolicy.getPlanName());
            candidate.setSourceUrl(savedPolicy.getOfficialDocumentUrl());
            candidate.setFinalAcquisitionUrl(savedPolicy.getOfficialDocumentUrl());
            candidate.setDocumentArtifactId(savedPolicy.getPolicyWordingDocument());
            candidate.setVerifiedSha256(hash);
            candidate.setDocumentAuthenticityStatus(isSynthetic ? "TEST_FIXTURE_OR_SYNTHETIC" : "VERIFIED_OFFICIAL_SOURCE");
            candidate.setLifecycleStage(isSynthetic ? "TEST_FIXTURE_OR_SYNTHETIC" : "PUBLISHED_ELIGIBLE");
            candidate.setContractConfidenceScore(isSynthetic ? 0.6 : 1.0);
            candidate.setPricingConfidenceScore(isSynthetic ? 0.6 : 1.0);
            candidate.setLinkedPolicyId(savedPolicy.getId());
            candidate.setReviewedBy(isSynthetic ? null : "SYSTEM_MIGRATION_OFFICIAL");
            candidate.setReviewedAt(isSynthetic ? null : Instant.now());
            candidate.setReviewerNotes(isSynthetic
                    ? "Phase 5.5 generated test fixture; requires authentic insurer portal document acquisition and human review."
                    : "Initial migration from authentic acquired cohort with verified SHA-256 and contractual terms.");
            if (candidate.getDiscoveredAt() == null) {
                candidate.setDiscoveredAt(Instant.now());
            }
            if (candidate.getCreatedAt() == null) {
                candidate.setCreatedAt(Instant.now());
            }
            candidate.setUpdatedAt(Instant.now());
            candidateRepository.save(candidate);

            count++;
        }
        return count;
    }

    private int syncOfficialPolicySources() {
        List<StructuredPolicy> cohort = policyIngestionService.getAuthenticAcquiredCatalog();
        int count = 0;

        for (StructuredPolicy p : cohort) {
            String url = p.getOfficialDocumentUrl();
            if (url == null || url.isBlank()) continue;

            Optional<PolicySource> existing = policySourceRepository.findBySourceUrl(url);
            PolicySource source = existing.orElseGet(PolicySource::new);
            source.setSourceName(p.getPolicyName() + " Official Source");
            source.setProvider(p.getProvider());
            source.setCanonicalProductName(p.getPolicyName());
            source.setIrdaUin(p.getIrdaUin());
            source.setExpectedRevision(p.getUinRevision());
            source.setDocumentType(p.getDocumentClassification() != null ? p.getDocumentClassification() : "POLICY_WORDING");
            source.setSourceAuthority("OFFICIAL_INSURER_WEBSITE");
            source.setSourceUrl(url);
            source.setActive(true);
            source.setStatus("ACTIVE");
            source.setLastSha256Checksum(p.getDocumentChecksum());
            source.setDiscoveryMode("CURATED_FALLBACK");
            if (source.getCreatedAt() == null) {
                source.setCreatedAt(Instant.now());
            }
            source.setUpdatedAt(Instant.now());
            policySourceRepository.save(source);
            count++;
        }
        return count;
    }

    private String deriveInsurerCode(String provider, String policyCode) {
        if (policyCode != null && policyCode.contains("_")) {
            String prefix = policyCode.substring(0, policyCode.indexOf('_'));
            if (List.of("NIC", "OIC", "NIA", "UIIC", "BAG").contains(prefix)) {
                return prefix;
            }
        }
        if (provider == null) return "UNKNOWN";
        String lower = provider.toLowerCase();
        if (lower.contains("hdfc")) return "HDFC_ERGO";
        if (lower.contains("star")) return "STAR_HEALTH";
        if (lower.contains("care")) return "CARE_HEALTH";
        if (lower.contains("niva") || lower.contains("max bupa")) return "NIVA_BUPA";
        if (lower.contains("aditya")) return "ADITYA_BIRLA";
        if (lower.contains("icici")) return "ICICI_LOMBARD";
        if (lower.contains("bajaj")) return "BAG";
        if (lower.contains("tata")) return "TATA_AIG";
        if (lower.contains("manipal") || lower.contains("cigna")) return "MANIPAL_CIGNA";
        if (lower.contains("new india")) return "NIA";
        if (lower.contains("united india")) return "UIIC";
        if (lower.contains("national")) return "NIC";
        if (lower.contains("oriental")) return "OIC";
        return "GENERIC_INSURER";
    }

    public record SyncResult(int insurersSynced, int policiesSynced, int sourcesSynced) {}
}
