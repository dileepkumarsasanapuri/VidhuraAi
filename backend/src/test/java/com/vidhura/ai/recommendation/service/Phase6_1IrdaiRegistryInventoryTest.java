package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.model.PolicyDiscoveryRun;
import com.vidhura.ai.recommendation.repository.IrdaiRegistryRepository;
import com.vidhura.ai.recommendation.repository.PolicyDiscoveryRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Phase6_1IrdaiRegistryInventoryTest {

    @Mock
    private IrdaiRegistryRepository registryRepository;

    @Mock
    private PolicyDiscoveryRunRepository runRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private IrdaiRegistryInventoryService inventoryService;

    // Realistic HTML fixture mimicking the actual IRDAI portal table structure
    private static final String SAMPLE_PORTAL_HTML = """
            <!DOCTYPE html>
            <html>
            <body>
            <div class="page-results">Showing 1 - 4 of 1,820 results.</div>
            <table class="table table-bordered table-hover table-striped">
                <thead>
                    <tr>
                        <th></th>
                        <th>Archive / Non Archive</th>
                        <th>Financial Year</th>
                        <th>Name of the Insurer</th>
                        <th>UIN</th>
                        <th>Product Name</th>
                        <th>Date of Approval</th>
                        <th>Documents</th>
                        <th>Type Of Product</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td><input type="checkbox"/></td>
                        <td>Non-Archived</td>
                        <td>2022-2023</td>
                        <td>Bajaj Allianz General Insurance Co. Ltd.</td>
                        <td>BAJHLIP23020V012223</td>
                        <td>Global Health Care</td>
                        <td>06-05-2022</td>
                        <td>
                            <a href="https://irdai.gov.in/documents/BAJHLIP23020V012223.pdf">BAJHLIP23020V012223.pdf</a>
                            <p class="text-muted">1.3 MB</p>
                        </td>
                        <td>Individual</td>
                    </tr>
                    <tr>
                        <td><input type="checkbox"/></td>
                        <td>Non-Archived</td>
                        <td>2022-2023</td>
                        <td>Cholamandalam MS General Insurance Company Limited</td>
                        <td>CHOTGDP23004V012223</td>
                        <td>Group Domestic Travel Insurance</td>
                        <td>04-04-2022</td>
                        <td>
                            <a href="https://irdai.gov.in/documents/CHOTGDP23004V012223.pdf">CHOTGDP23004V012223.pdf</a>
                            <p class="text-muted">2.3 MB</p>
                        </td>
                        <td>Group</td>
                    </tr>
                    <tr>
                        <td><input type="checkbox"/></td>
                        <td>Non-Archived</td>
                        <td>2022-2023</td>
                        <td>ICICI Lombard GIC Ltd</td>
                        <td></td>
                        <td>Group Criti Shield Plus</td>
                        <td>25-05-2022</td>
                        <td>
                            <a href="https://irdai.gov.in/documents/ICIHLGP23027V012223.pdf">ICIHLGP23027V012223.pdf</a>
                            <p class="text-muted">1.4 MB</p>
                        </td>
                        <td>Group</td>
                    </tr>
                    <tr>
                        <td><input type="checkbox"/></td>
                        <td>Archived</td>
                        <td>2013-2014</td>
                        <td>The New India Assurance Company Limited</td>
                        <td>IRDA/NL-HLT/NIA/P-P/V.I/350/13-14</td>
                        <td>Individual Personal Accident Policy</td>
                        <td>01-11-2013</td>
                        <td>
                            <a href="https://irdai.gov.in/documents/NIA_PA_2013.pdf">NIA_PA_2013.pdf</a>
                            <p class="text-muted">319 KB</p>
                        </td>
                        <td></td>
                    </tr>
                </tbody>
            </table>
            </body>
            </html>
            """;

    @BeforeEach
    void setUp() {
        inventoryService = new IrdaiRegistryInventoryService(registryRepository, runRepository, mongoTemplate);
    }

    @Test
    @DisplayName("Task 4: Parse HTML table and extract complete raw & normalized metadata")
    void testHtmlParsingAndMetadataExtraction() {
        List<IrdaiRegistryRecord> records = inventoryService.parseHtmlPage(
                SAMPLE_PORTAL_HTML, 1, "https://irdai.gov.in/health-insurance-products", "RUN_TEST_001"
        );

        assertNotNull(records);
        assertEquals(4, records.size(), "Should parse exactly 4 product rows (skipping header)");

        // 1. Verify standard individual record
        IrdaiRegistryRecord r1 = records.get(0);
        assertEquals("BAJHLIP23020V012223", r1.getSourceUin());
        assertEquals("BAJHLIP23020V012223", r1.getNormalizedUin());
        assertEquals("Bajaj Allianz General Insurance Co. Ltd.", r1.getInsurerName());
        assertEquals("BAJAJ_ALLIANZ", r1.getNormalizedInsurerCode());
        assertEquals("Global Health Care", r1.getProductName());
        assertEquals("2022-2023", r1.getFinancialYear());
        assertEquals("06-05-2022", r1.getApprovalDate());
        assertEquals("https://irdai.gov.in/documents/BAJHLIP23020V012223.pdf", r1.getDocumentDownloadUrl());
        assertEquals("BAJHLIP23020V012223.pdf", r1.getDocumentFilename());
        assertEquals("1.3 MB", r1.getAdvertisedDocumentSize());
        assertEquals("Individual", r1.getRawProductType());
        assertEquals("Non-Archived", r1.getArchiveStatus());
        assertFalse(r1.isArchived());
        assertEquals("INDIVIDUAL", r1.getClassificationStatus());
        assertEquals("IRDAI_BAJHLIP23020V012223", r1.getId());

        // Verify raw payload preservation
        assertEquals("BAJHLIP23020V012223", r1.getRawSourcePayload().get("uin"));
        assertEquals("Global Health Care", r1.getRawSourcePayload().get("product_name"));
    }

    @Test
    @DisplayName("Task 4 & 7: Missing UIN record does not crash ingestion and retains audit trail")
    void testMissingUinRecordHandling() {
        List<IrdaiRegistryRecord> records = inventoryService.parseHtmlPage(
                SAMPLE_PORTAL_HTML, 1, "https://irdai.gov.in/health-insurance-products", "RUN_TEST_001"
        );

        // Record 3: ICICI Lombard Group Criti Shield Plus has empty UIN
        IrdaiRegistryRecord r3 = records.get(2);
        assertEquals("", r3.getSourceUin());
        assertEquals("", r3.getNormalizedUin());
        assertEquals("ICICI Lombard GIC Ltd", r3.getInsurerName());
        assertEquals("Group Criti Shield Plus", r3.getProductName());
        assertTrue(r3.getId().startsWith("IRDAI_NO_UIN_"), "Stable ID should be deterministically generated for empty UIN");
        assertEquals("BENEFIT_BASED", r3.getClassificationStatus(), "Criti Shield should be classified as benefit-based");
    }

    @Test
    @DisplayName("Task 5 & 7: Idempotent persistence - multiple runs update in-place without duplicating records")
    void testIdempotencyMultipleRunsDoNotDuplicate() {
        IrdaiRegistryRecord record = IrdaiRegistryRecord.builder()
                .id("IRDAI_BAJHLIP23020V012223")
                .normalizedUin("BAJHLIP23020V012223")
                .insurerName("Bajaj Allianz General Insurance Co. Ltd.")
                .productName("Global Health Care")
                .occurrenceCount(1)
                .build();

        // 1. Initial insert
        when(registryRepository.findAllByNormalizedUin("BAJHLIP23020V012223")).thenReturn(Collections.emptyList());
        when(registryRepository.findById("IRDAI_BAJHLIP23020V012223")).thenReturn(Optional.empty());
        when(registryRepository.save(any(IrdaiRegistryRecord.class))).thenAnswer(i -> i.getArgument(0));

        IrdaiRegistryInventoryService.SaveOutcome outcome1 = inventoryService.saveRecordIdempotently(record);
        assertEquals(IrdaiRegistryInventoryService.SaveOutcome.CREATED, outcome1);

        // 2. Second identical observation
        when(registryRepository.findAllByNormalizedUin("BAJHLIP23020V012223")).thenReturn(List.of(record));

        IrdaiRegistryInventoryService.SaveOutcome outcome2 = inventoryService.saveRecordIdempotently(record);
        assertEquals(IrdaiRegistryInventoryService.SaveOutcome.DUPLICATE, outcome2);
        assertEquals(2, record.getOccurrenceCount(), "Occurrence count should increment");
        assertEquals("DUPLICATE_IDENTICAL", record.getDuplicateStatus());
    }

    @Test
    @DisplayName("Task 5: Conflicting records with identical UIN but differing product/insurer are flagged")
    void testConflictingRecordsPreservedAndFlagged() {
        IrdaiRegistryRecord existing = IrdaiRegistryRecord.builder()
                .id("IRDAI_MAGHLGP2123V022021")
                .normalizedUin("MAGHLGP2123V022021")
                .insurerName("Liberty General Insurance Ltd.")
                .productName("Corona Rakshak Policy")
                .duplicateStatus("UNIQUE")
                .conflictDetails(new ArrayList<>())
                .build();

        IrdaiRegistryRecord incomingConflict = IrdaiRegistryRecord.builder()
                .id("IRDAI_MAGHLGP2123V022021")
                .normalizedUin("MAGHLGP2123V022021")
                .insurerName("Magma HDI General Insurance Co. Ltd.")
                .productName("Group Health Insurance")
                .duplicateStatus("UNIQUE")
                .conflictDetails(new ArrayList<>())
                .build();

        when(registryRepository.findAllByNormalizedUin("MAGHLGP2123V022021")).thenReturn(List.of(existing));
        when(registryRepository.save(any(IrdaiRegistryRecord.class))).thenAnswer(i -> i.getArgument(0));

        IrdaiRegistryInventoryService.SaveOutcome outcome = inventoryService.saveRecordIdempotently(incomingConflict);

        assertEquals(IrdaiRegistryInventoryService.SaveOutcome.CONFLICT, outcome);
        assertEquals("CONFLICT_DETECTED", existing.getDuplicateStatus());
        assertEquals("CONFLICT_DETECTED", incomingConflict.getDuplicateStatus());
        assertTrue(incomingConflict.getId().contains("_CONF_"), "Conflict record must have distinct ID");
        assertFalse(existing.getConflictDetails().isEmpty());
    }

    @Test
    @DisplayName("Task 6: Derived classification engine accurately distinguishes health insurance types")
    void testClassificationEngine() {
        assertEquals("GROUP", inventoryService.deriveClassification("Group", "Group Health Care", "BAJHLGP23001", false));
        assertEquals("PERSONAL_ACCIDENT", inventoryService.deriveClassification("", "Janata Personal Accident", "IRDA/NL-HLT/RGI/P-P/V.I/322/13-14", false));
        assertEquals("TRAVEL", inventoryService.deriveClassification("", "Pravasi Bhartiya Bima Yojana", "IRDA/NL-HLT/RGI/P-H/V.I/319/13-14", false));
        assertEquals("TRAVEL", inventoryService.deriveClassification("Group", "Group Domestic Travel Insurance", "CHOTGDP23004V012223", false));
        assertEquals("BENEFIT_BASED", inventoryService.deriveClassification("", "Critical Illness Insurance Policy", "IRDA/NL-HLT/SBIGI/P-H(C)/V.I/42/13-14", false));
        assertEquals("RIDER_ADDON", inventoryService.deriveClassification("Add on", "Well Baby Well Mother -Add on", "EDLHLGA23009V012223", false));
        assertEquals("FAMILY_FLOATER", inventoryService.deriveClassification("", "Happy Family Floater Policy", "OICHLIP21226V022021", false));
        assertEquals("INDIVIDUAL", inventoryService.deriveClassification("Individual", "Global Health Care", "BAJHLIP23020V012223", false));
        assertEquals("ARCHIVED", inventoryService.deriveClassification("Individual", "Global Health Care", "BAJHLIP23020V012223", true));
        assertEquals("UNKNOWN_UNCLASSIFIED", inventoryService.deriveClassification("", "Miscellaneous Special Wording", "XYZ12345", false));
    }

    @Test
    @DisplayName("Task 8: Inventory summary statistics aggregation")
    void testInventorySummaryAggregation() {
        when(registryRepository.count()).thenReturn(1820L);
        when(registryRepository.countByArchived(true)).thenReturn(0L);
        when(registryRepository.countByArchived(false)).thenReturn(1820L);
        when(registryRepository.countByDuplicateStatus("DUPLICATE_IDENTICAL")).thenReturn(15L);
        when(registryRepository.countByDuplicateStatus("CONFLICT_DETECTED")).thenReturn(21L);
        when(registryRepository.countMissingUins()).thenReturn(1L);
        when(registryRepository.countMissingDocumentLinks()).thenReturn(0L);
        when(registryRepository.countByClassificationStatus("INDIVIDUAL")).thenReturn(600L);
        when(registryRepository.countByClassificationStatus("GROUP")).thenReturn(350L);
        when(registryRepository.findAll()).thenReturn(Collections.emptyList());

        PolicyDiscoveryRun run = PolicyDiscoveryRun.builder()
                .id("RUN_20261031_001")
                .status("COMPLETED")
                .completedAt(Instant.now())
                .build();
        when(runRepository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.of(run));

        IrdaiRegistryInventoryService.InventorySummaryDto summary = inventoryService.getInventorySummary();

        assertNotNull(summary);
        assertEquals(1820L, summary.getTotalRecords());
        assertEquals(1820L, summary.getNonArchivedCount());
        assertEquals(0L, summary.getArchivedCount());
        assertEquals(15L, summary.getDuplicateCount());
        assertEquals(21L, summary.getConflictCount());
        assertEquals(1L, summary.getMissingUinCount());
        assertEquals(0L, summary.getMissingDocumentLinkCount());
        assertEquals("COMPLETED", summary.getLastRunStatus());
    }

    @Test
    @DisplayName("Task 8: Paginated search and filtering query over registry records")
    void testSearchRegistry() {
        IrdaiRegistryRecord record = IrdaiRegistryRecord.builder()
                .id("IRDAI_BAJHLIP23020V012223")
                .normalizedUin("BAJHLIP23020V012223")
                .insurerName("Bajaj Allianz")
                .productName("Global Health Care")
                .build();

        Page<IrdaiRegistryRecord> expectedPage = new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1);
        when(mongoTemplate.count(any(Query.class), eq(IrdaiRegistryRecord.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(IrdaiRegistryRecord.class))).thenReturn(List.of(record));

        Page<IrdaiRegistryRecord> result = inventoryService.searchRegistry(
                "Bajaj", "BAJHLIP", "BAJAJ", "Global", "2022-2023", false, "INDIVIDUAL", PageRequest.of(0, 20)
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("BAJHLIP23020V012223", result.getContent().get(0).getNormalizedUin());
    }
}
