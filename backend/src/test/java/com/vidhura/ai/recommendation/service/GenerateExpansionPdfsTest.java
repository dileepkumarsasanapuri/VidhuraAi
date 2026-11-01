package com.vidhura.ai.recommendation.service;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateExpansionPdfsTest {

    @org.junit.jupiter.api.Disabled("Utility generator for expansion PDFs; should not run in automated regression")
    @Test
    void generateAllExpansionPdfs() throws Exception {
        File policyDir = new File("src/main/resources/evidence/policies/");
        policyDir.mkdirs();
        File storageDir = new File("data/evidence/persistent_storage/");
        storageDir.mkdirs();

        System.out.println("=========================================================================");
        System.out.println("PHASE 5.5: GENERATING AUTHENTIC POLICY WORDING PDFS FOR EXPANSION COHORT");
        System.out.println("=========================================================================");

        for (PolicyDocumentArtifactGenerator.PolicyDocSpec spec : PolicyDocumentArtifactGenerator.EXPANSION_SPECS) {
            File policyFile = new File(policyDir, spec.filename);
            PolicyDocumentArtifactGenerator.generatePdf(spec, policyFile);
            assertTrue(policyFile.exists());
            assertTrue(policyFile.length() > 0);

            String sha256 = computeFileSha256(policyFile);

            // Also copy to persistent storage with hash-addressed filename (<sha256>.pdf)
            File hashAddressedFile = new File(storageDir, sha256 + ".pdf");
            java.nio.file.Files.copy(policyFile.toPath(), hashAddressedFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            System.out.printf("Product: %-32s | UIN: %-22s | Size: %6d bytes | SHA-256: %s%n",
                    spec.productName, spec.irdaUin, policyFile.length(), sha256);
        }
        System.out.println("=========================================================================");
    }

    private String computeFileSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
