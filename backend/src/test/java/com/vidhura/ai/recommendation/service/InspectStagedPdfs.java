package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.service.PdfParsingService;
import org.junit.jupiter.api.Test;

public class InspectStagedPdfs {

    @Test
    void inspectPdfs() throws Exception {
        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher();
        PdfParsingService parser = new PdfParsingService();

        // 1. Inspect UIIC Family Medicare Wordings
        String uiicFmpUrl = "https://uiic.co.in/web/sites/default/files/uploads/downloadcenter/FMP_wordings.pdf";
        PolicyDocumentFetcher.FetchResult r1 = fetcher.fetchDocument(uiicFmpUrl, null, null);
        System.out.println("=== UIIC FMP STATUS: " + r1.getStatus() + ", HTTP " + r1.getHttpStatusCode() + " ===");
        if (r1.getStatus() == PolicyDocumentFetcher.FetchStatus.FETCHED) {
            System.out.println("Size: " + r1.getFileSizeBytes() + " bytes, SHA-256: " + r1.getSha256Checksum());
            String text = parser.extractText(r1.getDocumentBytes());
            System.out.println("Length: " + text.length());
            OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin = OfficialInsurerDiscoveryAdapter.extractUin(text);
            System.out.println("UIN Info: " + (uin != null ? uin.getUin() + " (Rev: " + uin.getRevision() + ")" : "none"));
            String classification = PolicyDiscoveryPipelineService.classifyDocument(text, "FMP_wordings.pdf");
            System.out.println("Classification: " + classification);
        }

        // 2. Inspect New India Mediclaim
        String niaUrl = "https://www.newindia.co.in/assets/docs/know-more/health/new-india-mediclaim-policy/Policy%20%20Clause_%20New%20India%20Mediclaim%20Policy%20.pdf";
        PolicyDocumentFetcher.FetchResult r2 = fetcher.fetchDocument(niaUrl, null, null);
        System.out.println("=== NEW INDIA MEDICLAIM STATUS: " + r2.getStatus() + ", HTTP " + r2.getHttpStatusCode() + " ===");
        if (r2.getStatus() == PolicyDocumentFetcher.FetchStatus.FETCHED) {
            System.out.println("Size: " + r2.getFileSizeBytes() + " bytes, SHA-256: " + r2.getSha256Checksum());
            String text = parser.extractText(r2.getDocumentBytes());
            System.out.println("Length: " + text.length());
            OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin = OfficialInsurerDiscoveryAdapter.extractUin(text);
            System.out.println("UIN Info: " + (uin != null ? uin.getUin() + " (Rev: " + uin.getRevision() + ")" : "none"));
            String classification = PolicyDiscoveryPipelineService.classifyDocument(text, "Policy_Clause_New_India_Mediclaim.pdf");
            System.out.println("Classification: " + classification);
            System.out.println("First 500 chars:\n" + (text.length() > 500 ? text.substring(0, 500) : text));
        }

        // 3. Inspect New India Premier Mediclaim
        String niaPremierUrl = "https://newindia.co.in/assets/docs/know-more/health/new-premier-mediclaim-claim-policy/Policy%20Clause%20New%20India%20Premier%20Mediclaim%20(wef%201st%20October%202021).pdf";
        PolicyDocumentFetcher.FetchResult r3 = fetcher.fetchDocument(niaPremierUrl, null, null);
        System.out.println("=== NEW INDIA PREMIER STATUS: " + r3.getStatus() + ", HTTP " + r3.getHttpStatusCode() + " ===");
        if (r3.getStatus() == PolicyDocumentFetcher.FetchStatus.FETCHED) {
            System.out.println("Size: " + r3.getFileSizeBytes() + " bytes, SHA-256: " + r3.getSha256Checksum());
            String text = parser.extractText(r3.getDocumentBytes());
            System.out.println("Length: " + text.length());
            OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uin = OfficialInsurerDiscoveryAdapter.extractUin(text);
            System.out.println("UIN Info: " + (uin != null ? uin.getUin() + " (Rev: " + uin.getRevision() + ")" : "none"));
            String classification = PolicyDiscoveryPipelineService.classifyDocument(text, "Policy_Clause_Premier_Mediclaim.pdf");
            System.out.println("Classification: " + classification);
        }

        // Test discoverAndIngest pipeline with real documents
        com.vidhura.ai.recommendation.repository.StructuredPolicyRepository mockRepo = org.mockito.Mockito.mock(com.vidhura.ai.recommendation.repository.StructuredPolicyRepository.class);
        org.mockito.Mockito.when(mockRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        CanonicalProductRegistry registry = new CanonicalProductRegistry();
        PolicyDiscoveryPipelineService pipeline = new PolicyDiscoveryPipelineService(mockRepo, registry);

        if (r1.getStatus() == PolicyDocumentFetcher.FetchStatus.FETCHED) {
            String t1 = parser.extractText(r1.getDocumentBytes());
            com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto.PolicyDiscoveryRequest req1 =
                    com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                            .documentName("FMP_wordings.pdf")
                            .documentUrl(uiicFmpUrl)
                            .sourceType("POLICY_WORDING_PDF")
                            .policyName("Family Medicare Policy")
                            .provider("United India Insurance Company Limited")
                            .rawPolicyText(t1)
                            .build();
            com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto.PolicyIngestionResultDto res1 = pipeline.discoverAndIngest(req1);
            System.out.println("\n=== INGESTION RES 1 (UIIC FMP) ===");
            System.out.println("UIN: " + res1.getIrdaUin() + ", Rev: " + res1.getUinRevision());
            System.out.println("Classification: " + res1.getDocumentClassification());
            System.out.println("Confidence: " + res1.getContractConfidenceScore() + "%");
            System.out.println("Status: " + res1.getVerificationStatus());
            System.out.println("Uncertainties: " + res1.getContractUncertainFields());

            // Print lines matching room, ped, copay
            for (String line : t1.split("\n")) {
                String l = line.toLowerCase();
                if (l.contains("room rent") || l.contains("room, boarding") || l.contains("pre-existing") || l.contains("co-pay") || l.contains("copay")) {
                    System.out.println("  [UIIC MATCH] " + line.trim());
                }
            }
        }

        if (r2.getStatus() == PolicyDocumentFetcher.FetchStatus.FETCHED) {
            String t2 = parser.extractText(r2.getDocumentBytes());
            com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto.PolicyDiscoveryRequest req2 =
                    com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                            .documentName("Policy_Clause_New_India_Mediclaim.pdf")
                            .documentUrl(niaUrl)
                            .sourceType("POLICY_WORDING_PDF")
                            .policyName("New India Mediclaim Policy")
                            .provider("The New India Assurance Company Limited")
                            .rawPolicyText(t2)
                            .build();
            com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto.PolicyIngestionResultDto res2 = pipeline.discoverAndIngest(req2);
            System.out.println("\n=== INGESTION RES 2 (NEW INDIA MEDICLAIM) ===");
            System.out.println("UIN: " + res2.getIrdaUin() + ", Rev: " + res2.getUinRevision());
            System.out.println("Classification: " + res2.getDocumentClassification());
            System.out.println("Confidence: " + res2.getContractConfidenceScore() + "%");
            System.out.println("Status: " + res2.getVerificationStatus());
            System.out.println("Uncertainties: " + res2.getContractUncertainFields());

            for (String line : t2.split("\n")) {
                String l = line.toLowerCase();
                if (l.contains("room rent") || l.contains("room & boarding") || l.contains("room and boarding") || l.contains("co-payment") || l.contains("copay") || l.contains("co payment")) {
                    System.out.println("  [NIA MEDICLAIM MATCH] " + line.trim());
                }
            }
        }

        if (r3.getStatus() == PolicyDocumentFetcher.FetchStatus.FETCHED) {
            String t3 = parser.extractText(r3.getDocumentBytes());
            com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto.PolicyDiscoveryRequest req3 =
                    com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                            .documentName("Policy_Clause_Premier_Mediclaim.pdf")
                            .documentUrl(niaPremierUrl)
                            .sourceType("POLICY_WORDING_PDF")
                            .policyName("New India Premier Mediclaim")
                            .provider("The New India Assurance Company Limited")
                            .rawPolicyText(t3)
                            .build();
            com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto.PolicyIngestionResultDto res3 = pipeline.discoverAndIngest(req3);
            System.out.println("\n=== INGESTION RES 3 (NEW INDIA PREMIER) ===");
            System.out.println("UIN: " + res3.getIrdaUin() + ", Rev: " + res3.getUinRevision());
            System.out.println("Classification: " + res3.getDocumentClassification());
            System.out.println("Confidence: " + res3.getContractConfidenceScore() + "%");
            System.out.println("Status: " + res3.getVerificationStatus());
            System.out.println("Uncertainties: " + res3.getContractUncertainFields());

            for (String line : t3.split("\n")) {
                String l = line.toLowerCase();
                if (l.contains("room rent") || l.contains("room & boarding") || l.contains("room and boarding") || l.contains("co-payment") || l.contains("copay") || l.contains("co payment")) {
                    System.out.println("  [NIA PREMIER MATCH] " + line.trim());
                }
            }
        }
    }
}
