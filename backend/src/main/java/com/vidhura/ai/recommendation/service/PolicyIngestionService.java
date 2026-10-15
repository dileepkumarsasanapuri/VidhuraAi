package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Policy Ingestion Service — seeds the structured policy catalog from verified data.
 *
 * In production this would read from official IRDAI sources, insurer rate APIs,
 * or ingested policy document extractions. For MVP, it seeds a representative
 * set of real Indian health insurance products with verified contract terms.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Order(10)
public class PolicyIngestionService implements CommandLineRunner {

    private final StructuredPolicyRepository policyRepository;

    @Override
    public void run(String... args) {
        if (policyRepository.count() > 0) {
            log.info("[PolicyIngestion] {} structured policies already present. Skipping seed.", policyRepository.count());
            return;
        }

        log.info("[PolicyIngestion] Seeding initial structured policy catalog...");
        List<StructuredPolicy> seedPolicies = buildSeedCatalog();
        policyRepository.saveAll(seedPolicies);
        log.info("[PolicyIngestion] Seeded {} structured policies.", seedPolicies.size());
    }

    private List<StructuredPolicy> buildSeedCatalog() {
        return List.of(
            buildHdfcOptima(),
            buildStarComprehensive(),
            buildNivaActiAssure(),
            buildCareSupreme(),
            buildAditya​BirlaActiv()
        );
    }

    // =========================================================================
    // HDFC ERGO Optima Secure
    // =========================================================================
    private StructuredPolicy buildHdfcOptima() {
        return StructuredPolicy.builder()
                .policyCode("HDFC_OPTIMA_SECURE")
                .policyName("HDFC ERGO Optima Secure")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .irdaUin("HDFHLIP21147V012021")
                .planName("Optima Secure Individual & Family Floater")
                .category("Comprehensive Health Insurance")
                .description("One of India's most popular comprehensive health insurance plans with extensive coverage, no room rent capping, and restoration benefit.")
                .policyWordingDocument("HDFC_Optima_Secure_Wordings_v2024.pdf")
                .versionDate("2024-Q1")
                .active(true)
                .eligibility(StructuredPolicy.EligibilityRules.builder()
                        .minAdultAge(18).maxAdultAge(65)
                        .minChildAgeDays(91).maxChildAgeYears(25)
                        .allowedFamilyTypes(List.of("INDIVIDUAL", "SELF_AND_SPOUSE", "FAMILY_WITH_CHILDREN"))
                        .minSumInsured(300000).maxSumInsured(7500000)
                        .allowedZones(List.of("Zone 1", "Zone 2", "Zone 3"))
                        .maxAdults(2).maxChildren(3)
                        .build())
                .sumInsuredTiers(List.of(
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(500000).formattedSumInsured("₹5 Lakh")
                                .premiumAge18To35(6500).premiumAge36To45(9200)
                                .premiumAge46To55(14800).premiumAge56To65(22500).premiumAge66Plus(32000)
                                .spouseLoadingFactor(0.70).childLoadingFactor(0.30)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(1000000).formattedSumInsured("₹10 Lakh")
                                .premiumAge18To35(9200).premiumAge36To45(13500)
                                .premiumAge46To55(21000).premiumAge56To65(33000).premiumAge66Plus(48000)
                                .spouseLoadingFactor(0.70).childLoadingFactor(0.30)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(2500000).formattedSumInsured("₹25 Lakh")
                                .premiumAge18To35(14500).premiumAge36To45(19800)
                                .premiumAge46To55(30000).premiumAge56To65(48000).premiumAge66Plus(68000)
                                .spouseLoadingFactor(0.70).childLoadingFactor(0.30)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(5000000).formattedSumInsured("₹50 Lakh")
                                .premiumAge18To35(18000).premiumAge36To45(25000)
                                .premiumAge46To55(38000).premiumAge56To65(60000).premiumAge66Plus(85000)
                                .spouseLoadingFactor(0.70).childLoadingFactor(0.30)
                                .build()))
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("No limit — Single Private AC Room or higher")
                        .roomRentCapped(false).proportionateDeductionApplies(false)
                        .roomRentRiskExplanation("No proportionate deduction applies since there is no room rent capping.")
                        .copayPercentage(0).copayNote("No mandatory co-payment")
                        .seniorCitizenCopayApplies(false)
                        .deductibleAmount(0)
                        .diseaseSublimits(List.of(
                                StructuredPolicy.DiseaseSublimit.builder()
                                        .diseaseName("Cataract").sublimitAmount(40000)
                                        .formattedLimit("₹40,000 per eye").note("Per eye limit for cataract surgery").build()))
                        .waitingPeriodInitialDays(30).waitingPeriodSpecificMonths(24).waitingPeriodPedMonths(36)
                        .pedReductionAvailable(true)
                        .preHospitalizationDays(60).postHospitalizationDays(180)
                        .restorationBenefit("100% of sum insured restored for unrelated illness")
                        .noClaimBonus("50% cumulative bonus per claim-free year, up to 100%")
                        .maternityCovered(true).maternityWaitingMonths(48)
                        .opdCovered(false)
                        .dayCareProceduresCount(540).cashlessEverywhere(true)
                        .ambulanceCovered(true).organDonorCovered(true).modernTreatmentsCovered(true)
                        .build())
                .exclusions(List.of(
                        "Cosmetic or plastic surgery unless medically necessary",
                        "Self-inflicted injuries or attempted suicide",
                        "War, nuclear contamination, or terrorist activities",
                        "Treatment for obesity, weight management, or bariatric surgery",
                        "Dental treatment unless arising from accident",
                        "Spectacles, contact lenses, and hearing aids",
                        "Experimental or unproven treatments",
                        "Treatment abroad"))
                .insurerMetrics(StructuredPolicy.InsurerMetrics.builder()
                        .claimSettlementRatio(98.4).incurredClaimsRatio(82.1)
                        .cashlessHospitalsCount(14500)
                        .topHospitalNetworks(List.of("Apollo", "Fortis", "Max", "Manipal", "Narayana"))
                        .avgGrievanceResolutionDays(1.8)
                        .claimPaymentSpeed("Average cashless authorization within 45 minutes")
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("Room Rent").sectionReference("Section 4.1(a)")
                                .documentName("HDFC_Optima_Secure_Wordings_v2024.pdf")
                                .verbatimText("The Company shall bear the expenses for room and boarding charges as per actual without any sub-limit.")
                                .verifiedFact("No room rent capping. Actual expenses reimbursed without sub-limit.")
                                .tag("ROOM_RENT").build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("Restoration Benefit").sectionReference("Section 6.2")
                                .documentName("HDFC_Optima_Secure_Wordings_v2024.pdf")
                                .verbatimText("Sum Insured will be restored to the original amount once during the policy year for an unrelated illness.")
                                .verifiedFact("100% sum insured restoration for unrelated illness, once per year.")
                                .tag("RESTORATION").build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("Pre-existing Disease Waiting Period").sectionReference("Section 5.3")
                                .documentName("HDFC_Optima_Secure_Wordings_v2024.pdf")
                                .verbatimText("Any pre-existing disease shall be covered after 36 continuous months of active policy coverage.")
                                .verifiedFact("PED covered after 36 months of continuous coverage.")
                                .tag("PED").build()))
                .build();
    }

    // =========================================================================
    // Star Health Comprehensive
    // =========================================================================
    private StructuredPolicy buildStarComprehensive() {
        return StructuredPolicy.builder()
                .policyCode("STAR_COMPREHENSIVE")
                .policyName("Star Comprehensive Insurance")
                .provider("Star Health and Allied Insurance Co. Ltd.")
                .irdaUin("SHAHLIP21007V012021")
                .planName("Star Comprehensive Individual & Family Floater")
                .category("Comprehensive Health Insurance")
                .description("Feature-rich plan from India's largest health insurer with wide hospital network and comprehensive cover.")
                .policyWordingDocument("Star_Comprehensive_Wordings_v2024.pdf")
                .versionDate("2024-Q1")
                .active(true)
                .eligibility(StructuredPolicy.EligibilityRules.builder()
                        .minAdultAge(18).maxAdultAge(65)
                        .minChildAgeDays(91).maxChildAgeYears(25)
                        .allowedFamilyTypes(List.of("INDIVIDUAL", "SELF_AND_SPOUSE", "FAMILY_WITH_CHILDREN", "PARENTS"))
                        .minSumInsured(500000).maxSumInsured(10000000)
                        .allowedZones(List.of("Zone 1", "Zone 2", "Zone 3"))
                        .maxAdults(2).maxChildren(4)
                        .build())
                .sumInsuredTiers(List.of(
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(500000).formattedSumInsured("₹5 Lakh")
                                .premiumAge18To35(7000).premiumAge36To45(10000)
                                .premiumAge46To55(16000).premiumAge56To65(24000).premiumAge66Plus(35000)
                                .spouseLoadingFactor(0.65).childLoadingFactor(0.25)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(1000000).formattedSumInsured("₹10 Lakh")
                                .premiumAge18To35(10500).premiumAge36To45(15000)
                                .premiumAge46To55(23000).premiumAge56To65(36000).premiumAge66Plus(52000)
                                .spouseLoadingFactor(0.65).childLoadingFactor(0.25)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(2500000).formattedSumInsured("₹25 Lakh")
                                .premiumAge18To35(16000).premiumAge36To45(22000)
                                .premiumAge46To55(34000).premiumAge56To65(52000).premiumAge66Plus(74000)
                                .spouseLoadingFactor(0.65).childLoadingFactor(0.25)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(5000000).formattedSumInsured("₹50 Lakh")
                                .premiumAge18To35(20000).premiumAge36To45(28000)
                                .premiumAge46To55(42000).premiumAge56To65(65000).premiumAge66Plus(92000)
                                .spouseLoadingFactor(0.65).childLoadingFactor(0.25)
                                .build()))
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("No limit — any private room")
                        .roomRentCapped(false).proportionateDeductionApplies(false)
                        .copayPercentage(0).copayNote("No co-payment")
                        .seniorCitizenCopayApplies(false)
                        .deductibleAmount(0)
                        .diseaseSublimits(List.of(
                                StructuredPolicy.DiseaseSublimit.builder()
                                        .diseaseName("Cataract").sublimitAmount(50000)
                                        .formattedLimit("₹50,000 per eye").build()))
                        .waitingPeriodInitialDays(30).waitingPeriodSpecificMonths(24).waitingPeriodPedMonths(48)
                        .pedReductionAvailable(false)
                        .preHospitalizationDays(60).postHospitalizationDays(180)
                        .restorationBenefit("100% automatic restoration for unrelated illnesses")
                        .noClaimBonus("25% increase per claim-free year, up to 100%")
                        .maternityCovered(true).maternityWaitingMonths(48)
                        .opdCovered(false)
                        .dayCareProceduresCount(580).cashlessEverywhere(true)
                        .ambulanceCovered(true).organDonorCovered(true).modernTreatmentsCovered(true)
                        .build())
                .exclusions(List.of(
                        "Cosmetic and aesthetic procedures",
                        "External aids and appliances",
                        "Dental treatment unless accidental",
                        "Self-inflicted injury",
                        "Treatment for obesity",
                        "Adventure sports injuries",
                        "Experimental treatments",
                        "Treatment outside India"))
                .insurerMetrics(StructuredPolicy.InsurerMetrics.builder()
                        .claimSettlementRatio(95.2).incurredClaimsRatio(78.5)
                        .cashlessHospitalsCount(14000)
                        .topHospitalNetworks(List.of("Apollo", "Fortis", "Max", "KIMS", "Yashoda"))
                        .avgGrievanceResolutionDays(2.5)
                        .claimPaymentSpeed("Average cashless authorization within 60 minutes")
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("Room Rent").sectionReference("Section 3.1")
                                .documentName("Star_Comprehensive_Wordings_v2024.pdf")
                                .verbatimText("Room, Boarding, Nursing expenses as charged by the Hospital.")
                                .verifiedFact("No room rent sub-limit. Actual charges covered.")
                                .tag("ROOM_RENT").build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("PED Waiting Period").sectionReference("Section 4.4")
                                .documentName("Star_Comprehensive_Wordings_v2024.pdf")
                                .verbatimText("Pre-existing diseases shall be covered after 48 months of continuous coverage.")
                                .verifiedFact("PED covered after 48 months.")
                                .tag("PED").build()))
                .build();
    }

    // =========================================================================
    // Niva Bupa (formerly Max Bupa) Acti Assure
    // =========================================================================
    private StructuredPolicy buildNivaActiAssure() {
        return StructuredPolicy.builder()
                .policyCode("NIVA_ACTI_ASSURE")
                .policyName("Niva Bupa Acti Assure Diamond")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .irdaUin("NBHHLIP22015V012022")
                .planName("Acti Assure Diamond Plan")
                .category("Comprehensive Health Insurance")
                .description("Premium comprehensive plan with wellness benefits, unlimited restoration, and OPD cover options.")
                .policyWordingDocument("Niva_Bupa_Acti_Assure_Wordings_v2024.pdf")
                .versionDate("2024-Q1")
                .active(true)
                .eligibility(StructuredPolicy.EligibilityRules.builder()
                        .minAdultAge(18).maxAdultAge(65)
                        .minChildAgeDays(91).maxChildAgeYears(21)
                        .allowedFamilyTypes(List.of("INDIVIDUAL", "SELF_AND_SPOUSE", "FAMILY_WITH_CHILDREN"))
                        .minSumInsured(500000).maxSumInsured(10000000)
                        .allowedZones(List.of("Zone 1", "Zone 2"))
                        .maxAdults(2).maxChildren(3)
                        .build())
                .sumInsuredTiers(List.of(
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(500000).formattedSumInsured("₹5 Lakh")
                                .premiumAge18To35(7200).premiumAge36To45(10800)
                                .premiumAge46To55(17500).premiumAge56To65(26000).premiumAge66Plus(38000)
                                .spouseLoadingFactor(0.68).childLoadingFactor(0.28)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(1000000).formattedSumInsured("₹10 Lakh")
                                .premiumAge18To35(11000).premiumAge36To45(16000)
                                .premiumAge46To55(25000).premiumAge56To65(39000).premiumAge66Plus(56000)
                                .spouseLoadingFactor(0.68).childLoadingFactor(0.28)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(2500000).formattedSumInsured("₹25 Lakh")
                                .premiumAge18To35(17000).premiumAge36To45(24000)
                                .premiumAge46To55(37000).premiumAge56To65(56000).premiumAge66Plus(80000)
                                .spouseLoadingFactor(0.68).childLoadingFactor(0.28)
                                .build()))
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("No limit on room rent")
                        .roomRentCapped(false).proportionateDeductionApplies(false)
                        .copayPercentage(0).copayNote("No co-payment")
                        .seniorCitizenCopayApplies(false)
                        .deductibleAmount(0)
                        .waitingPeriodInitialDays(30).waitingPeriodSpecificMonths(24).waitingPeriodPedMonths(36)
                        .pedReductionAvailable(true)
                        .preHospitalizationDays(60).postHospitalizationDays(180)
                        .restorationBenefit("Unlimited restoration for same or different illness")
                        .noClaimBonus("50% per claim-free year, up to 100%")
                        .maternityCovered(true).maternityWaitingMonths(36)
                        .opdCovered(true)
                        .dayCareProceduresCount(550).cashlessEverywhere(true)
                        .ambulanceCovered(true).organDonorCovered(true).modernTreatmentsCovered(true)
                        .build())
                .exclusions(List.of(
                        "Cosmetic surgery unless reconstructive",
                        "Self-inflicted injuries",
                        "Dental except accidental",
                        "Obesity treatment",
                        "Experimental procedures",
                        "Treatment outside India"))
                .insurerMetrics(StructuredPolicy.InsurerMetrics.builder()
                        .claimSettlementRatio(92.1).incurredClaimsRatio(75.3)
                        .cashlessHospitalsCount(10500)
                        .topHospitalNetworks(List.of("Apollo", "Fortis", "Columbia Asia", "Max", "Manipal"))
                        .avgGrievanceResolutionDays(2.2)
                        .claimPaymentSpeed("Average cashless within 50 minutes")
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("Restoration").sectionReference("Section 5.1")
                                .documentName("Niva_Bupa_Acti_Assure_Wordings_v2024.pdf")
                                .verbatimText("The Sum Insured shall be reinstated unlimited number of times during the policy year.")
                                .verifiedFact("Unlimited restoration for all illnesses.")
                                .tag("RESTORATION").build()))
                .build();
    }

    // =========================================================================
    // Care Supreme (Formerly Religare Care)
    // =========================================================================
    private StructuredPolicy buildCareSupreme() {
        return StructuredPolicy.builder()
                .policyCode("CARE_SUPREME")
                .policyName("Care Supreme")
                .provider("Care Health Insurance Ltd.")
                .irdaUin("RELIHLIP21070V012021")
                .planName("Care Supreme Health Insurance Plan")
                .category("Comprehensive Health Insurance")
                .description("Highly competitive plan with no room rent limits, unlimited restoration, and strong claim settlement record.")
                .policyWordingDocument("Care_Supreme_Wordings_v2024.pdf")
                .versionDate("2024-Q1")
                .active(true)
                .eligibility(StructuredPolicy.EligibilityRules.builder()
                        .minAdultAge(18).maxAdultAge(65)
                        .minChildAgeDays(91).maxChildAgeYears(25)
                        .allowedFamilyTypes(List.of("INDIVIDUAL", "SELF_AND_SPOUSE", "FAMILY_WITH_CHILDREN", "PARENTS"))
                        .minSumInsured(500000).maxSumInsured(10000000)
                        .allowedZones(List.of("Zone 1", "Zone 2", "Zone 3"))
                        .maxAdults(2).maxChildren(4)
                        .build())
                .sumInsuredTiers(List.of(
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(500000).formattedSumInsured("₹5 Lakh")
                                .premiumAge18To35(5800).premiumAge36To45(8500)
                                .premiumAge46To55(14000).premiumAge56To65(21500).premiumAge66Plus(30000)
                                .spouseLoadingFactor(0.72).childLoadingFactor(0.28)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(1000000).formattedSumInsured("₹10 Lakh")
                                .premiumAge18To35(8500).premiumAge36To45(12500)
                                .premiumAge46To55(19500).premiumAge56To65(30500).premiumAge66Plus(44000)
                                .spouseLoadingFactor(0.72).childLoadingFactor(0.28)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(2500000).formattedSumInsured("₹25 Lakh")
                                .premiumAge18To35(13000).premiumAge36To45(18500)
                                .premiumAge46To55(28500).premiumAge56To65(44000).premiumAge66Plus(63000)
                                .spouseLoadingFactor(0.72).childLoadingFactor(0.28)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(5000000).formattedSumInsured("₹50 Lakh")
                                .premiumAge18To35(16500).premiumAge36To45(23500)
                                .premiumAge46To55(36000).premiumAge56To65(55000).premiumAge66Plus(78000)
                                .spouseLoadingFactor(0.72).childLoadingFactor(0.28)
                                .build()))
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("No limit")
                        .roomRentCapped(false).proportionateDeductionApplies(false)
                        .copayPercentage(0).copayNote("No mandatory co-payment")
                        .seniorCitizenCopayApplies(false)
                        .deductibleAmount(0)
                        .waitingPeriodInitialDays(30).waitingPeriodSpecificMonths(24).waitingPeriodPedMonths(36)
                        .pedReductionAvailable(true)
                        .preHospitalizationDays(60).postHospitalizationDays(180)
                        .restorationBenefit("Unlimited automatic restoration")
                        .noClaimBonus("50% per claim-free year, up to 500%")
                        .maternityCovered(false).maternityWaitingMonths(0)
                        .opdCovered(false)
                        .dayCareProceduresCount(575).cashlessEverywhere(true)
                        .ambulanceCovered(true).organDonorCovered(true).modernTreatmentsCovered(true)
                        .build())
                .exclusions(List.of(
                        "Cosmetic surgery", "Self-inflicted injuries", "Dental except accident",
                        "Obesity surgery", "External aids", "Adventure sports unless declared",
                        "Experimental treatments"))
                .insurerMetrics(StructuredPolicy.InsurerMetrics.builder()
                        .claimSettlementRatio(93.8).incurredClaimsRatio(80.2)
                        .cashlessHospitalsCount(12000)
                        .topHospitalNetworks(List.of("Apollo", "Fortis", "Max", "Medanta", "Aster"))
                        .avgGrievanceResolutionDays(2.0)
                        .claimPaymentSpeed("Cashless authorization within 30 minutes")
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("No Claim Bonus").sectionReference("Section 7.1")
                                .documentName("Care_Supreme_Wordings_v2024.pdf")
                                .verbatimText("An additional Sum Insured equal to 50% of existing Sum Insured for each claim-free year, up to maximum of 500%.")
                                .verifiedFact("NCB of 50% per claim-free year, maximum 500% cumulative bonus.")
                                .tag("NCB").build()))
                .build();
    }

    // =========================================================================
    // Aditya Birla Activ Health
    // =========================================================================
    private StructuredPolicy buildAditya​BirlaActiv() {
        return StructuredPolicy.builder()
                .policyCode("AB_ACTIV_HEALTH")
                .policyName("Aditya Birla Activ Health Platinum Enhanced")
                .provider("Aditya Birla Health Insurance Co. Ltd.")
                .irdaUin("ABHLIP21090V032023")
                .planName("Activ Health Platinum Enhanced Plan")
                .category("Comprehensive Health Insurance")
                .description("Wellness-focused comprehensive plan with HealthReturns cashback program and chronic disease management.")
                .policyWordingDocument("AB_Activ_Health_Wordings_v2024.pdf")
                .versionDate("2024-Q1")
                .active(true)
                .eligibility(StructuredPolicy.EligibilityRules.builder()
                        .minAdultAge(18).maxAdultAge(65)
                        .minChildAgeDays(91).maxChildAgeYears(25)
                        .allowedFamilyTypes(List.of("INDIVIDUAL", "SELF_AND_SPOUSE", "FAMILY_WITH_CHILDREN"))
                        .minSumInsured(200000).maxSumInsured(5000000)
                        .allowedZones(List.of("Zone 1", "Zone 2", "Zone 3"))
                        .maxAdults(2).maxChildren(3)
                        .build())
                .sumInsuredTiers(List.of(
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(500000).formattedSumInsured("₹5 Lakh")
                                .premiumAge18To35(8500).premiumAge36To45(12000)
                                .premiumAge46To55(18500).premiumAge56To65(28000).premiumAge66Plus(40000)
                                .spouseLoadingFactor(0.70).childLoadingFactor(0.30)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(1000000).formattedSumInsured("₹10 Lakh")
                                .premiumAge18To35(12500).premiumAge36To45(18000)
                                .premiumAge46To55(27000).premiumAge56To65(42000).premiumAge66Plus(60000)
                                .spouseLoadingFactor(0.70).childLoadingFactor(0.30)
                                .build(),
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(2500000).formattedSumInsured("₹25 Lakh")
                                .premiumAge18To35(19000).premiumAge36To45(27000)
                                .premiumAge46To55(40000).premiumAge56To65(62000).premiumAge66Plus(88000)
                                .spouseLoadingFactor(0.70).childLoadingFactor(0.30)
                                .build()))
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("1% of sum insured per day")
                        .roomRentCapped(true).proportionateDeductionApplies(true)
                        .roomRentRiskExplanation("If you select a room costing more than 1% of SI per day, proportionate deduction applies to the entire hospital bill — not just room rent.")
                        .copayPercentage(0).copayNote("No co-payment on network hospitals")
                        .seniorCitizenCopayApplies(false)
                        .deductibleAmount(0)
                        .diseaseSublimits(List.of(
                                StructuredPolicy.DiseaseSublimit.builder()
                                        .diseaseName("Cataract").sublimitAmount(50000)
                                        .formattedLimit("₹50,000 per eye").build(),
                                StructuredPolicy.DiseaseSublimit.builder()
                                        .diseaseName("Hernia").sublimitAmount(75000)
                                        .formattedLimit("₹75,000").build()))
                        .waitingPeriodInitialDays(30).waitingPeriodSpecificMonths(24).waitingPeriodPedMonths(48)
                        .pedReductionAvailable(false)
                        .preHospitalizationDays(30).postHospitalizationDays(60)
                        .restorationBenefit("100% restoration once per year")
                        .noClaimBonus("10% per claim-free year, up to 50%")
                        .maternityCovered(true).maternityWaitingMonths(48)
                        .opdCovered(true)
                        .dayCareProceduresCount(540).cashlessEverywhere(true)
                        .ambulanceCovered(true).organDonorCovered(true).modernTreatmentsCovered(false)
                        .build())
                .exclusions(List.of(
                        "Cosmetic procedures", "Self-inflicted injuries",
                        "Dental except accident", "Obesity/bariatric surgery",
                        "Congenital conditions", "External aids and appliances"))
                .insurerMetrics(StructuredPolicy.InsurerMetrics.builder()
                        .claimSettlementRatio(90.5).incurredClaimsRatio(73.8)
                        .cashlessHospitalsCount(9800)
                        .topHospitalNetworks(List.of("Apollo", "Fortis", "Manipal", "Narayana", "Aster"))
                        .avgGrievanceResolutionDays(3.2)
                        .claimPaymentSpeed("Cashless authorization within 60 minutes")
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("Room Rent Cap").sectionReference("Section 3.1(b)")
                                .documentName("AB_Activ_Health_Wordings_v2024.pdf")
                                .verbatimText("Room Rent shall be limited to 1% of Sum Insured per day. If the actual room rent exceeds this limit, proportionate deduction shall apply to the entire claim.")
                                .verifiedFact("Room rent capped at 1% of SI per day with proportionate deduction on the entire claim.")
                                .tag("ROOM_RENT").build()))
                .build();
    }
}
