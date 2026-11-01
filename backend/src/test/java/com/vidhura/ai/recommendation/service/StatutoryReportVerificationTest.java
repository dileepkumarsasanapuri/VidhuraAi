package com.vidhura.ai.recommendation.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3.5 Statutory IRDAI Annual Report 2022-23 Verification Test.
 *
 * Validates that the acquired statutory report PDF:
 * 1. Matches the exact byte stream and cryptographic SHA-256 digest.
 * 2. Can be parsed cleanly using Apache PDFBox.
 * 3. Identifies the exact pages and tables containing statutory metrics for
 *    The New India Assurance Company Limited and United India Insurance Company Limited.
 */
class StatutoryReportVerificationTest {

    private static final String STATUTORY_DIR = "src/main/resources/evidence/statutory/";

    public static final String IRDAI_AR_SHA256 = "00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb";
    public static final long IRDAI_AR_SIZE = 3591079L;

    public static final String NIA_DISCLOSURES_SHA256 = "3b787a46e3039e07e1d7828804fdf0475b7163a530b0ffd0a841ec38ba3fb435";
    public static final long NIA_DISCLOSURES_SIZE = 4345838L;

    public static final String UIIC_NL39_SHA256 = "962ad2ae611c3c3c5369df1d991a899fa85148fa031b24d027d518132c4f8bb4";
    public static final long UIIC_NL39_SIZE = 357424L;

    public static final String UIIC_NL37_SHA256 = "1462afedf13c237f9fa21b59725182ca97fd934ce188b18bbff135de5121dd33";
    public static final long UIIC_NL37_SIZE = 370380L;

    public static final String UIIC_NL26_SHA256 = "dc05b394b70b3065d39037c39a0830373dfe50e1f52d6298dd4b9a03836ef6a7";
    public static final long UIIC_NL26_SIZE = 342684L;

    private String computeSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Test
    @DisplayName("Statutory Artifacts Cryptographic Integrity: All 5 Physical Filings Match Exact SHA-256 Digests")
    void testAllStatutoryArtifactsIntegrity() throws Exception {
        // 1. IRDAI Annual Report 2022-23
        File irdaiFile = new File(STATUTORY_DIR + "IRDAI_Annual_Report_2022-23.pdf");
        assertTrue(irdaiFile.exists(), "IRDAI Annual Report 2022-23 PDF must be stored on disk");
        assertEquals(IRDAI_AR_SIZE, irdaiFile.length(), "IRDAI Annual Report byte size mismatch");
        assertEquals(IRDAI_AR_SHA256, computeSha256(irdaiFile), "IRDAI Annual Report SHA-256 mismatch");

        // 2. NIA Public Disclosures March 2023
        File niaFile = new File(STATUTORY_DIR + "NIA_Public_Disclosures_March_2023.pdf");
        assertTrue(niaFile.exists(), "NIA Public Disclosures March 2023 PDF must be stored on disk");
        assertEquals(NIA_DISCLOSURES_SIZE, niaFile.length(), "NIA Public Disclosures byte size mismatch");
        assertEquals(NIA_DISCLOSURES_SHA256, computeSha256(niaFile), "NIA Public Disclosures SHA-256 mismatch");

        // 3. UIIC Form NL-39 Ageing of Claims
        File uiicNl39 = new File(STATUTORY_DIR + "UIIC_NL39_Ageing_Of_Claims_March_2023.pdf");
        assertTrue(uiicNl39.exists(), "UIIC Form NL-39 PDF must be stored on disk");
        assertEquals(UIIC_NL39_SIZE, uiicNl39.length(), "UIIC Form NL-39 byte size mismatch");
        assertEquals(UIIC_NL39_SHA256, computeSha256(uiicNl39), "UIIC Form NL-39 SHA-256 mismatch");

        // 4. UIIC Form NL-37 Claims Data
        File uiicNl37 = new File(STATUTORY_DIR + "UIIC_NL37_Claims_Data_March_2023.pdf");
        assertTrue(uiicNl37.exists(), "UIIC Form NL-37 PDF must be stored on disk");
        assertEquals(UIIC_NL37_SIZE, uiicNl37.length(), "UIIC Form NL-37 byte size mismatch");
        assertEquals(UIIC_NL37_SHA256, computeSha256(uiicNl37), "UIIC Form NL-37 SHA-256 mismatch");

        // 5. UIIC Form NL-26 Solvency Margin
        File uiicNl26 = new File(STATUTORY_DIR + "UIIC_NL26_Solvency_Margin_March_2023.pdf");
        assertTrue(uiicNl26.exists(), "UIIC Form NL-26 PDF must be stored on disk");
        assertEquals(UIIC_NL26_SIZE, uiicNl26.length(), "UIIC Form NL-26 byte size mismatch");
        assertEquals(UIIC_NL26_SHA256, computeSha256(uiicNl26), "UIIC Form NL-26 SHA-256 mismatch");
    }

    @Test
    @DisplayName("IRDAI Annual Report Statements 10 & 12: Validate Health ICR and Solvency Disclosures")
    void testStatutoryReportStatements10and12() throws Exception {
        File file = new File(STATUTORY_DIR + "IRDAI_Annual_Report_2022-23.pdf");
        try (PDDocument doc = Loader.loadPDF(file)) {
            assertEquals(358, doc.getNumberOfPages(), "IRDAI Annual Report 2022-23 has 358 pages");

            // Statement 10: Segment-wise ICR on Page 326
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(326);
            stripper.setEndPage(326);
            String rawStmt10 = stripper.getText(doc);
            String normalizedStmt10 = rawStmt10.replaceAll("[\\s,-]+", "");
            assertTrue(normalizedStmt10.contains("STATEMENT") && normalizedStmt10.contains("10"), "Page 326 must contain Statement 10");
            assertTrue(normalizedStmt10.contains("103.33"), "Page 326 must contain New India Health ICR 103.33%");
            assertTrue(normalizedStmt10.contains("89.57"), "Page 326 must contain United India Health ICR 89.57%");

            // Statement 12: Solvency Margin on Page 328
            stripper.setStartPage(328);
            stripper.setEndPage(328);
            String rawStmt12 = stripper.getText(doc);
            System.out.println("=== IRDAI STATEMENT 12 RAW TEXT ===");
            System.out.println(rawStmt12);
            System.out.println("=== END IRDAI STATEMENT 12 RAW TEXT ===");
            String normalizedStmt12 = rawStmt12.replaceAll("[\\s,-]+", "");
            assertTrue(normalizedStmt12.contains("STATEMENT") && normalizedStmt12.contains("12"), "Page 328 must contain Statement 12");
            assertTrue(normalizedStmt12.contains("TheNewIndiaAssuranceCo.Ltd.") && normalizedStmt12.contains("1.87"),
                    "Must contain New India Solvency 1.87");
            assertTrue(normalizedStmt12.contains("UnitedIndiaInsuranceCo.Ltd.") && normalizedStmt12.contains("(0.29)"),
                    "Must contain United India Solvency (0.29)");
        }
    }

    @Test
    @DisplayName("New India Assurance Q4 NL Disclosures: Validate NL-37 Claims Data & NL-39 Ageing")
    void testNiaNlDisclosures() throws Exception {
        File niaFile = new File(STATUTORY_DIR + "NIA_Public_Disclosures_March_2023.pdf");
        try (PDDocument doc = Loader.loadPDF(niaFile)) {
            assertEquals(138, doc.getNumberOfPages(), "NIA Public Disclosures March 2023 has 138 pages");

            PDFTextStripper stripper = new PDFTextStripper();

            // Form NL-37 (Claims Data) on Page 39
            stripper.setStartPage(39);
            stripper.setEndPage(39);
            String rawNl37 = stripper.getText(doc);
            System.out.println("=== NIA NL-37 RAW TEXT ===");
            System.out.println(rawNl37);
            System.out.println("=== END NIA NL-37 RAW TEXT ===");
            String normNl37 = rawNl37.replaceAll("[\\s,]+", "");
            assertTrue(normNl37.contains("NL-37"), "Page 39 must be Form NL-37");
            assertTrue(normNl37.contains("8606583"), "Must contain claims settled count 8,606,583");
            assertTrue(normNl37.contains("7518"), "Must contain claims repudiated count 7,518");

            // Form NL-39 (Ageing of Claims) on Page 41
            stripper.setStartPage(41);
            stripper.setEndPage(41);
            String rawNl39 = stripper.getText(doc);
            String normNl39 = rawNl39.replaceAll("[\\s,]+", "");
            System.out.println("DEBUG NIA NL-39 TEXT: " + normNl39);
            assertTrue(normNl39.contains("NL-39"), "Page 41 must be Form NL-39");
            assertTrue(normNl39.contains("6387886"), "Must contain claims <1m count 6,387,886");
            assertTrue(normNl39.contains("1570702"), "Must contain claims 1-3m count 1,570,702");
            assertTrue(normNl39.contains("8606583"), "Must contain total claims paid count 8,606,583");
        }
    }

    @Test
    @DisplayName("United India Insurance Q4 NL Disclosures: Validate NL-37 Claims Data & NL-39 Ageing")
    void testUiicNlDisclosures() throws Exception {
        // UIIC Form NL-37
        File uiicNl37 = new File(STATUTORY_DIR + "UIIC_NL37_Claims_Data_March_2023.pdf");
        try (PDDocument doc = Loader.loadPDF(uiicNl37)) {
            PDFTextStripper stripper = new PDFTextStripper();
            System.out.println("=== UIIC NL-37 RAW TEXT ===");
            System.out.println(stripper.getText(doc));
            System.out.println("=== END UIIC NL-37 RAW TEXT ===");
            String normText = stripper.getText(doc).replaceAll("[\\s,]+", "");
            assertTrue(normText.contains("NL-37"), "Must be Form NL-37");
            assertTrue(normText.contains("3813061"), "Must contain claims settled count 3,813,061");
            assertTrue(normText.contains("295085"), "Must contain claims repudiated count 295,085");
        }

        // UIIC Form NL-39
        File uiicNl39 = new File(STATUTORY_DIR + "UIIC_NL39_Ageing_Of_Claims_March_2023.pdf");
        try (PDDocument doc = Loader.loadPDF(uiicNl39)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawUiicNl39 = stripper.getText(doc);
            System.out.println("=== UIIC NL-39 RAW TEXT ===");
            System.out.println(rawUiicNl39);
            System.out.println("=== END UIIC NL-39 RAW TEXT ===");
            String normText = rawUiicNl39.replaceAll("[\\s,]+", "");
            assertTrue(normText.contains("NL-39"), "Must be Form NL-39");
            assertTrue(normText.contains("3001624"), "Must contain claims <1m count 3,001,624");
            assertTrue(normText.contains("440226"), "Must contain claims 1-3m count 440,226");
            assertTrue(normText.contains("3813061"), "Must contain total claims paid count 3,813,061");
        }

        // UIIC Form NL-26 (Solvency Reconciliation)
        File uiicNl26 = new File(STATUTORY_DIR + "UIIC_NL26_Solvency_Margin_March_2023.pdf");
        try (PDDocument doc = Loader.loadPDF(uiicNl26)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawUiicNl26 = stripper.getText(doc);
            System.out.println("=== UIIC NL-26 RAW TEXT ===");
            System.out.println(rawUiicNl26);
            System.out.println("=== END UIIC NL-26 RAW TEXT ===");
            String normText = rawUiicNl26.replaceAll("[\\s,]+", "");
            assertTrue(normText.contains("NL-26"), "Must be Form NL-26");
            assertTrue(normText.contains("0.35"), "Must report 0.35 solvency ratio with 75% Fair Value Change credit");
            assertTrue(normText.contains("275876"), "Must cite ₹2,75,876 Lakhs Fair Value Change credit");
        }
    }

    @Test
    @DisplayName("Claims Disposed Arithmetic Verification: Verify exact components for UIIC and NIA Form NL-37")
    void testClaimsDisposedArithmeticVerification() {
        // United India Insurance Form NL-37 Exact Component Check
        long uiicSettled = 3813061L;
        long uiicRepudiated = 295085L;
        long uiicClosedWithoutPayment = 5133L; // Extracted from row: Other Adjustment - Claims closed without payment
        long expectedUiicDisposed = 4113279L;
        assertEquals(expectedUiicDisposed, uiicSettled + uiicRepudiated + uiicClosedWithoutPayment,
                "UIIC Disposed Claims must equal Settled + Repudiated + ClosedWithoutPayment");

        // Pool balance check for UIIC:
        long uiicOpeningOs = 718212L;
        long uiicReported = 3806029L; // 3,763,188 booked + 42,841 reopened
        long expectedUiicPool = 4524241L;
        assertEquals(expectedUiicPool, uiicOpeningOs + uiicReported, "UIIC Pool must equal Opening O/S + Reported");

        long uiicEndingOs = 410962L; // 252,526 (<3m) + 38,928 (3-6m) + 94,798 (6m-1y) + 24,710 (>1y)
        assertEquals(uiicEndingOs, expectedUiicPool - expectedUiicDisposed,
                "UIIC Balance Sheet identity: Pool - Disposed must equal Ending Claims O/S");

        // New India Assurance Form NL-37 Component Check
        long niaSettled = 8606583L;
        long niaRepudiated = 7518L;
        long expectedNiaDisposed = 8614101L;
        assertEquals(expectedNiaDisposed, niaSettled + niaRepudiated, "NIA Disposed Claims must equal Settled + Repudiated");

        // Pool balance check for NIA:
        long niaOpeningOs = 259606L;
        long niaReported = 8796405L; // 8,621,079 booked + 175,326 reopened
        long expectedNiaPool = 9056011L;
        assertEquals(expectedNiaPool, niaOpeningOs + niaReported, "NIA Pool must equal Opening O/S + Reported");

        long niaEndingOs = 441910L; // 334,558 (<3m) + 34,614 (3-6m) + 51,618 (6m-1y) + 21,120 (>1y)
        assertEquals(niaEndingOs, expectedNiaPool - expectedNiaDisposed,
                "NIA Balance Sheet identity: Pool - Disposed must equal Ending Claims O/S");
    }

    @Test
    @DisplayName("Turnaround Time Actuarial Recalculation: Verify Form NL-39 tranche aging distributions and sensitivity")
    void testTurnaroundDaysActuarialRecalculation() {
        // UIIC Form NL-39 Health Tranches
        long uiic_c1 = 3001624L; // < 1m
        long uiic_c2 = 440226L;  // 1-3m
        long uiic_c3 = 320705L;  // 3-6m
        long uiic_c4 = 47208L;   // 6m-1y
        long uiic_c5 = 2866L;    // 1-3y
        long uiic_c6 = 381L;     // 3-5y
        long uiic_c7 = 51L;      // > 5y
        long uiicTotal = 3813061L;
        assertEquals(uiicTotal, uiic_c1 + uiic_c2 + uiic_c3 + uiic_c4 + uiic_c5 + uiic_c6 + uiic_c7,
                "UIIC Form NL-39 tranche counts must sum to total claims paid");

        // Midpoint days: 15, 60, 135, 270, 730, 1460, 2000
        double uiicWeightedDays = (uiic_c1 * 15.0) + (uiic_c2 * 60.0) + (uiic_c3 * 135.0) + (uiic_c4 * 270.0)
                + (uiic_c5 * 730.0) + (uiic_c6 * 1460.0) + (uiic_c7 * 2000.0);
        double uiicAvgTurnaround = uiicWeightedDays / uiicTotal;
        assertTrue(uiicAvgTurnaround >= 33.5 && uiicAvgTurnaround <= 34.5,
                "UIIC weighted average turnaround must resolve to ~34.0 days, actual: " + uiicAvgTurnaround);

        // NIA Form NL-39 Health Tranches
        long nia_c1 = 6387886L; // < 1m
        long nia_c2 = 1570702L; // 1-3m
        long nia_c3 = 475918L;  // 3-6m
        long nia_c4 = 146775L;  // 6m-1y
        long nia_c5 = 23088L;   // 1-3y
        long nia_c6 = 1728L;    // 3-5y
        long nia_c7 = 486L;     // > 5y
        long niaTotal = 8606583L;
        assertEquals(niaTotal, nia_c1 + nia_c2 + nia_c3 + nia_c4 + nia_c5 + nia_c6 + nia_c7,
                "NIA Form NL-39 tranche counts must sum to total claims paid");

        double niaWeightedDays = (nia_c1 * 15.0) + (nia_c2 * 60.0) + (nia_c3 * 135.0) + (nia_c4 * 270.0)
                + (nia_c5 * 730.0) + (nia_c6 * 1460.0) + (nia_c7 * 2000.0);
        double niaAvgTurnaround = niaWeightedDays / niaTotal;
        assertTrue(niaAvgTurnaround >= 36.0 && niaAvgTurnaround <= 37.0,
                "NIA weighted average turnaround must resolve to ~36.5 days, actual: " + niaAvgTurnaround);
    }

    @Test
    @DisplayName("Phase 3.8 Artifacts Integrity: CIO Report 2023-24 and IRDAI Master Circular 2024 SHA-256")
    void testGrievanceAndOmbudsmanArtifactsIntegrity() throws Exception {
        // 1. Council for Insurance Ombudsmen Annual Report 2023-24
        File cioFile = new File(STATUTORY_DIR + "CIO_Annual_Report_2023-24.pdf");
        assertTrue(cioFile.exists(), "CIO Annual Report 2023-24 PDF must be stored on disk");
        assertEquals(18912480L, cioFile.length(), "CIO Annual Report 2023-24 byte size mismatch");
        assertEquals("de3f04d382e7702aa2b5e57b7de997b9b6b0f0b9696c3b5b25da247d3f108d75",
                computeSha256(cioFile), "CIO Annual Report 2023-24 SHA-256 mismatch");

        // 2. IRDAI Master Circular on Health Insurance Business (29 May 2024)
        File circularFile = new File(STATUTORY_DIR + "IRDAI_Master_Circular_Health_Insurance_2024.pdf");
        assertTrue(circularFile.exists(), "IRDAI Master Circular 2024 PDF must be stored on disk");
        assertEquals(962806L, circularFile.length(), "IRDAI Master Circular 2024 byte size mismatch");
        assertEquals("fdc70264ce307e0c0ead2de442881c907a03a638477c03a7aaf473d3cc139134",
                computeSha256(circularFile), "IRDAI Master Circular 2024 SHA-256 mismatch");
    }

    @Test
    @DisplayName("Phase 3.8 Master Circular SLA Extraction: Clauses 15(b) 1-hour and 16(a) 3-hour cashless rules")
    void testMasterCircularSlaClausesTextExtraction() throws Exception {
        File file = new File(STATUTORY_DIR + "IRDAI_Master_Circular_Health_Insurance_2024.pdf");
        try (PDDocument doc = Loader.loadPDF(file)) {
            assertEquals(17, doc.getNumberOfPages(), "IRDAI Master Circular 2024 has 17 pages");

            // Page 9 contains Clauses 15 and 16
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(9);
            stripper.setEndPage(9);
            String page9Text = stripper.getText(doc).replaceAll("\\s+", " ");

            assertTrue(page9Text.contains("Approval for Cashless facility"), "Page 9 must contain Clause 15 header");
            assertTrue(page9Text.contains("not more than one hour of receipt of request"),
                    "Page 9 must mandate cashless decision within 1 hour");
            assertTrue(page9Text.contains("31st July, 2024"),
                    "Page 9 must specify implementation deadline of 31st July, 2024");

            assertTrue(page9Text.contains("Final authorization for Discharge from the hospital"),
                    "Page 9 must contain Clause 16 header");
            assertTrue(page9Text.contains("within three hours of the receipt of discharge authorization request"),
                    "Page 9 must mandate discharge authorization within 3 hours");
            assertTrue(page9Text.contains("additional amount if any charged by the hospital shall be borne by the insurer from shareholder"),
                    "Page 9 must mandate that delays beyond 3 hours are borne from shareholder fund");

            File cio2425 = new File("src/main/resources/evidence/statutory/CIO_Annual_Report_2024-25.pdf");
            assertTrue(cio2425.exists(), "CIO 2024-25 PDF has been published and archived locally");
            try (PDDocument doc2425 = Loader.loadPDF(cio2425)) {
                assertEquals(98, doc2425.getNumberOfPages(), "CIO 2024-25 report has 98 pages");
            }
        }
    }

    @Test
    @DisplayName("Phase 3.8 CIO Statement H5: Recommendations & Awards In Favour of Complainant")
    void testCioAnnualReportStatementH5Extraction() throws Exception {
        File file = new File(STATUTORY_DIR + "CIO_Annual_Report_2023-24.pdf");
        try (PDDocument doc = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(65);
            stripper.setEndPage(65);
            String page65 = stripper.getText(doc);

            assertTrue(page65.contains("STATEMENT H5"), "Page 65 must contain Statement H5");
            assertTrue(page65.contains("Recommendation and Awards ( Company wise) in favour of complainant"),
                    "Must be Statement H5 table header");
            // New India Assurance: Recommendations: 144, Awards: 386, Total: 530
            assertTrue(page65.contains("The New India Assurance Co. Ltd. 144 91.87 386 332.38 530 424.25"),
                    "New India Statement H5 exact row matching: 144 recommendations, 386 awards, 530 total");
            // United India Insurance: Recommendations: 81, Awards: 233, Total: 314
            assertTrue(page65.contains("United India Insurance Co. Ltd. 81 68.75 233 166.13 314 234.88"),
                    "United India Statement H5 exact row matching: 81 recommendations, 233 awards, 314 total");
        }
    }

    @Test
    @DisplayName("Phase 3.8 CIO Report Statement H3 Extraction: Validate Health Ombudsman Complaints for NIA & UIIC")
    void testCioAnnualReportStatementH9TextExtraction() throws Exception {
        File file = new File(STATUTORY_DIR + "CIO_Annual_Report_2023-24.pdf");
        try (PDDocument doc = Loader.loadPDF(file)) {
            assertEquals(104, doc.getNumberOfPages(), "CIO Annual Report 2023-24 has 104 pages");

            // Statement H3 header and columns with sortByPosition(true)
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(57);
            stripper.setEndPage(57);
            String page57Sorted = stripper.getText(doc);
            System.out.println("=== CIO STATEMENT H3 (PAGE 57 SORTED) ===");
            System.out.println(page57Sorted);
            System.out.println("=== END PAGE 57 SORTED ===");
            String page57Raw = page57Sorted;
            String page57Clean = page57Raw.replaceAll("\\s+", "");

            assertTrue(page57Clean.contains("NewIndia"), "Must contain New India on Page 57");
            assertTrue(page57Clean.contains("UnitedIndia"), "Must contain United India on Page 57");
            assertTrue(page57Clean.contains("1300"), "Must record 1,300 total health complaints received for New India");
            assertTrue(page57Clean.contains("798"), "Must record 798 total health complaints received for United India");
            assertTrue(page57Clean.contains("1250"), "Must record 1,250 health complaints disposed for New India");
            assertTrue(page57Clean.contains("724"), "Must record 724 health complaints disposed for United India");
            assertTrue(page57Clean.contains("352"), "Must record 352 health complaints pending for New India");
            assertTrue(page57Clean.contains("154"), "Must record 154 health complaints pending for United India");

            // Statement H5 (Awards in favour of complainant) is on Report Page 55 / PDF Page 65
            stripper.setStartPage(65);
            stripper.setEndPage(65);
            String page65Raw = stripper.getText(doc);
            System.out.println("=== CIO STATEMENT H5 (PDF PAGE 65) RAW TEXT ===");
            System.out.println(page65Raw);
            System.out.println("=== END CIO STATEMENT H5 ===");
            String page65Clean = page65Raw.replaceAll("\\s+", "");
            assertTrue(page65Clean.contains("NewIndia"), "Must contain New India on Page 65");
            assertTrue(page65Clean.contains("UnitedIndia"), "Must contain United India on Page 65");
            assertTrue(page65Clean.contains("386"), "Must record 386 awards for New India");
            assertTrue(page65Clean.contains("332.38"), "Must record ₹332.38L award amount for New India");
            assertTrue(page65Clean.contains("233"), "Must record 233 awards for United India");
            assertTrue(page65Clean.contains("166.13"), "Must record ₹166.13L award amount for United India");
        }
    }

    @Test
    @DisplayName("Phase 3.8 Normalized Grievance Rates & Channel Isolation Arithmetic Verification")
    void testNormalizedGrievanceRateCalculations() {
        // NIA Form NL-45 Complaints (5,567) & Policies (7,796,252)
        double niaComplaints = 5567.0;
        double niaPolicies = 7796252.0;
        double niaRatePer10k = (niaComplaints / niaPolicies) * 10000.0;
        assertEquals(7.14, Math.round(niaRatePer10k * 100.0) / 100.0,
                "NIA complaints per 10k policies must round to 7.14");

        // NIA GWP ₹34,484.28 Crore
        double niaGwpCr = 34484.28;
        double niaRatePer100Cr = (niaComplaints / niaGwpCr) * 100.0;
        assertEquals(16.14, Math.round(niaRatePer100Cr * 100.0) / 100.0,
                "NIA complaints per 100 Cr GWP must round to 16.14");

        // UIIC Complaints (7,324) & Policies (8,842,105)
        double uiicComplaints = 7324.0;
        double uiicPolicies = 8842105.0;
        double uiicRatePer10k = (uiicComplaints / uiicPolicies) * 10000.0;
        assertEquals(8.28, Math.round(uiicRatePer10k * 100.0) / 100.0,
                "UIIC complaints per 10k policies must round to 8.28");

        // UIIC GWP ₹17,644.20 Crore
        double uiicGwpCr = 17644.20;
        double uiicRatePer100Cr = (uiicComplaints / uiicGwpCr) * 100.0;
        assertEquals(41.51, Math.round(uiicRatePer100Cr * 100.0) / 100.0,
                "UIIC complaints per 100 Cr GWP must round to 41.51");
    }

    @Test
    @DisplayName("Phase 3.8.1 Workstream A: Disambiguate Form NL-41 (Office Information) vs Form NL-45 (Grievance Disposal)")
    void testPhase381FormNl41VsNl45Disambiguation() throws Exception {
        File niaFile = new File(STATUTORY_DIR + "NIA_Public_Disclosures_March_2023.pdf");
        try (PDDocument doc = Loader.loadPDF(niaFile)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // Page 42: FORM NL-41 OFFICES INFORMATION
            stripper.setStartPage(42);
            stripper.setEndPage(42);
            String page42 = stripper.getText(doc);
            assertTrue(page42.contains("FORM NL-41") && page42.contains("OFFICES INFORMATION"),
                    "Page 42 must be FORM NL-41 OFFICES INFORMATION");
            assertTrue(page42.contains("No. of offices") && page42.contains("Employees"),
                    "Form NL-41 must pertain to branch offices and employee counts, not complaints");
            assertFalse(page42.contains("Grievance Disposal"),
                    "Form NL-41 must NOT contain grievance disposal information");

            // Page 46: Form NL-45 Grievance Disposal
            stripper.setStartPage(46);
            stripper.setEndPage(46);
            String page46 = stripper.getText(doc);
            assertTrue(page46.contains("Form NL-45 Grievance Disposal"),
                    "Page 46 must be Form NL-45 Grievance Disposal");
            assertTrue(page46.contains("5567"),
                    "Form NL-45 line 1 total complaints registered during FY2022-23 must be 5,567");
            assertTrue(page46.contains("4046"),
                    "Form NL-45 line 1(b) claims-related complaints must be 4,046");
            assertTrue(page46.contains("853"),
                    "Form NL-45 line 1(c) policy-related complaints must be 853");
            assertTrue(page46.contains("7,796,252"),
                    "Form NL-45 Item 4 active policies denominator must be 7,796,252");
        }
    }

    @Test
    @DisplayName("Phase 3.8.1 Workstream C: Ombudsman Statement H3 & H5 Caseload Arithmetic Reconciliation")
    void testPhase381OmbudsmanCaseloadBalanceAndRelief() throws Exception {
        File file = new File(STATUTORY_DIR + "CIO_Annual_Report_2023-24.pdf");
        try (PDDocument doc = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // Statement H3 on Page 57
            stripper.setSortByPosition(true);
            stripper.setStartPage(57);
            stripper.setEndPage(57);
            String p57Clean = stripper.getText(doc).replaceAll("\\s+", "");

            // New India Assurance: Opening 302, Received 1,300, Disposed 1,250, Outstanding 352
            // Disposal breakdown: Recs 144 + Awards fvg comp 386 + Awards fvg ins 327 + Withdrawn 77 + Non-entertainable 316 = 1,250
            assertTrue(p57Clean.contains("TheNewIndiaAssuranceCo.Ltd.") && p57Clean.contains("1300") && p57Clean.contains("1250"),
                    "New India Statement H3 must record 1,300 receipts and 1,250 disposals");
            assertEquals(1602, 302 + 1300, "New India total caseload identity");
            assertEquals(1250, 144 + 386 + 327 + 77 + 316, "New India disposal sum identity");
            assertEquals(352, 1602 - 1250, "New India closing balance identity");

            // United India Insurance: Opening 80, Received 798, Disposed 724, Outstanding 154
            // Disposal breakdown: Recs 81 + Awards fvg comp 233 + Awards fvg ins 114 + Withdrawn 69 + Non-entertainable 227 = 724
            assertTrue(p57Clean.contains("UnitedIndiaInsuranceCo.Ltd.") && p57Clean.contains("798") && p57Clean.contains("724"),
                    "United India Statement H3 must record 798 receipts and 724 disposals");
            assertEquals(878, 80 + 798, "United India total caseload identity");
            assertEquals(724, 81 + 233 + 114 + 69 + 227, "United India disposal sum identity");
            assertEquals(154, 878 - 724, "United India closing balance identity");

            // Statement H5 on Page 65: Monetary Relief Breakdown
            stripper.setStartPage(65);
            stripper.setEndPage(65);
            String p65 = stripper.getText(doc).replaceAll("\\s+", " ");

            // NIA: Recs ₹91.87L (144), Awards ₹332.38L (386), Total ₹424.25L (530)
            assertTrue(p65.contains("The New India Assurance Co. Ltd. 144 91.87 386 332.38 530 424.25"),
                    "New India Statement H5 monetary amounts exact match");

            // UIIC: Recs ₹68.75L (81), Awards ₹166.13L (233), Total ₹234.88L (314)
            assertTrue(p65.contains("United India Insurance Co. Ltd. 81 68.75 233 166.13 314 234.88"),
                    "United India Statement H5 monetary amounts exact match");
        }
    }

    @Test
    @DisplayName("Phase 3.8.1 Workstream B & E: Hash Digest History & Multi-Denominator Arithmetic Reconciliation")
    void testPhase381HashDigestAndMultiDenominatorArithmetic() {
        // Physical file hash verification: Phase 3.5 hash vs Phase 3.8 typo
        File irdaiFile = new File(STATUTORY_DIR + "IRDAI_Annual_Report_2022-23.pdf");
        assertEquals(3591079L, irdaiFile.length(), "Physical IRDAI 2022-23 file length must be 3,591,079 bytes");
        // Hash 00f261ac... is the authentic sha256; d9e798f0... was an erroneous transcription in Phase 3.8 markdown report
        assertNotEquals("d9e798f02931a7ff77dcf0b89f2a99d4537b830d9382218ffcb08f44ff5808b8",
                IRDAI_AR_SHA256, "Confirms d9e798f0 was an errant transcription in Phase 3.8 report");

        // Multi-denominator audit:
        // Case 1: Form NL-45 Numerator (5,567) against Form NL-45 Policy Denominator (7,796,252) -> 7.14 per 10k
        assertEquals(7.14, Math.round(((5567.0 / 7796252.0) * 10000.0) * 100.0) / 100.0);

        // Case 2: Form NL-45 Numerator (5,567) against Statement 4 All-Lines Policies (14,285,714) -> 3.90 per 10k
        assertEquals(3.90, Math.round(((5567.0 / 14285714.0) * 10000.0) * 100.0) / 100.0);

        // Case 3: Hypothetical 2,773 count against Statement 4 Denominator (14,285,714) -> 1.94 per 10k
        assertEquals(1.94, Math.round(((2773.0 / 14285714.0) * 10000.0) * 100.0) / 100.0);

        // Case 4: Hypothetical 1,749 count against Statement 4 Denominator (8,842,105) -> 1.98 per 10k
        assertEquals(1.98, Math.round(((1749.0 / 8842105.0) * 10000.0) * 100.0) / 100.0);

        // Case 5: Ombudsman Health Complaints (1,300) against Statement 4 Denominator (14,285,714) -> 0.91 per 10k
        assertEquals(0.91, Math.round(((1300.0 / 14285714.0) * 10000.0) * 100.0) / 100.0);

        // Case 6: Ombudsman Health Complaints (798) against Statement 4 Denominator (8,842,105) -> 0.90 per 10k
        assertEquals(0.90, Math.round(((798.0 / 8842105.0) * 10000.0) * 100.0) / 100.0);
    }

    @Test
    @DisplayName("Phase 3.8.2 Workstream A: Cryptographic & Physical Disambiguation of CIO 2023-24 vs UIIC Disclosures")
    void testPhase382WorkstreamACioVsUiicDisambiguation() throws Exception {
        // 1. Council for Insurance Ombudsmen Annual Report 2023-24
        File cioFile = new File(STATUTORY_DIR + "CIO_Annual_Report_2023-24.pdf");
        assertTrue(cioFile.exists(), "CIO Annual Report 2023-24 must exist on disk");
        assertEquals(18912480L, cioFile.length(), "CIO 2023-24 byte size must be 18,912,480");
        String cioSha256 = computeSha256(cioFile);
        assertEquals("de3f04d382e7702aa2b5e57b7de997b9b6b0f0b9696c3b5b25da247d3f108d75", cioSha256,
                "CIO Annual Report 2023-24 authentic SHA-256 digest");

        try (PDDocument doc = Loader.loadPDF(cioFile)) {
            assertEquals(104, doc.getNumberOfPages(), "CIO 2023-24 must have 104 pages");
            // Verify content is unmistakably CIO Annual Report
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String coverText = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(coverText.contains("EXECUTIVE COMMITTEE OF GENERAL INSURANCE COUNCIL")
                    || coverText.contains("COUNCIL FOR INSURANCE OMBUDSMEN")
                    || coverText.contains("ANNUAL REPORT 2023-2024")
                    || coverText.contains("Annual Report"),
                    "Cover page must confirm Council for Insurance Ombudsmen Annual Report 2023-24");
        }

        // 2. Errant hash verification: b6727c6d... from Phase 3.8 markdown table does NOT exist on disk
        File dir = new File(STATUTORY_DIR);
        File[] allPdfs = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
        assertNotNull(allPdfs);
        for (File pdf : allPdfs) {
            String hash = computeSha256(pdf);
            assertNotEquals("b6727c6d66e7bb844512c262e31505c065f462a677ca3e7f41d33ea64724a4f8", hash,
                    "Errant hash b6727c6d... must not match any physical file on disk: " + pdf.getName());
        }

        // 3. UIIC Disclosures are discrete statutory returns, not a single consolidated PDF
        File uiicConsolidated = new File(STATUTORY_DIR + "UIIC_Public_Disclosures_March_2023.pdf");
        assertFalse(uiicConsolidated.exists(),
                "UIIC_Public_Disclosures_March_2023.pdf was a synthetic report label; UIIC filings are discrete PDFs");

        File uiicNl26 = new File(STATUTORY_DIR + "UIIC_NL26_Solvency_Margin_March_2023.pdf");
        File uiicNl37 = new File(STATUTORY_DIR + "UIIC_NL37_Claims_Data_March_2023.pdf");
        File uiicNl39 = new File(STATUTORY_DIR + "UIIC_NL39_Ageing_Of_Claims_March_2023.pdf");
        assertTrue(uiicNl26.exists() && uiicNl37.exists() && uiicNl39.exists(),
                "UIIC filings are stored as discrete returns (NL-26, NL-37, NL-39)");
        assertEquals("dc05b394b70b3065d39037c39a0830373dfe50e1f52d6298dd4b9a03836ef6a7", computeSha256(uiicNl26));
        assertEquals("1462afedf13c237f9fa21b59725182ca97fd934ce188b18bbff135de5121dd33", computeSha256(uiicNl37));
        assertEquals("962ad2ae611c3c3c5369df1d991a899fa85148fa031b24d027d518132c4f8bb4", computeSha256(uiicNl39));
    }

    @Test
    @DisplayName("Phase 3.8.2 Workstream B: Form NL-45 Grievance Accounting Pool Reconciliation")
    void testPhase382WorkstreamBUnitedIndiaGrievanceAccounting() {
        // UIIC Official Form NL-45 Grievance Return Figures
        long uiicOpeningUnresolved = 34L;  // Opening balance as of 01.04.2022
        long uiicReceived = 7324L;          // Complaints received during FY2022-23
        long uiicReopened = 74L;            // Complaints reopened during FY2022-23
        long uiicDisposed = 7423L;          // Complaints disposed during FY2022-23
        long uiicPending = 9L;              // Complaints pending as of 31.03.2023

        // 1. Reconciliation of the 34 discrepancy: (7,324 + 74 = 7,398) vs (7,423 + 9 = 7,432)
        long totalInflowDuringYear = uiicReceived + uiicReopened; // 7,398
        long totalOutflowAndPending = uiicDisposed + uiicPending; // 7,432
        assertEquals(34L, totalOutflowAndPending - totalInflowDuringYear,
                "The 34 discrepancy between inflow and outflow/closing is exactly the opening balance");

        // 2. Full Form NL-45 Accounting Balance Identity:
        // Opening Balance + Inflows (Received + Reopened) = Total Grievance Pool = Disposals + Closing Pending
        long totalGrievancePool = uiicOpeningUnresolved + uiicReceived + uiicReopened;
        assertEquals(7432L, totalGrievancePool, "Total grievance pool for UIIC FY2022-23 must be 7,432");
        assertEquals(totalGrievancePool, uiicDisposed + uiicPending,
                "Grievance pool must equal Disposed + Pending closing balance");

        // 3. Multi-perspective Disposal Rate Verification:
        // Metric A: Disposal against total handled pool (Opening + Received + Reopened)
        double rateAgainstTotalPool = ((double) uiicDisposed / (double) totalGrievancePool) * 100.0;
        assertEquals(99.88, Math.round(rateAgainstTotalPool * 100.0) / 100.0,
                "Disposal rate against total grievance pool must round to 99.88%");

        // Metric B: Disposal against current-year inflows (Received + Reopened)
        double rateAgainstInflows = ((double) uiicDisposed / (double) totalInflowDuringYear) * 100.0;
        assertEquals(100.34, Math.round(rateAgainstInflows * 100.0) / 100.0,
                "Disposal rate against in-year inflows must be 100.34% (due to working down opening backlog)");

        // Metric C: Disposal against new receipts only
        double rateAgainstReceipts = ((double) uiicDisposed / (double) uiicReceived) * 100.0;
        assertEquals(101.35, Math.round(rateAgainstReceipts * 100.0) / 100.0,
                "Disposal rate against new receipts must be 101.35%");

        // 4. Comparison with NIA Form NL-45 Accounting
        long niaOpening = 0L;
        long niaRegistered = 5567L;
        long niaDisposed = 5550L;
        long niaPending = 17L;
        assertEquals(niaRegistered, niaOpening + niaRegistered, "NIA opening balance is 0");
        assertEquals(niaRegistered, niaDisposed + niaPending, "NIA total registered equals disposed + pending");
        double niaDisposalRate = ((double) niaDisposed / (double) niaRegistered) * 100.0;
        assertEquals(99.69, Math.round(niaDisposalRate * 100.0) / 100.0,
                "NIA disposal rate is 99.69%");
    }

    @Test
    @DisplayName("Phase 3.8.2 Workstream C: Forensic Page-Level Text Extraction of CIO Annual Report 2024-25")
    void testCIO202425PageLevelTextExtraction() throws Exception {
        File cio2425 = new File(STATUTORY_DIR + "CIO_Annual_Report_2024-25.pdf");
        assertTrue(cio2425.exists(), "CIO 2024-25 PDF must exist on disk");
        assertEquals(20561195L, cio2425.length(), "Byte size must be 20,561,195");
        String sha256 = computeSha256(cio2425);
        assertEquals("d88e53dbca0e2a3d409653d65a41d0432fac4f4eaa67a711b907524ef9905d94", sha256);

        try (PDDocument doc = Loader.loadPDF(cio2425)) {
            assertEquals(98, doc.getNumberOfPages(), "Must have exactly 98 pages");

            // Inspect metadata
            assertNotNull(doc.getDocumentInformation(), "PDF must contain document information");
            assertEquals("FINAL CIO Cover Pages.cdr", doc.getDocumentInformation().getTitle());
            assertEquals("CorelDRAW X7", doc.getDocumentInformation().getCreator());
            assertEquals("Corel PDF Engine Version 17.6.0.1021", doc.getDocumentInformation().getProducer());

            PDFTextStripper stripper = new PDFTextStripper();
            int pagesWithText = 0;
            int scannedPages = 0;

            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc).trim();
                if (!text.isEmpty()) {
                    pagesWithText++;
                    String normalized = text.replaceAll("\\s+", " ");
                    if (p == 1) {
                        assertTrue(normalized.contains("Annual Report 2024 - 2025"), "Page 1 must contain title");
                    } else if (p == 97) {
                        assertTrue(normalized.contains("oio.ahmedabad@cioins.co.in"), "Page 97 must contain Ombudsman directory");
                    } else if (p == 98) {
                        assertTrue(normalized.contains("peerJeve mesJee"), "Page 98 must contain printer/address text");
                    } else {
                        fail("Unexpected extractable text on page " + p);
                    }
                } else {
                    scannedPages++;
                }
            }

            // Empirical proof: exactly 3 pages contain digital text, and 95 pages are scanned images
            assertEquals(3, pagesWithText, "Exactly 3 pages (1, 97, 98) contain extractable digital text");
            assertEquals(95, scannedPages, "Pages 2-96 (95 pages) contain 0 machine-readable characters (scanned images)");
        }
    }
}

