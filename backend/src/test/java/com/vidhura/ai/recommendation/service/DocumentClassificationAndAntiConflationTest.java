package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DocumentClassificationAndAntiConflationTest {

    private StructuredPolicyRepository policyRepository;
    private CanonicalProductRegistry canonicalProductRegistry;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private PolicyPublicationService publicationService;

    private static final String NIVA_BUPA_CATALOGUE_TEXT = """
            List of Products Offered 
            Product Name Product UIN Date of launch 
            NBHHLIP27059V022627 
            ReAssure 3.0 8-Oct-26 
            Health Companion NBHHLIP26051V092526 01-Mar-26 
            Xtend NBHHLIA26052V012526 28-Jan-26 
            Rise NBHHLIP25041V012425 14-Feb-25 
            ReAssure 2.0 NBHHLIP27054V032627 19-Jun-26 
            Senior First NBHHLIP27053V022627 10-Apr-26 
            Arogya Sanjeevani, Niva Bupa Health NBHHLIP27056V042627 16-July-26 
            Last Updated on 8th October 2026
            """;

    private static final String UIIC_CATALOGUE_TEXT = """
            LIST OF RETAIL PRODUCTS 
            Sl. No. Name of Product Product UIN LoB 
            1 Agricultural Pumpset Insurance IRDAN545RP0001V01199900 Miscellaneous 
            2 Agricultural Implements Policy IRDAN545RP0002V01199900 Miscellaneous 
            3 Animal Driven Cart Insurance IRDAN545RP0003V01199900 Miscellaneous 
            6 Cattle Insurance IRDAN545RP0006V01199900 Miscellaneous 
            17 Gramin Accident Policy Individual IRDAN545RP0017V01199900 Miscellaneous 
            28 Family Medicare Policy IRDAI/HLT/UII/P-H/V.1/20/2021-22 Health 
            """;

    private static final String GENUINE_NIVA_BUPA_REASSURE_POLICY_WORDING = """
            NIVA BUPA HEALTH INSURANCE COMPANY LIMITED
            REASSURE 2.0 - POLICY WORDINGS
            IRDAI Registration UIN: NBHHLIP27054V032627
            
            SECTION 1: DEFINITIONS & ELIGIBILITY
            Entry age for adults: 18 years to 65 years. Children eligible from 91 days to 25 years.
            
            SECTION 2: BENEFITS COVERED
            Section 2.1: In-patient Hospitalization & Room Rent Limit
            We will cover reasonable and customary medical expenses for in-patient hospitalization.
            There is no room rent capping. Single Private AC Room or higher room category is eligible
            without any proportionate deductions on doctor, surgeon, or diagnostic expenses.
            
            Section 2.2: Pre and Post Hospitalization Expenses
            Pre-hospitalization expenses covered for 60 days. Post-hospitalization expenses covered for 180 days.
            
            Section 2.3: ReAssure Forever / Restoration Benefit
            Unlimited 100% restoration of Sum Insured for same or different illness throughout the policy year.
            
            Section 2.4: Cumulative Bonus Booster
            100% cumulative bonus per year up to maximum 500% of sum insured.
            
            SECTION 3: WAITING PERIODS
            Section 3.1: 30-day initial waiting period for all illnesses except accidental trauma.
            Section 3.2: 24 months specific illness waiting period for specified procedures.
            Section 3.3: Pre-Existing Disease (PED) Waiting Period
            Pre-existing conditions, illnesses, and diseases (PED) are covered after 36 months of continuous coverage.
            
            SECTION 4: CO-PAYMENT
            Section 4.1: Nil Co-payment. Zero copay across network hospitals. No mandatory copay applies.
            
            SECTION 5: GENERAL EXCLUSIONS
            Investigation & evaluation only admissions without active treatment;
            Cosmetic or plastic surgery;
            Non-prescription dietary supplements and vitamins;
            Intentional self-inflicted injury;
            Non-medical hospital consumables classified under IRDAI List 1.
            """;

    @BeforeEach
    void setUp() {
        policyRepository = Mockito.mock(StructuredPolicyRepository.class);
        canonicalProductRegistry = new CanonicalProductRegistry();
        discoveryPipelineService = new PolicyDiscoveryPipelineService(policyRepository, canonicalProductRegistry);
        publicationService = new PolicyPublicationService(policyRepository, null, canonicalProductRegistry);

        when(policyRepository.save(any(StructuredPolicy.class))).thenAnswer(invocation -> {
            StructuredPolicy p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId("mock-id-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            }
            return p;
        });
    }

    @Test
    @DisplayName("Classifies Niva Bupa 'List of Products Offered' as PRODUCT_CATALOGUE")
    void testClassifyNivaBupaCatalogue() {
        String classification = PolicyDiscoveryPipelineService.classifyDocument(
                NIVA_BUPA_CATALOGUE_TEXT, "List of Products Offered_22nd November 2023.pdf");
        assertEquals("PRODUCT_CATALOGUE", classification,
                "Niva Bupa product schedule must be classified as PRODUCT_CATALOGUE");
    }

    @Test
    @DisplayName("Classifies UIIC 'List-of-Retail-Products' as PRODUCT_CATALOGUE")
    void testClassifyUiicCatalogue() {
        String classification = PolicyDiscoveryPipelineService.classifyDocument(
                UIIC_CATALOGUE_TEXT, "List-of-Retail-Products.pdf");
        assertEquals("PRODUCT_CATALOGUE", classification,
                "UIIC product schedule must be classified as PRODUCT_CATALOGUE");
    }

    @Test
    @DisplayName("Classifies full contractual text as POLICY_WORDING")
    void testClassifyGenuinePolicyWording() {
        String classification = PolicyDiscoveryPipelineService.classifyDocument(
                GENUINE_NIVA_BUPA_REASSURE_POLICY_WORDING, "Niva_Bupa_ReAssure_2.0_Policy_Wordings.pdf");
        assertEquals("POLICY_WORDING", classification,
                "Comprehensive policy contract must be classified as POLICY_WORDING");
    }

    @Test
    @DisplayName("Anti-conflation guard: Multi-product catalogue cannot produce high contract confidence or active policy")
    void testCatalogueIngestionAntiConflationGuard() {
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("List of Products Offered_22nd November 2023.pdf")
                .documentUrl("https://www.nivabupa.com/content/dam/nivabupa/PDF/List%20of%20Products%20Offered_22nd%20November%202023.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .rawPolicyText(NIVA_BUPA_CATALOGUE_TEXT)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        assertEquals("PRODUCT_CATALOGUE", result.getDocumentClassification());
        assertEquals(0.0, result.getContractConfidenceScore(), "Product catalogue must have contract confidence score of 0.0");
        assertTrue(result.getContractUncertainFields().contains("MULTI_PRODUCT_CATALOGUE_DISCOVERY_INDEX"),
                "Must flag MULTI_PRODUCT_CATALOGUE_DISCOVERY_INDEX");
        assertFalse(result.isReadyForPublication(), "Multi-product catalogue cannot be marked ready for publication");
    }

    @Test
    @DisplayName("Publication Gate: Attempting to approve a PRODUCT_CATALOGUE draft throws IllegalStateException")
    void testCataloguePublicationBlockedByGate() {
        StructuredPolicy catalogueDraft = StructuredPolicy.builder()
                .id("pol-cat-01")
                .policyCode("NIVA_BUPA_CATALOGUE_DRAFT")
                .policyName("Niva Bupa Product Catalogue")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .irdaUin("NBHHLIP27059V022627")
                .documentClassification("PRODUCT_CATALOGUE")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .active(false)
                .contractConfidenceScore(0.0)
                .contractUncertainFields(List.of("MULTI_PRODUCT_CATALOGUE_DISCOVERY_INDEX"))
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("Unverified Room Category")
                        .waitingPeriodPedMonths(36)
                        .copayPercentage(0.0)
                        .build())
                .build();

        when(policyRepository.findById("pol-cat-01")).thenReturn(Optional.of(catalogueDraft));

        PolicyDiscoveryDto.PolicyVerificationRequest approvalRequest =
                PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                        .decision("APPROVE")
                        .reviewerName("ComplianceOfficer@vidhura.ai")
                        .reviewerNotes("Attempting approval of product catalogue")
                        .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            publicationService.verifyAndPublish("pol-cat-01", approvalRequest);
        });

        assertTrue(ex.getMessage().contains("PRODUCT_CATALOGUE"),
                "Error message must state that product catalogue documents cannot be published as policies: " + ex.getMessage());
    }

    @Test
    @DisplayName("Catalogue parser extracts individual product entries for targeted policy discovery")
    void testCatalogueEntryExtraction() {
        List<PolicySourceMonitor.DiscoveredCatalogueProduct> products =
                PolicySourceMonitor.parseCatalogueProducts(NIVA_BUPA_CATALOGUE_TEXT);

        assertFalse(products.isEmpty(), "Should extract product entries from catalogue");
        assertTrue(products.stream().anyMatch(p -> p.getIrdaUin().equalsIgnoreCase("NBHHLIP27054V032627")),
                "Must extract ReAssure 2.0 UIN (NBHHLIP27054V032627)");
        assertTrue(products.stream().anyMatch(p -> p.getIrdaUin().equalsIgnoreCase("NBHHLIP26051V092526")),
                "Must extract Health Companion UIN (NBHHLIP26051V092526)");
        assertTrue(products.stream().anyMatch(p -> p.getIrdaUin().equalsIgnoreCase("NBHHLIP27053V022627")),
                "Must extract Senior First UIN (NBHHLIP27053V022627)");
    }

    @Test
    @DisplayName("Genuine policy wording achieves 100% contract confidence and valid clause citations")
    void testGenuinePolicyWordingIngestion() {
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Niva_Bupa_ReAssure_2.0_Policy_Wordings.pdf")
                .documentUrl("https://www.nivabupa.com/content/dam/nivabupa/PDF/reassure-2.0-policy-wordings.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .policyName("ReAssure 2.0")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .irdaUin("NBHHLIP27054V032627")
                .uinRevision("V032627")
                .rawPolicyText(GENUINE_NIVA_BUPA_REASSURE_POLICY_WORDING)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        assertEquals("POLICY_WORDING", result.getDocumentClassification());
        assertEquals(100.0, result.getContractConfidenceScore(), "Genuine contractual wording must have 100% confidence");
        assertTrue(result.getContractUncertainFields().isEmpty(), "No contract uncertainties should remain");
        assertEquals("EXTRACTION_PENDING_REVIEW", result.getVerificationStatus(), "Server gate must stage as pending review");
        assertFalse(result.isReadyForPublication(), "Mandatory human review gate enforced");
    }

    @Test
    @DisplayName("Fail-Closed: Arbitrary marketing brochure/flyer is classified as UNKNOWN and rejected for publication")
    void testMarketingFlyerClassifiedAsUnknownAndBlocked() {
        String marketingFlyerText = """
                SAVE UP TO 25% ON HEALTH INSURANCE TODAY!
                Comprehensive medical coverage for you and your family.
                Instant policy issuance in 2 minutes. Cashless network across 10,000+ hospitals.
                Special discount for young adults and corporate employees.
                Call 1800-000-0000 or visit our website to apply now.
                """;

        String classification = PolicyDiscoveryPipelineService.classifyDocument(
                marketingFlyerText, "Health_Insurance_Marketing_Brochure.pdf");
        assertEquals("UNKNOWN", classification, "Marketing brochure lacking positive contractual evidence must classify as UNKNOWN");

        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Health_Insurance_Marketing_Brochure.pdf")
                .documentUrl("https://www.nivabupa.com/content/dam/nivabupa/PDF/brochure.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .rawPolicyText(marketingFlyerText)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        assertEquals("UNKNOWN", result.getDocumentClassification());
        assertEquals(0.0, result.getContractConfidenceScore(), "UNKNOWN document must receive 0% contract confidence");
        assertTrue(result.getContractUncertainFields().contains("UNCLASSIFIED_NON_CONTRACTUAL_DOCUMENT"),
                "Must flag UNCLASSIFIED_NON_CONTRACTUAL_DOCUMENT uncertainty");
        assertFalse(result.isReadyForPublication(), "UNKNOWN document cannot be ready for publication");

        // Verify Publication Gate rejects approval of UNKNOWN document
        StructuredPolicy unknownDraft = StructuredPolicy.builder()
                .id("pol-unk-01")
                .policyCode("UNKNOWN_BROCHURE_DRAFT")
                .policyName("Unclassified Marketing Brochure")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .irdaUin("NBHHLIP27054V032627")
                .documentClassification("UNKNOWN")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .active(false)
                .contractConfidenceScore(0.0)
                .contractUncertainFields(List.of("UNCLASSIFIED_NON_CONTRACTUAL_DOCUMENT"))
                .contractTerms(StructuredPolicy.ContractTerms.builder().roomRentLimit("Single Private Room").build())
                .build();

        when(policyRepository.findById("pol-unk-01")).thenReturn(Optional.of(unknownDraft));

        PolicyDiscoveryDto.PolicyVerificationRequest approvalReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Auditor@vidhura.ai")
                .reviewerNotes("Attempting approval of unknown marketing document")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                publicationService.verifyAndPublish("pol-unk-01", approvalReq));
        assertTrue(ex.getMessage().contains("UNKNOWN"), "Error message must state document is UNKNOWN: " + ex.getMessage());
    }

    @Test
    @DisplayName("Fail-Closed: Customer Information Sheet (CIS) is blocked from policy publication")
    void testCustomerInformationSheetClassifiedAndBlocked() {
        String cisText = """
                CUSTOMER INFORMATION SHEET (CIS)
                Policy: Niva Bupa ReAssure 2.0
                UIN: NBHHLIP27054V032627
                Description of benefits: In-patient hospitalization, pre and post hospitalisation.
                This is a summary document under IRDAI Master Circular guidelines and does not constitute the contract of insurance.
                """;

        String classification = PolicyDiscoveryPipelineService.classifyDocument(
                cisText, "ReAssure_2.0_Customer_Information_Sheet.pdf");
        assertEquals("CUSTOMER_INFORMATION_SHEET", classification);

        StructuredPolicy cisDraft = StructuredPolicy.builder()
                .id("pol-cis-01")
                .policyCode("NIVA_BUPA_CIS")
                .policyName("ReAssure 2.0 CIS")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .irdaUin("NBHHLIP27054V032627")
                .documentClassification("CUSTOMER_INFORMATION_SHEET")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .active(false)
                .contractConfidenceScore(0.0)
                .contractUncertainFields(List.of("CUSTOMER_INFORMATION_SHEET_DISCLOSURE_ONLY"))
                .contractTerms(StructuredPolicy.ContractTerms.builder().roomRentLimit("Single Private Room").build())
                .build();

        when(policyRepository.findById("pol-cis-01")).thenReturn(Optional.of(cisDraft));

        PolicyDiscoveryDto.PolicyVerificationRequest req = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Auditor@vidhura.ai")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                publicationService.verifyAndPublish("pol-cis-01", req));
        assertTrue(ex.getMessage().contains("CUSTOMER_INFORMATION_SHEET") || ex.getMessage().contains("Contractual document validation failed"),
                "CIS must be blocked from publication: " + ex.getMessage());
    }

    @Test
    @DisplayName("Fail-Closed: Regulatory Form NL-25 filing is blocked from policy publication")
    void testRegulatoryFilingClassifiedAndBlocked() {
        String nl25Text = """
                FORM NL-25: QUARTERLY PUBLIC DISCLOSURE OF PRODUCTS
                Name of Insurer: Niva Bupa Health Insurance Company Limited
                Registration No. 145 dated 15-02-2010
                Schedule of Health Products Filed with IRDAI
                """;

        String classification = PolicyDiscoveryPipelineService.classifyDocument(
                nl25Text, "Public_Disclosure_Form_NL-25.pdf");
        assertEquals("REGULATORY_FILING", classification);

        StructuredPolicy nl25Draft = StructuredPolicy.builder()
                .id("pol-nl25-01")
                .policyCode("NIVA_BUPA_NL25")
                .policyName("Form NL-25 Disclosure")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .irdaUin("NBHHLIP27054V032627")
                .documentClassification("REGULATORY_FILING")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .active(false)
                .contractConfidenceScore(0.0)
                .contractUncertainFields(List.of("REGULATORY_FILING_DISCLOSURE_ONLY"))
                .contractTerms(StructuredPolicy.ContractTerms.builder().roomRentLimit("Single Private Room").build())
                .build();

        when(policyRepository.findById("pol-nl25-01")).thenReturn(Optional.of(nl25Draft));

        PolicyDiscoveryDto.PolicyVerificationRequest req = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Auditor@vidhura.ai")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                publicationService.verifyAndPublish("pol-nl25-01", req));
        assertTrue(ex.getMessage().contains("REGULATORY_FILING") || ex.getMessage().contains("Contractual document validation failed"),
                "Regulatory filing must be blocked: " + ex.getMessage());
    }

    @Test
    @DisplayName("Publication Gate: Rejects policy publication if supporting evidence has missing verbatim text")
    void testPublicationRejectsEvidenceWithMissingVerbatimText() {
        StructuredPolicy policyMissingVerbatim = StructuredPolicy.builder()
                .id("pol-missing-verbatim-01")
                .policyCode("NIVA_BUPA_INCOMPLETE_EVIDENCE")
                .policyName("ReAssure 2.0 Incomplete Evidence")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .irdaUin("NBHHLIP27054V032627")
                .uinRevision("V032627")
                .documentClassification("POLICY_WORDING")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .active(false)
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("Single Private Room")
                        .waitingPeriodPedMonths(36)
                        .copayPercentage(0.0)
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("In-patient Hospitalization & Room Rent Limit")
                                .sectionReference("Section 2.1")
                                .verbatimText("") // Blank verbatim text!
                                .build()
                ))
                .build();

        when(policyRepository.findById("pol-missing-verbatim-01")).thenReturn(Optional.of(policyMissingVerbatim));

        PolicyDiscoveryDto.PolicyVerificationRequest req = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Auditor@vidhura.ai")
                .reviewerNotes("Attempting approval with incomplete evidence")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                publicationService.verifyAndPublish("pol-missing-verbatim-01", req));
        assertTrue(ex.getMessage().contains("missing verbatim contractual text"),
                "Must reject publication when verbatim text is missing: " + ex.getMessage());
    }
}
