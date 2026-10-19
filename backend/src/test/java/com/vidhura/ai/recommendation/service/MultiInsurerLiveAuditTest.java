package com.vidhura.ai.recommendation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Phase 1 Audit: Controlled live probe of every configured insurer source URL.
 * Records HTTP status, size, SHA-256, document classification, and error reason.
 */
public class MultiInsurerLiveAuditTest {

    private static final Logger log = LoggerFactory.getLogger(MultiInsurerLiveAuditTest.class);

    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    public record InsurerProbeTarget(
            String insurerId,
            String insurerName,
            String productName,
            String officialUrl,
            String configuredDocumentType
    ) {}

    @Test
    @DisplayName("Phase 1: Live Audit across all configured insurer endpoints")
    void testAuditAllConfiguredInsurersLive() {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher(false);

        List<InsurerProbeTarget> targets = List.of(
                // 1. United India Insurance (UIIC)
                new InsurerProbeTarget("UIIC_CATALOGUE", "United India Insurance Co. Ltd.", "Retail Products Schedule",
                        "https://uiic.co.in/web/sites/default/files/basic-documents/List-of-Retail-Products.pdf", "PRODUCT_CATALOGUE"),
                new InsurerProbeTarget("UIIC_MEDICARE", "United India Insurance Co. Ltd.", "Family Medicare 2021",
                        "https://uiic.co.in/sites/default/files/uploads/download-forms/family-medicare-policy-wording.pdf", "POLICY_WORDING"),

                // 2. Niva Bupa Health Insurance
                new InsurerProbeTarget("NIVA_CATALOGUE", "Niva Bupa Health Insurance Co. Ltd.", "Products Offered Schedule",
                        "https://www.nivabupa.com/content/dam/nivabupa/PDF/List%20of%20Products%20Offered_22nd%20November%202023.pdf", "PRODUCT_CATALOGUE"),
                new InsurerProbeTarget("NIVA_REASSURE", "Niva Bupa Health Insurance Co. Ltd.", "ReAssure 2.0 Wordings",
                        "https://www.nivabupa.com/content/dam/nivabupa/pdf/reassure-2-0-policy-wording.pdf", "POLICY_WORDING"),

                // 3. HDFC ERGO General Insurance
                new InsurerProbeTarget("HDFC_OPTIMA", "HDFC ERGO General Insurance Co. Ltd.", "Optima Secure Wordings",
                        "https://www.hdfcergo.com/docs/default-source/downloads/policy-wordings/optima-secure-policy-wording.pdf", "POLICY_WORDING"),
                new InsurerProbeTarget("HDFC_KOTI", "HDFC ERGO General Insurance Co. Ltd.", "My:Health Koti Suraksha",
                        "https://www.hdfcergo.com/docs/default-source/downloads/policy-wordings/myhealth-koti-suraksha-policy-wording.pdf", "POLICY_WORDING"),

                // 4. Star Health and Allied Insurance
                new InsurerProbeTarget("STAR_COMP", "Star Health and Allied Insurance Co. Ltd.", "Star Comprehensive Insurance",
                        "https://www.starhealth.in/sites/default/files/star-comprehensive-policy-wording.pdf", "POLICY_WORDING"),
                new InsurerProbeTarget("STAR_RED_CARPET", "Star Health and Allied Insurance Co. Ltd.", "Senior Citizens Red Carpet",
                        "https://www.starhealth.in/sites/default/files/senior-citizens-red-carpet-policy-wording.pdf", "POLICY_WORDING"),

                // 5. Care Health Insurance
                new InsurerProbeTarget("CARE_SUPREME", "Care Health Insurance Ltd.", "Care Supreme Policy Terms",
                        "https://www.careinsurance.com/product/care-supreme-policy-terms.pdf", "POLICY_WORDING"),

                // 6. ICICI Lombard General Insurance
                new InsurerProbeTarget("ICICI_ELEVATE", "ICICI Lombard General Insurance Co. Ltd.", "Elevate Policy Wordings",
                        "https://www.icicilombard.com/docs/default-source/health-insurance/elevate-policy-wordings.pdf", "POLICY_WORDING"),

                // 7. Bajaj Allianz General Insurance
                new InsurerProbeTarget("BAJAJ_GUARD", "Bajaj Allianz General Insurance Co. Ltd.", "Health Guard Policy Terms",
                        "https://www.bajajallianz.com/content/dam/bagic/pdf/health-guard-policy-wordings.pdf", "POLICY_WORDING"),

                // 8. Tata AIG General Insurance
                new InsurerProbeTarget("TATA_MEDICARE", "Tata AIG General Insurance Co. Ltd.", "MediCare Official Wordings",
                        "https://www.tataaig.com/content/dam/tagic/pdf/consumer/health/medicare-policy-wordings.pdf", "POLICY_WORDING"),

                // 9. National Insurance
                new InsurerProbeTarget("NIC_MEDICLAIM", "National Insurance Co. Ltd.", "National Mediclaim Policy Terms",
                        "https://nationalinsurance.nic.co.in/sites/default/files/national-mediclaim-policy-wording.pdf", "POLICY_WORDING"),

                // 10. IRDAI Regulator
                new InsurerProbeTarget("IRDAI_STANDARD", "Insurance Regulatory and Development Authority of India", "Standard Health Product Guidelines",
                        "https://irdai.gov.in/documents/37343/0/Guidelines+on+Standard+Individual+Health+Insurance+Product.pdf/369dff4f-e97d-4eba-85b1-9a0e0080ff58?t=1633496923225", "REGULATORY_FILING")
        );

        System.out.println("\n==========================================================================================");
        System.out.println("PHASE 1 LIVE AUDIT MATRIX: ALL CONFIGURED INSURER OFFICIAL SOURCE URLS");
        System.out.println("==========================================================================================");

        com.vidhura.ai.service.PdfParsingService parser = new com.vidhura.ai.service.PdfParsingService();

        for (InsurerProbeTarget target : targets) {
            System.out.println("\n-----------------------------------------------------------");
            System.out.printf("TARGET: [%s] %s - %s%n", target.insurerId(), target.insurerName(), target.productName());
            System.out.println("URL: " + target.officialUrl());

            try {
                PolicyDocumentFetcher.FetchResult res = fetcher.fetchDocument(target.officialUrl(), null, null);
                System.out.println("Fetch Status: " + res.getStatus());
                System.out.println("HTTP Response Code: " + res.getHttpStatusCode());
                System.out.println("Content-Type: " + res.getContentType());
                System.out.println("File Size: " + res.getFileSizeBytes() + " bytes");
                System.out.println("SHA-256: " + res.getSha256Checksum());

                if (res.getStatus() == PolicyDocumentFetcher.FetchStatus.FETCHED && res.getDocumentBytes() != null) {
                    boolean isPdf = fetcher.isPdfContent(res.getDocumentBytes());
                    System.out.println("Valid %PDF- Signature: " + isPdf);

                    String text = "";
                    try {
                        text = parser.extractText(res.getDocumentBytes());
                        System.out.println("Extracted Text Length: " + text.length() + " chars");
                    } catch (Exception e) {
                        System.out.println("PDF Text Extraction failed: " + e.getMessage());
                    }

                    String classification = PolicyDiscoveryPipelineService.classifyDocument(text, target.officialUrl());
                    System.out.println("Document Classification: " + classification);

                    OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin = OfficialInsurerDiscoveryAdapter.extractUin(text);
                    if (uin != null) {
                        System.out.printf("Extracted Statutory UIN: %s (Format=%s, Rev=%s)%n",
                                uin.getUin(), uin.getFormat(), uin.getRevision());
                    } else {
                        System.out.println("Extracted Statutory UIN: None found in direct text pass");
                    }
                } else {
                    System.out.println("Failure / Error Diagnostic: " + res.getErrorMessage());
                }
            } catch (Exception e) {
                System.out.println("Exception during fetch: " + e.getMessage());
            }
        }
        System.out.println("\n==========================================================================================");
        System.out.println("PHASE 1 LIVE AUDIT PROBE COMPLETED");
        System.out.println("==========================================================================================");
    }
}
