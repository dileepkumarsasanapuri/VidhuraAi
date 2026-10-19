package com.vidhura.ai.recommendation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import javax.net.ssl.*;

@DisplayName("Test Real PDF Download from Official Insurer and IRDAI Endpoints")
public class TestRealPdfDownload {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @Test
    @DisplayName("Verify real PDF download and %PDF- magic bytes from reachable official portals")
    void testRealPdfDownloads() throws Exception {
        String[] testPdfs = {
            "https://uiic.co.in/web/sites/default/files/basic-documents/List-of-Retail-Products.pdf",
            "https://irdai.gov.in/documents/37343/0/Guidelines+on+Standard+Individual+Health+Insurance+Product.pdf/369dff4f-e97d-4eba-85b1-9a0e0080ff58?t=1633496923225",
            "https://www.nivabupa.com/content/dam/nivabupa/PDF/List%20of%20Products%20Offered_22nd%20November%202023.pdf"
        };

        PolicyDocumentFetcher fetcher = new PolicyDocumentFetcher();
        int successfulDownloads = 0;

        for (String pdfUrl : testPdfs) {
            System.out.println("\n-----------------------------------------------------------");
            System.out.println("TESTING REAL PDF DOWNLOAD: " + pdfUrl);
            try {
                PolicyDocumentFetcher.FetchResult res = fetcher.fetchDocument(pdfUrl, null, null);
                System.out.println("Status: " + res.getStatus());
                System.out.println("HTTP Code: " + res.getHttpStatusCode());
                System.out.println("File Size: " + res.getFileSizeBytes() + " bytes");
                System.out.println("SHA-256: " + res.getSha256Checksum());
                System.out.println("Content-Type: " + res.getContentType());
                if (res.getDocumentBytes() != null && res.getDocumentBytes().length >= 10) {
                    byte[] b = res.getDocumentBytes();
                    String header = new String(b, 0, Math.min(b.length, 30));
                    System.out.println("Magic bytes: " + header);
                    boolean isPdf = fetcher.isPdfContent(b);
                    System.out.println("isPdfContent: " + isPdf);
                    if (isPdf && res.getStatus() == PolicyDocumentFetcher.FetchStatus.FETCHED) {
                        successfulDownloads++;
                        try {
                            com.vidhura.ai.service.PdfParsingService parser = new com.vidhura.ai.service.PdfParsingService();
                            String text = parser.extractText(b);
                            System.out.println("Extracted text length: " + text.length() + " chars");
                            OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uinInfo = OfficialInsurerDiscoveryAdapter.extractUin(text);
                            if (uinInfo != null) {
                                System.out.println("Extracted UIN: " + uinInfo.getUin() + " (Format=" + uinInfo.getFormat() + ", Revision=" + uinInfo.getRevision() + ")");
                            } else {
                                System.out.println("No UIN found in first parse pass.");
                            }
                        } catch (Exception pe) {
                            System.out.println("Text parsing error: " + pe.getMessage());
                        }
                    }
                }
                if (res.getErrorMessage() != null) {
                    System.out.println("Error: " + res.getErrorMessage());
                }
            } catch (Exception e) {
                System.out.println("Exception: " + e.getMessage());
                e.printStackTrace(System.out);
            }
        }

        System.out.println("\n===========================================================");
        System.out.println("Total successful real official PDF downloads: " + successfulDownloads);
        assertTrue(successfulDownloads >= 1, "At least one real official PDF must be downloaded successfully");
    }
}

