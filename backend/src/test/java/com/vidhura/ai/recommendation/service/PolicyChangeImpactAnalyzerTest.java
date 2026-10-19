package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.PolicyChangeImpactReport;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PolicyChangeImpactAnalyzer Unit Tests")
class PolicyChangeImpactAnalyzerTest {

    private PolicyChangeImpactAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PolicyChangeImpactAnalyzer();
    }

    @Test
    @DisplayName("Should flag room rent cap introduction as a CRITICAL material regression")
    void testDetectRoomRentCappingRegression() {
        StructuredPolicy oldPolicy = StructuredPolicy.builder()
                .policyCode("HDFC_OPTIMA_SECURE")
                .policyName("Optima Secure")
                .provider("HDFC ERGO")
                .uinRevision("V012021")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .roomRentLimit("Single Private AC Room without cap")
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("ROOM_RENT")
                                .sectionReference("Section 4.1")
                                .verbatimText("No capping on room rent applies.")
                                .build()
                ))
                .build();

        StructuredPolicy newDraft = StructuredPolicy.builder()
                .policyCode("HDFC_OPTIMA_SECURE_DRAFT_V022024")
                .policyName("Optima Secure")
                .provider("HDFC ERGO")
                .uinRevision("V022024")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(true)
                        .roomRentLimit("1% of Sum Insured per day")
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("ROOM_RENT")
                                .sectionReference("Clause 3(b)")
                                .verbatimText("Room rent capped at 1% of Sum Insured.")
                                .build()
                ))
                .build();

        PolicyChangeImpactReport report = analyzer.analyzeImpact(oldPolicy, newDraft);

        assertNotNull(report);
        assertTrue(report.isMaterialChangesDetected(), "Room rent cap introduction must be marked as material");
        assertTrue(report.isRequiresSeniorReview(), "Must require senior reviewer sign-off");

        PolicyChangeImpactReport.FactDiff roomDiff = report.getContractualDiffs().stream()
                .filter(d -> "Room Rent Terms".equals(d.getFieldName()))
                .findFirst()
                .orElse(null);

        assertNotNull(roomDiff);
        assertEquals("CRITICAL", roomDiff.getSeverity());
        assertTrue(roomDiff.isMaterial());
        assertTrue(roomDiff.getImpactExplanation().contains("proportionate deductions"));

        // Verify downstream claim & ranking impacts generated
        assertFalse(report.getClaimSimulationImpacts().isEmpty());
        assertTrue(report.isAdvisoryOnly(), "Must be explicitly marked as advisory");
        assertNotNull(report.getAdvisoryDisclaimer());
        assertFalse(report.getBenchmarkSimulations().isEmpty(), "Must contain simulated benchmark results");
        assertEquals(3, report.getBenchmarkSimulations().size());
        assertTrue(report.getRankingScoreImpacts().stream().anyMatch(s -> s.contains("ranking score will drop")));
    }

    @Test
    @DisplayName("Should detect mandatory co-payment increase and compute score impact")
    void testDetectCopayIncrease() {
        StructuredPolicy oldPolicy = StructuredPolicy.builder()
                .policyCode("CARE_SUPREME")
                .policyName("Care Supreme")
                .uinRevision("V012021")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .build();

        StructuredPolicy newDraft = StructuredPolicy.builder()
                .policyCode("CARE_SUPREME_REV")
                .policyName("Care Supreme")
                .uinRevision("V022024")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .copayPercentage(20.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .build();

        PolicyChangeImpactReport report = analyzer.analyzeImpact(oldPolicy, newDraft);

        assertTrue(report.isMaterialChangesDetected());
        assertTrue(report.isRequiresSeniorReview());

        PolicyChangeImpactReport.FactDiff copayDiff = report.getContractualDiffs().stream()
                .filter(d -> "Co-payment Percentage".equals(d.getFieldName()))
                .findFirst()
                .orElse(null);

        assertNotNull(copayDiff);
        assertEquals("0%", copayDiff.getPreviousValue());
        assertEquals("20%", copayDiff.getNewValue());
        assertEquals("CRITICAL", copayDiff.getSeverity());
        assertTrue(copayDiff.getImpactExplanation().contains("out-of-pocket liability"));
    }

    @Test
    @DisplayName("Should detect pre-existing disease waiting period change")
    void testDetectPedWaitingPeriodChange() {
        StructuredPolicy oldPolicy = StructuredPolicy.builder()
                .policyCode("NIVA_REASSURE")
                .policyName("ReAssure 2.0")
                .uinRevision("V012021")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .build();

        StructuredPolicy newDraft = StructuredPolicy.builder()
                .policyCode("NIVA_REASSURE_REV")
                .policyName("ReAssure 2.0")
                .uinRevision("V022024")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(24) // Reduced to 24 months
                        .build())
                .build();

        PolicyChangeImpactReport report = analyzer.analyzeImpact(oldPolicy, newDraft);

        PolicyChangeImpactReport.FactDiff pedDiff = report.getContractualDiffs().stream()
                .filter(d -> "PED Waiting Period".equals(d.getFieldName()))
                .findFirst()
                .orElse(null);

        assertNotNull(pedDiff);
        assertEquals("36 months", pedDiff.getPreviousValue());
        assertEquals("24 months", pedDiff.getNewValue());
        assertTrue(pedDiff.getImpactExplanation().contains("shortened from 36 to 24 months"));
    }

    @Test
    @DisplayName("Should generate baseline impact report for newly introduced product")
    void testBrandNewProductIntroduction() {
        StructuredPolicy newDraft = StructuredPolicy.builder()
                .policyCode("ICICI_ELEVATE")
                .policyName("Elevate")
                .provider("ICICI Lombard")
                .irdaUin("ICIHLIP21167V012021")
                .uinRevision("V012021")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .build();

        PolicyChangeImpactReport report = analyzer.analyzeImpact(null, newDraft);

        assertNotNull(report);
        assertEquals("None (New Product Offering)", report.getPreviousRevision());
        assertTrue(report.isRequiresSeniorReview());
        assertEquals(1, report.getContractualDiffs().size());
        assertEquals("Product Introduction", report.getContractualDiffs().get(0).getFieldName());
    }
}
