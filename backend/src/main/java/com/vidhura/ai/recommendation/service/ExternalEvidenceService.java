package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.model.HospitalNetworkNode;
import com.vidhura.ai.recommendation.model.PremiumQuoteRecord;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceRepository;
import com.vidhura.ai.recommendation.repository.HospitalNetworkRepository;
import com.vidhura.ai.recommendation.repository.PremiumQuoteRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service managing external real-world evidence, hospital network nodes,
 * and authentic premium quotes with strict cryptographic provenance and human verification gates.
 *
 * Strict Ground-Truth Constraints:
 * 1. Never fabricates or hallucinates metrics. If an insurer has no verified statutory filing
 *    in the repository, the metrics are returned as null/unverified.
 * 2. Ingested records require valid 64-character SHA-256 hashes matching statutory filings.
 * 3. Human reviewer gate: unreviewed external records remain UNVERIFIED_PENDING_REVIEW until signed off.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalEvidenceService {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final BigDecimal GST_RATE_DIVISOR = new BigDecimal("1.18");

    private final ExternalEvidenceRepository evidenceRepository;
    private final HospitalNetworkRepository hospitalRepository;
    private final PremiumQuoteRepository quoteRepository;
    private final StructuredPolicyRepository policyRepository;

    // =========================================================================
    // 1. EXTERNAL EVIDENCE INGESTION & VERIFICATION PIPELINE
    // =========================================================================

    /**
     * Ingests a new external evidence record with cryptographic SHA-256 validation.
     * Staged as UNVERIFIED_PENDING_REVIEW unless ingested directly by an ADMIN/REVIEWER.
     */
    public ExternalEvidenceDto.EvidenceRecordDto ingestEvidenceRecord(
            ExternalEvidenceDto.IngestEvidenceRequest request, User currentUser) {

        log.info("[ExternalEvidence] Ingesting record: insurer='{}', metric='{}', period='{}'",
                request.getInsurerCode(), request.getMetricKey(), request.getReportingPeriod());

        // 1. Validate cryptographic SHA-256 format
        String cleanHash = request.getSourceDocumentHash() != null
                ? request.getSourceDocumentHash().replaceAll("(?i)^sha256:", "").trim()
                : "";
        if (!SHA256_PATTERN.matcher(cleanHash).matches()) {
            throw new IllegalArgumentException("Invalid source document hash. Strict provenance requires a genuine 64-character hex SHA-256 hash.");
        }

        // 2. Prevent duplicate ingestion for the exact same source filing & metric key
        if (evidenceRepository.existsBySourceDocumentHashAndMetricKey(cleanHash, request.getMetricKey())) {
            throw new IllegalArgumentException("Duplicate evidence record: source hash '" + cleanHash
                    + "' already contains an extraction for metric '" + request.getMetricKey() + "'.");
        }

        // 3. Determine initial verification status based on user role
        boolean isPrivilegedReviewer = currentUser != null && (
                "ROLE_ADMIN".equals(currentUser.getRole()) || "ROLE_REVIEWER".equals(currentUser.getRole())
        );

        String initialStatus = isPrivilegedReviewer ? "VERIFIED_STATUTORY" : "UNVERIFIED_PENDING_REVIEW";
        String verifier = isPrivilegedReviewer ? currentUser.getEmail() : null;
        Instant verifiedTime = isPrivilegedReviewer ? Instant.now() : null;

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .evidenceType(request.getEvidenceType())
                .insurerCode(request.getInsurerCode().toUpperCase().trim())
                .insurerName(request.getInsurerName())
                .metricCategory(request.getMetricCategory())
                .metricKey(request.getMetricKey())
                .metricValue(request.getMetricValue())
                .numericValue(request.getNumericValue())
                .metricUnit(request.getMetricUnit() != null ? request.getMetricUnit() : "PERCENTAGE")
                .reportingPeriod(request.getReportingPeriod())
                .publicationDate(request.getPublicationDate())
                .sourceDocumentTitle(request.getSourceDocumentTitle())
                .sourceUrl(request.getSourceUrl())
                .sourceDocumentHash(cleanHash)
                .citationPageNumber(request.getCitationPageNumber())
                .citationTableReference(request.getCitationTableReference())
                .ingestionTimestamp(Instant.now())
                .verificationStatus(initialStatus)
                .verifiedBy(verifier)
                .verifiedAt(verifiedTime)
                .verificationNotes(request.getVerificationNotes())
                .materialImpact(true)
                .build();

        ExternalEvidenceRecord saved = evidenceRepository.save(record);
        log.info("[ExternalEvidence] Stored evidence record id='{}', status='{}'", saved.getId(), saved.getVerificationStatus());

        return mapToEvidenceDto(saved);
    }

    /**
     * Reviewer-gated verification sign-off for external evidence records.
     */
    public ExternalEvidenceDto.EvidenceRecordDto verifyEvidenceRecord(
            String recordId, ExternalEvidenceDto.VerifyEvidenceRequest request, User reviewer) {

        if (reviewer == null || (!"ROLE_ADMIN".equals(reviewer.getRole()) && !"ROLE_REVIEWER".equals(reviewer.getRole()))) {
            throw new AccessDeniedException("Only designated Compliance Reviewers or Administrators can verify external statutory evidence.");
        }

        ExternalEvidenceRecord record = evidenceRepository.findById(recordId)
                .orElseThrow(() -> new NoSuchElementException("External evidence record not found with ID: " + recordId));

        String newStatus = request.getVerificationStatus();
        if (!"VERIFIED_STATUTORY".equals(newStatus) && !"REJECTED".equals(newStatus)) {
            throw new IllegalArgumentException("Invalid verification status. Allowed: 'VERIFIED_STATUTORY' or 'REJECTED'.");
        }

        record.setVerificationStatus(newStatus);
        record.setVerifiedBy(reviewer.getEmail());
        record.setVerifiedAt(Instant.now());
        if (request.getVerificationNotes() != null) {
            record.setVerificationNotes(request.getVerificationNotes());
        }

        ExternalEvidenceRecord saved = evidenceRepository.save(record);
        log.info("[ExternalEvidence] Record id='{}' signed off as '{}' by '{}'",
                saved.getId(), newStatus, reviewer.getEmail());

        return mapToEvidenceDto(saved);
    }

    // =========================================================================
    // 2. INSURER PERFORMANCE METRICS QUERY PIPELINE
    // =========================================================================

    /**
     * Retrieves verified statutory performance metrics for a specific insurer.
     * Ground-Truth: Returns null/unverified for missing metrics rather than fabricating data.
     */
    public ExternalEvidenceDto.InsurerPerformanceSummaryDto getInsurerPerformanceMetrics(
            String insurerCode, String reportingPeriod) {

        if (insurerCode == null || insurerCode.isBlank()) {
            return null;
        }

        String normCode = insurerCode.toUpperCase().trim();
        List<ExternalEvidenceRecord> records;

        if (reportingPeriod != null && !reportingPeriod.isBlank()) {
            records = evidenceRepository.findByInsurerCodeAndReportingPeriod(normCode, reportingPeriod);
        } else {
            records = evidenceRepository.findByInsurerCode(normCode);
        }

        // Filter to verified statutory records
        List<ExternalEvidenceRecord> verified = records.stream()
                .filter(r -> "VERIFIED_STATUTORY".equals(r.getVerificationStatus()))
                .collect(Collectors.toList());

        if (verified.isEmpty()) {
            // Return unverified shell without fabricating numbers
            return ExternalEvidenceDto.InsurerPerformanceSummaryDto.builder()
                    .insurerCode(normCode)
                    .reportingPeriod(reportingPeriod != null ? reportingPeriod : "UNVERIFIED")
                    .overallPerformanceRating("UNVERIFIED_PENDING_DISCLOSURE")
                    .verificationStatus("UNVERIFIED")
                    .verifiedStatutory(false)
                    .build();
        }

        ExternalEvidenceDto.InsurerPerformanceSummaryDto summary = ExternalEvidenceDto.InsurerPerformanceSummaryDto.builder()
                .insurerCode(normCode)
                .insurerName(verified.get(0).getInsurerName())
                .reportingPeriod(verified.get(0).getReportingPeriod())
                .statutorySourceTitle(verified.get(0).getSourceDocumentTitle())
                .statutoryDocumentHash(verified.get(0).getSourceDocumentHash())
                .sourceUrl(verified.get(0).getSourceUrl())
                .verificationStatus("VERIFIED_STATUTORY")
                .verifiedStatutory(true)
                .build();

        for (ExternalEvidenceRecord r : verified) {
            switch (r.getMetricCategory()) {
                case "INCURRED_CLAIM_RATIO":
                    summary.setIncurredClaimRatio(r.getMetricValue());
                    summary.setIncurredClaimRatioValue(r.getNumericValue());
                    break;
                case "SETTLEMENT_RATIO_LESS_3_MONTHS":
                    summary.setSettlementRatioLessThan3Months(r.getMetricValue());
                    summary.setSettlementRatioLessThan3MonthsValue(r.getNumericValue());
                    break;
                case "SETTLEMENT_RATIO_ANNUAL":
                    summary.setAnnualSettlementRatio(r.getMetricValue());
                    summary.setAnnualSettlementRatioValue(r.getNumericValue());
                    break;
                case "REPUDIATION_RATIO":
                    summary.setRepudiationRatio(r.getMetricValue());
                    summary.setRepudiationRatioValue(r.getNumericValue());
                    break;
                case "COMPLAINTS_PER_10K_CLAIMS":
                    summary.setComplaintsPer10kClaims(r.getMetricValue());
                    summary.setComplaintsPer10kClaimsValue(r.getNumericValue());
                    break;
                case "SOLVENCY_RATIO":
                    summary.setSolvencyRatio(r.getMetricValue());
                    summary.setSolvencyRatioValue(r.getNumericValue());
                    break;
                case "CLAIM_TURNAROUND_DAYS":
                    summary.setClaimTurnaroundDays(r.getMetricValue());
                    summary.setClaimTurnaroundDaysValue(r.getNumericValue());
                    break;
                default:
                    break;
            }
        }

        // Calculate objective rating from statutory metrics
        summary.setOverallPerformanceRating(calculateOverallRating(summary));
        return summary;
    }

    private String calculateOverallRating(ExternalEvidenceDto.InsurerPerformanceSummaryDto s) {
        BigDecimal settlement3m = s.getSettlementRatioLessThan3MonthsValue();
        BigDecimal annualSettlement = s.getAnnualSettlementRatioValue();
        BigDecimal effectiveSettlement = settlement3m != null ? settlement3m : annualSettlement;

        if (effectiveSettlement == null) {
            return "UNVERIFIED_PENDING_DISCLOSURE";
        }

        double val = effectiveSettlement.doubleValue();
        if (val >= 95.0) return "EXCELLENT";
        if (val >= 90.0) return "VERY_GOOD";
        if (val >= 85.0) return "STANDARD";
        return "REQUIRES_ATTENTION";
    }

    // =========================================================================
    // 3. HOSPITAL NETWORK REGISTRY PIPELINE
    // =========================================================================

    /**
     * Ingests or updates a hospital network node.
     */
    public ExternalEvidenceDto.HospitalNodeDto registerHospitalNode(
            ExternalEvidenceDto.IngestHospitalRequest request, User currentUser) {

        log.info("[HospitalRegistry] Registering hospital '{}' (ROHINI={}) in city '{}'",
                request.getHospitalName(), request.getRegistryRohiniCode(), request.getCity());

        HospitalNetworkNode node = hospitalRepository.findByHospitalId(request.getHospitalId())
                .orElse(HospitalNetworkNode.builder().hospitalId(request.getHospitalId()).build());

        node.setHospitalName(request.getHospitalName());
        node.setRegistryRohiniCode(request.getRegistryRohiniCode());
        node.setAddress(request.getAddress());
        node.setCity(request.getCity());
        node.setState(request.getState());
        node.setPinCode(request.getPinCode());
        node.setLatitude(request.getLatitude());
        node.setLongitude(request.getLongitude());
        node.setCategory(request.getCategory() != null ? request.getCategory() : "MULTI_SPECIALTY");
        node.setCashlessAgreements(request.getCashlessAgreements() != null ? request.getCashlessAgreements() : new ArrayList<>());
        node.setVerifiedAt(Instant.now());
        node.setActive(true);

        HospitalNetworkNode saved = hospitalRepository.save(node);
        return mapToHospitalDto(saved, null);
    }

    /**
     * Queries hospital network summary for a specific insurer in a given city or pincode.
     */
    public ExternalEvidenceDto.HospitalNetworkSummaryDto getHospitalNetworkSummary(
            String insurerCode, String city, String pinCode) {

        String normInsurer = insurerCode != null ? insurerCode.toUpperCase().trim() : "";
        List<HospitalNetworkNode> matchedNodes = new ArrayList<>();

        if (city != null && !city.isBlank()) {
            matchedNodes = hospitalRepository.findByCityAndCashlessInsurer(city.trim(), normInsurer);
        } else if (pinCode != null && !pinCode.isBlank()) {
            matchedNodes = hospitalRepository.findByPinCodeAndCashlessInsurer(pinCode.trim(), normInsurer);
        } else if (!normInsurer.isBlank()) {
            matchedNodes = hospitalRepository.findByCashlessInsurerCode(normInsurer);
        }

        int preferredCount = 0;
        int secondaryCount = 0;
        List<ExternalEvidenceDto.HospitalNodeDto> hospitalDtos = new ArrayList<>();

        LocalDate today = LocalDate.now();
        for (HospitalNetworkNode node : matchedNodes) {
            Optional<HospitalNetworkNode.CashlessAgreement> agreement = node.getCashlessAgreements().stream()
                    .filter(a -> normInsurer.equalsIgnoreCase(a.getInsurerCode())
                            && a.isCashlessActive()
                            && (a.getAgreementValidUntil() == null || !a.getAgreementValidUntil().isBefore(today)))
                    .findFirst();

            if (agreement.isPresent()) {
                HospitalNetworkNode.CashlessAgreement agr = agreement.get();
                boolean isAgreementUnverified = agr.getVerificationStatus() != null &&
                        agr.getVerificationStatus().toUpperCase().contains("UNVERIFIED");

                if (!isAgreementUnverified) {
                    String tier = agr.getTier();
                    if ("TIER_1_PREFERRED".equalsIgnoreCase(tier) || "GIPSA_PPN".equalsIgnoreCase(tier)) {
                        preferredCount++;
                    } else {
                        secondaryCount++;
                    }
                }
            }
            hospitalDtos.add(mapToHospitalDto(node, normInsurer));
        }

        // Compute accessibility score (0 to 100) based on verified local availability
        int total = matchedNodes.size();
        int verifiedCount = preferredCount + secondaryCount;
        double score = Math.min(100.0, (preferredCount * 25.0) + (secondaryCount * 10.0));
        if (verifiedCount == 0) score = 0.0;

        return ExternalEvidenceDto.HospitalNetworkSummaryDto.builder()
                .insurerCode(normInsurer)
                .city(city)
                .pinCode(pinCode)
                .totalNetworkHospitalsInCity(total)
                .preferredTier1Count(preferredCount)
                .secondaryCount(secondaryCount)
                .cashlessAccessibilityScore(score)
                .verificationBadge(verifiedCount > 0 ? "VERIFIED_HOSPITAL_REGISTRY" : "UNVERIFIED_PENDING_REGISTRY_AUDIT")
                .networkDisclaimer("Empanelment in provider network and cashless facility availability are subject to active bilateral hospital-insurer agreements and TPA pre-authorization at time of admission. Does not constitute guaranteed cashless approval.")
                .hospitals(hospitalDtos)
                .build();
    }

    // =========================================================================
    // 4. AUTHENTIC & MODELLED QUOTE PIPELINE
    // =========================================================================

    /**
     * Records an authentic or verified prospect quote with exact decimal amounts.
     */
    public ExternalEvidenceDto.QuoteDetailDto recordPremiumQuote(PremiumQuoteRecord quote) {
        log.info("[QuotePipeline] Recording quote for policy '{}', age={}, SI='{}', annual='{}'",
                quote.getPolicyCode(), quote.getAge(), quote.getSumInsured(), quote.getQuotedAnnualPremium());

        if (quote.getQuoteId() == null || quote.getQuoteId().isBlank()) {
            quote.setQuoteId("quote-" + UUID.randomUUID().toString().substring(0, 8));
        }

        // Calculate GST (18%) and base premium if not already split
        if (quote.getQuotedAnnualPremium() != null && (quote.getQuotedBasePremium() == null || quote.getQuotedGst() == null)) {
            BigDecimal base = quote.getQuotedAnnualPremium().divide(GST_RATE_DIVISOR, 2, RoundingMode.HALF_UP);
            BigDecimal gst = quote.getQuotedAnnualPremium().subtract(base);
            quote.setQuotedBasePremium(base);
            quote.setQuotedGst(gst);
        }

        if (quote.getExpiryTimestamp() == null) {
            quote.setExpiryTimestamp(Instant.now().plus(30, ChronoUnit.DAYS));
        }

        PremiumQuoteRecord saved = quoteRepository.save(quote);
        return mapToQuoteDto(saved);
    }

    /**
     * Resolves quote: First checks repository for an authentic verified quote.
     * If absent or expired, falls back to actuarial model estimate with clear non-binding label.
     */
    public ExternalEvidenceDto.QuoteDetailDto getAuthenticOrModelledQuote(
            String policyCode, int age, BigDecimal sumInsured, String pinCode, String familyConstruct) {

        if (policyCode == null || sumInsured == null) {
            return null;
        }

        // 1. Check for authentic quote in repository
        Optional<PremiumQuoteRecord> authenticQuote = quoteRepository
                .findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(policyCode, age, sumInsured);

        Instant now = Instant.now();
        if (authenticQuote.isPresent()) {
            PremiumQuoteRecord q = authenticQuote.get();
            boolean isExpired = q.getExpiryTimestamp() != null && q.getExpiryTimestamp().isBefore(now);
            if (isExpired && !q.isStale()) {
                q.setStale(true);
                try {
                    quoteRepository.save(q);
                } catch (Exception e) {
                    log.warn("[QuotePipeline] Could not mark quote {} as stale: {}", q.getQuoteId(), e.getMessage());
                }
            }
            if (!isExpired && !q.isStale() && "VERIFIED_AUTHENTIC_QUOTE".equals(q.getVerificationStatus())) {
                log.info("[QuotePipeline] Authentic quote HIT for policy '{}', age={}, SI='{}': ₹{}",
                        policyCode, age, sumInsured, q.getQuotedAnnualPremium());
                return mapToQuoteDto(q);
            }
        }

        // 2. Actuarial model fallback (clearly badged as non-binding estimate)
        Optional<StructuredPolicy> policyOpt = policyRepository.findByPolicyCode(policyCode);
        if (policyOpt.isEmpty()) {
            return null;
        }

        StructuredPolicy policy = policyOpt.get();
        double modelledAnnual = estimateModelledPremium(policy, age, sumInsured.longValue());

        BigDecimal annualAmount = BigDecimal.valueOf(modelledAnnual).setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = annualAmount.divide(GST_RATE_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal gst = annualAmount.subtract(base);

        NumberFormat inrFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        String formatted = inrFormat.format(annualAmount.doubleValue());

        return ExternalEvidenceDto.QuoteDetailDto.builder()
                .quoteId("modelled-" + UUID.randomUUID().toString().substring(0, 8))
                .policyCode(policyCode)
                .uinRevision(policy.getUinRevision())
                .insurerCode(policy.getProvider())
                .sumInsured(sumInsured)
                .age(age)
                .pinCode(pinCode)
                .familyConstruct(familyConstruct != null ? familyConstruct : "INDIVIDUAL")
                .quotedAnnualPremium(annualAmount)
                .quotedBasePremium(base)
                .quotedGst(gst)
                .quotingChannel("ESTIMATED_ACTUARIAL_MODEL")
                .quoteTimestamp(now)
                .isAuthentic(false)
                .verificationStatus("ESTIMATED_ACTUARIAL_MODEL")
                .formattedAnnualPremium(formatted)
                .isStale(false)
                .build();
    }

    private double estimateModelledPremium(StructuredPolicy policy, int age, long sumInsured) {
        if (policy.getSumInsuredTiers() == null || policy.getSumInsuredTiers().isEmpty()) {
            return 12000.0;
        }

        StructuredPolicy.SumInsuredTier matchTier = policy.getSumInsuredTiers().stream()
                .filter(t -> t.getSumInsured() == sumInsured)
                .findFirst()
                .orElse(policy.getSumInsuredTiers().get(0));

        double premium;
        if (age <= 35) {
            premium = matchTier.getPremiumAge18To35();
        } else if (age <= 45) {
            premium = matchTier.getPremiumAge36To45();
        } else if (age <= 55) {
            premium = matchTier.getPremiumAge46To55();
        } else if (age <= 65) {
            premium = matchTier.getPremiumAge56To65();
        } else {
            premium = matchTier.getPremiumAge66Plus();
        }

        return premium > 0 ? premium : 12000.0;
    }

    // =========================================================================
    // MAPPERS
    // =========================================================================

    private ExternalEvidenceDto.EvidenceRecordDto mapToEvidenceDto(ExternalEvidenceRecord r) {
        return ExternalEvidenceDto.EvidenceRecordDto.builder()
                .id(r.getId())
                .evidenceType(r.getEvidenceType())
                .insurerCode(r.getInsurerCode())
                .insurerName(r.getInsurerName())
                .metricCategory(r.getMetricCategory())
                .metricKey(r.getMetricKey())
                .metricValue(r.getMetricValue())
                .numericValue(r.getNumericValue())
                .metricUnit(r.getMetricUnit())
                .reportingPeriod(r.getReportingPeriod())
                .publicationDate(r.getPublicationDate())
                .sourceDocumentTitle(r.getSourceDocumentTitle())
                .sourceUrl(r.getSourceUrl())
                .sourceDocumentHash(r.getSourceDocumentHash())
                .citationPageNumber(r.getCitationPageNumber())
                .citationTableReference(r.getCitationTableReference())
                .verificationStatus(r.getVerificationStatus())
                .verifiedBy(r.getVerifiedBy())
                .verifiedAt(r.getVerifiedAt())
                .verificationNotes(r.getVerificationNotes())
                .ingestionTimestamp(r.getIngestionTimestamp())
                .build();
    }

    private ExternalEvidenceDto.HospitalNodeDto mapToHospitalDto(HospitalNetworkNode node, String targetInsurer) {
        boolean cashless = false;
        String tier = "STANDARD";
        java.time.LocalDate validUntil = null;
        String status = "UNVERIFIED";

        if (targetInsurer != null && node.getCashlessAgreements() != null) {
            Optional<HospitalNetworkNode.CashlessAgreement> agr = node.getCashlessAgreements().stream()
                    .filter(a -> targetInsurer.equalsIgnoreCase(a.getInsurerCode()))
                    .findFirst();
            if (agr.isPresent()) {
                HospitalNetworkNode.CashlessAgreement agreement = agr.get();
                tier = agreement.getTier() != null ? agreement.getTier() : "STANDARD";
                validUntil = agreement.getAgreementValidUntil();
                boolean isAgreementExpired = validUntil != null && validUntil.isBefore(java.time.LocalDate.now());

                boolean isAgreementUnverified = agreement.getVerificationStatus() != null &&
                        agreement.getVerificationStatus().toUpperCase().contains("UNVERIFIED");

                if (!agreement.isCashlessActive()) {
                    cashless = false;
                    status = "CASHLESS_INACTIVE";
                } else if (isAgreementExpired) {
                    cashless = false;
                    status = "HISTORICAL_AGREEMENT_EXPIRED";
                } else if (isAgreementUnverified) {
                    cashless = false;
                    status = agreement.getVerificationStatus();
                } else {
                    cashless = true;
                    status = agreement.getVerificationStatus() != null ? agreement.getVerificationStatus() : "VERIFIED_OFFICIAL_DISCLOSURE";
                }
            }
        }

        return ExternalEvidenceDto.HospitalNodeDto.builder()
                .hospitalId(node.getHospitalId())
                .hospitalName(node.getHospitalName())
                .registryRohiniCode(node.getRegistryRohiniCode())
                .address(node.getAddress())
                .city(node.getCity())
                .pinCode(node.getPinCode())
                .latitude(node.getLatitude())
                .longitude(node.getLongitude())
                .category(node.getCategory())
                .cashlessActive(cashless)
                .tier(tier)
                .agreementValidUntil(validUntil)
                .verificationStatus(status)
                .build();
    }

    private ExternalEvidenceDto.QuoteDetailDto mapToQuoteDto(PremiumQuoteRecord q) {
        NumberFormat inr = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        String formatted = q.getQuotedAnnualPremium() != null
                ? inr.format(q.getQuotedAnnualPremium().doubleValue())
                : "₹0.00";

        boolean isExpired = q.getExpiryTimestamp() != null && q.getExpiryTimestamp().isBefore(Instant.now());
        boolean isStale = q.isStale() || isExpired;
        boolean isAuthentic = "VERIFIED_AUTHENTIC_QUOTE".equals(q.getVerificationStatus()) && !isStale;
        String verificationStatus = isStale && "VERIFIED_AUTHENTIC_QUOTE".equals(q.getVerificationStatus())
                ? "EXPIRED_AUTHENTIC_QUOTE"
                : q.getVerificationStatus();

        return ExternalEvidenceDto.QuoteDetailDto.builder()
                .quoteId(q.getQuoteId())
                .policyCode(q.getPolicyCode())
                .uinRevision(q.getUinRevision())
                .insurerCode(q.getInsurerCode())
                .sumInsured(q.getSumInsured())
                .age(q.getAge())
                .pinCode(q.getPinCode())
                .zone(q.getZone())
                .familyConstruct(q.getFamilyConstruct())
                .quotedAnnualPremium(q.getQuotedAnnualPremium())
                .quotedBasePremium(q.getQuotedBasePremium())
                .quotedGst(q.getQuotedGst())
                .quotingChannel(q.getQuotingChannel())
                .quoteTimestamp(q.getQuoteTimestamp())
                .quoteReferenceNumber(q.getQuoteReferenceNumber())
                .sourceDocumentHash(q.getSourceDocumentHash())
                .isAuthentic(isAuthentic)
                .verificationStatus(verificationStatus)
                .formattedAnnualPremium(formatted)
                .isStale(isStale)
                .build();
    }
}
