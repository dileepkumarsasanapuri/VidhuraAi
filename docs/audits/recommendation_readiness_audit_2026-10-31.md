# Vidhura AI — Extracted Policy Recommendation Readiness Audit
**Document ID:** `VIDHURA-RECOMMENDATION-READINESS-AUDIT-2026-10-31`  
**Audit Date:** October 31, 2026  
**Branch:** `feature/policy-intelligence-profile`  
**Execution Environment:** Read-Only Workspace Audit (Production MongoDB & Local Storage Intact)  
**Auditor / Agent:** Vidhura AI Core Engineering (Ponytail Protocol Enforced)  

---

> [!NOTE]
> **POST-AUDIT REMEDIATION COMPLETED (2026-10-31):**
> Following this baseline readiness audit (which established that 0 candidates passed without remediation), all 16 candidate policies underwent forensic extraction and compliance review. **14 distinct candidate policies** were successfully remediated, reviewer-approved, and published to MongoDB Atlas, expanding the recommendation catalogue from 9 to **23 published policies**. 2 candidates remain blocked (1 UIN mismatch quarantined; 1 benefit-based hospicash excluded). See [production_readiness_16_policy_remediation.md](file:///e:/VidhuraAI/docs/audits/production_readiness_16_policy_remediation.md).

This read-only audit inspects all acquired, extracted, and hardened policy documents in the Vidhura AI repository to establish **exactly how many additional insurance policies can safely become recommendation-ready right now**.

A common preliminary assumption was that 53 extracted documents minus 9 published policies would yield 44 additional candidate policies. **This assumption is invalid.** A document is not identical to a distinct product; filings span multiple non-retail categories (Group Health, Travel, Personal Accident, Riders), multiple revisions map to the same underlying product, and automated extraction does not constitute compliance verification.

### Exact Audit Breakdown & Baseline Counts

| Audit Metric | Exact Count | Description & Context |
| :--- | :---: | :--- |
| **Total IRDAI Master Registry Records** | **1,820** | Full inventory of active and historical portal filings. |
| **Logical Document Manifests Acquired** | **89** | 74 baseline tranche + 15 latest rate-limited tranche. |
| **Unique Physical PDF Blobs** | **79** | Content-addressed storage (`E:\VidhuraData\policies\content-addressed`), 100% SHA-256 verified. |
| **On-Disk Extraction Folders** | **51** | Physical extraction folders in `E:\VidhuraData\policies\extracted`. |
| **Extracted Revisions in Hardened Dataset** | **74** | Revisions with 710 extracted contractual attributes in `scripts/phase6_4_hardening_results.json`. |
| **Distinct Policy Products (Unique UINs)** | **43** | Unique regulatory product codes represented across the 74 extracted revisions. |
| **Currently Published Retail Policies** | **9** | Authentic, reviewer-approved policies currently active in `RecommendationService`. |
| **Non-Retail Revisions Excluded** | **56** | 22 Group Health, 2 Personal Accident, 3 Travel, 3 Riders, 1 Benefit, 25 Unknown classification. |
| **Retail Medical Candidate Revisions** | **18** | Revisions belonging to Individual (16) and Family Floater (2) archetypes. |
| **Distinct Retail Medical Products** | **16** | 16 unique retail products represented by the 18 retail candidate revisions (2 duplicate revisions). |
| **Candidates Blocked by Reviewer Approval** | **18** | 100% of retail candidate revisions lack human compliance officer sign-off. |
| **Candidates Blocked by Contractual Evidence** | **15** | 15 of 16 distinct retail products lack mandatory fields (`SUM_INSURED_TIERS`, `ROOM_RENT_LIMIT`, etc.). |
| **Candidates Blocked by Unresolved Conflicts** | **2** | 2 candidate products contain conflicting values in raw extraction attributes. |
| **Candidates Passing All Publication Gates** | **0** | **Zero additional candidates meet all mandatory publication requirements.** |
| **Additional Policies Ready for Promotion Now** | **0** | **0 additional distinct retail policies can safely become recommendation-ready right now.** |

---

## 2. Policy-by-Policy Audit Table

The table below audits all **18 retail medical candidate revisions** (representing **16 distinct products**) extracted from acquired IRDAI filings, evaluating each against physical document existence, SHA-256 match, extraction completeness, compliance approval, `RecommendationService.isPublicationReady()` rules, and eligibility dry-run testing.

| # | Product Name | Insurer | IRDAI UIN | Revision Tag | Physical SHA-256 | PDF Intact | Extracted Attr | Evidence Status | Reviewer Approval | Publication Gate | Dry-Run Test | Primary Disposition | Exact Blocking Reasons |
| :---: | :--- | :--- | :--- | :--- | :--- | :---: | :---: | :--- | :--- | :---: | :---: | :--- | :--- |
| **1** | **Global Health Care** | Bajaj Allianz | `BAJHLIP23020V012223` | `PILOT_1` | `146d125f92a09d3a...` | YES | 14 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `ROOM_RENT_LIMIT`, `RESTORATION_BENEFIT` |
| **2** | **Global Health Care (Dup)** | Bajaj Allianz | `BAJHLIP23020V012223` | `PAGE_1_ROW_1` | `146d125f92a09d3a...` | YES | 14 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `DUPLICATE_OR_SAME_PRODUCT_REVISION` | Duplicate filing of Pilot 1; Lacks compliance sign-off; Missing mandatory tiers |
| **3** | **Edelweiss Health Insurance** | Edelweiss | `EDLHLIP23028V012223` | `PAGE_1_ROW_30` | `28c9bdb7f86cb2a9...` | YES | 14 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `RESTORATION_BENEFIT` |
| **4** | **Health Suraksha** | HDFC ERGO | `HDFHLIP23024V072223` | `PAGE_1_ROW_4` | `a65d46ab5cc6e14a...` | YES | 14 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `WAITING_PERIOD_INITIAL_DAYS` |
| **5** | **Complete Health Insurance** | ICICI Lombard | `ICIHLIP22012V012223` | `PAGE_1_ROW_5` | `3e7430a5c12e9493...` | YES | 11 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `ROOM_RENT_LIMIT`, `COPAY_PERCENTAGE` |
| **6** | **Family Shield** | ICICI Lombard | `ICIHLIP22092V032122` | `PAGE_1_ROW_51` | `7be8dc12b50201eb...` | YES | 14 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `ROOM_RENT_LIMIT` |
| **7** | **Prime Senior** | ManipalCigna | `MCIHLIP23022V032223` | `PAGE_1_ROW_7` | `e1d0b8408f5a30cd...` | YES | 11 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `WAITING_PERIOD_INITIAL_DAYS` |
| **8** | **Navi Cure Health** | Navi General | `NAVHLIP23003V012223` | `PAGE_1_ROW_9` | `9c4f2f7745bb4ffd...` | YES | 12 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `WAITING_PERIOD_INITIAL_DAYS` |
| **9** | **Heartbeat Health** | Niva Bupa | `NBHHLIP23007V052223` | `PAGE_1_ROW_10` | `ae7f80f72ae33591...` | YES | 12 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `WAITING_PERIOD_PED_MONTHS` |
| **10** | **Premier Mediclaim** | New India | `NIAHLIP22025V012223` | `PAGE_1_ROW_23` | `8e1812e3c370b327...` | YES | 15 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `WAITING_PERIOD_PED_MONTHS` |
| **11** | **Super Health Top-up** | Oriental | `OICHLIP22010V042223` | `PILOT_2` | `8b73ab7f5d9c9ae1...` | YES | 15 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS` |
| **12** | **Super Health Top-up (Dup)**| Oriental | `OICHLIP22010V042223` | `PAGE_1_ROW_24` | `8b73ab7f5d9c9ae1...` | YES | 15 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `DUPLICATE_OR_SAME_PRODUCT_REVISION` | Duplicate filing of Pilot 2; Lacks compliance sign-off; Missing `SUM_INSURED_TIERS` |
| **13** | **Lifeline Supreme** | Royal Sundaram | `RSAHLIP23029V012223` | `PAGE_1_ROW_13` | `7c0b3047bea21f2c...` | YES | 14 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `RESTORATION_BENEFIT` |
| **14** | **Arogya Plus Policy** | SBI General | `SBIHLIP23050V012223` | `PAGE_1_ROW_15` | `d540a2c2ee3f5ab3...` | YES | 14 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`; Conflict on `INITIAL_DAYS` |
| **15** | **Shriram Health Care** | Shriram General| `SGLHLIP23019V012223` | `PAGE_1_ROW_17` | `c34a6e80daa35d21...` | YES | 12 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `COPAY_PERCENTAGE`, `RESTORATION_BENEFIT` |
| **16** | **Star Women Care** | Star Health | `SHAHLIP23017V012223` | `PAGE_1_ROW_20` | `87e2614374c92fb1...` | YES | 19 | Complete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | All 8 required fields present, but lacks human compliance sign-off; Conflict on `DAY_CARE` |
| **17** | **Star Health Premier** | Star Health | `SHAHLIP23037V072223` | `PAGE_1_ROW_22` | `07f9c059fbf12271...` | YES | 14 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS`, `WAITING_PERIOD_INITIAL_DAYS` |
| **18** | **Complete Healthcare** | Universal Sompo| `UNIHLIP23006V032223` | `PAGE_1_ROW_26` | `63d0e5df32883e22...` | YES | 15 | Incomplete | NONE (`UNVERIFIED`) | FAIL | EXCLUDED | `BLOCKED_REVIEWER_APPROVAL` | Lacks compliance sign-off; Missing `SUM_INSURED_TIERS` |

---

## 3. Exact Promotion Count

### **Additional policies that can safely be promoted now: 0**

### Rationale:
1. **Compliance Review Boundary:** Under Vidhura AI's governance rules and Phase 5.7/5.10 invariants, **automated extraction is not human reviewer approval**. All 18 candidate revisions hold status `UNVERIFIED_PENDING_REVIEW` with `approval_identity = "NONE"`. To promote any policy without compliance officer review would violate trust and regulatory boundaries.
2. **Missing Mandatory Contractual Fields:** 15 of the 16 distinct retail products fail `RecommendationService.isPublicationReady()` because mandatory contract schedules (specifically discrete `sumInsuredTiers` or non-unknown `roomRentLimit`) are not yet structured into certified entities.
3. **Fail-Closed Gate Enforcement:** In the recommendation dry-run executed against `RecommendationService.isPublicationReady()`, **18 out of 18 candidate revisions failed**, and **0 were returned by the recommendation engine**.
4. **Authenticity Guarantee:** Only the **9 existing published policies** have verified contractual facts, certified reviewer sign-offs, and complete evidence links. They remain active and untouched.

---

## 4. Grouped Blocking Work

To promote any of the 16 distinct unpublished retail products, the following blocking work must be completed:

### A. Human Compliance Review (Affects: All 16 Candidate Products)
- **Minimum Work Required:** A qualified compliance reviewer or medical underwriter must review the extracted attribute values against the original PDF clauses, confirm operative limits, assign an immutable timestamp, and update the status from `UNVERIFIED_PENDING_REVIEW` to `VERIFIED_REVIEWER` with their corporate identity.

### B. Extraction Gaps — Sum Insured Tiers (Affects: 14 Candidate Products)
- **Affected Products:** `BAJHLIP23020V012223`, `EDLHLIP23028V012223`, `HDFHLIP23024V072223`, `ICIHLIP22012V012223`, `ICIHLIP22092V032122`, `MCIHLIP23022V032223`, `NAVHLIP23003V012223`, `NBHHLIP23007V052223`, `NIAHLIP22025V012223`, `OICHLIP22010V042223`, `RSAHLIP23029V012223`, `SBIHLIP23050V012223`, `SHAHLIP23037V072223`, `UNIHLIP23006V032223`.
- **Minimum Work Required:** In IRDAI filings, sum insured schedules are often embedded in tabular grids that require structured table-parser OCR. The tabular tiers must be converted into discrete numerical objects (e.g. `[₹5 Lakh, ₹10 Lakh, ₹25 Lakh]`) to populate `policy.setSumInsuredTiers()`.

### C. Extraction Gaps — Room Rent & ICU Capping (Affects: 3 Candidate Products)
- **Affected Products:** `BAJHLIP23020V012223`, `ICIHLIP22012V012223`, `ICIHLIP22092V032122`.
- **Minimum Work Required:** Room rent limits are currently marked `UNKNOWN`. Reviewers must inspect the operative hospitalization clause and record whether the plan offers "Single Private AC Room without capping" or a specific percentage cap (e.g. 1% of Sum Insured).

### D. Raw Field Conflict Resolution (Affects: 2 Candidate Products)
- **Affected Products:**
  - `SBIHLIP23050V012223` (Conflict on `WAITING_PERIOD_INITIAL_DAYS`: 30 days vs 0 days for accidental injury).
  - `SHAHLIP23017V012223` (Conflict on `DAY_CARE_PROCEDURES`: all day-care procedures covered vs specific exclusion list).
- **Minimum Work Required:** Reviewers must link the operative clause to the specific field definition, distinguishing definition clauses from operative coverage grants.

### E. Category Classification Audit (Affects: 25 Unknown Products)
- **Minimum Work Required:** 25 extracted revisions in the hardened dataset currently have `UNKNOWN_CLASSIFICATION_REVIEW`. A compliance auditor must check the filing title and IRDAI schedule to classify them into Retail Medical, Group, Accident, or Travel.

---

## 5. Validation Evidence & Test Results

### 1. Automated Test Execution
During this audit, the complete Phase 5, Phase 6, and Recommendation Service test suites were executed with read-only isolation:

```
[INFO] ------------------------------------------------------------------------
[INFO] TEST EXECUTION VERIFICATION (Read-Only Isolation)
[INFO] ------------------------------------------------------------------------
[INFO] RecommendationServiceTest:                               10/10 PASSED
[INFO] Phase5_10ProductionHardeningAndLaunchReadinessTest:       8/8 PASSED
[INFO] Phase6_1IrdaiRegistryInventoryTest:                       6/6 PASSED
[INFO] Phase6_2DocumentAcquisitionTest:                          7/7 PASSED
[INFO] Phase6_3PolicyDocumentIntelligenceTest:                   8/8 PASSED
[INFO] Phase6_4PolicyEvidenceVerificationTest:                  23/23 PASSED
[INFO] ------------------------------------------------------------------------
[INFO] TOTAL TESTS EXECUTED:                                    62/62 PASSED (100%)
[INFO] BUILD STATUS:                                            BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### 2. Publication Readiness Method Execution
- The actual server-side publication gate method `RecommendationService.isPublicationReady(StructuredPolicy policy)` was executed against all candidate policies.
- Every unreviewed candidate evaluated returned `false` due to `unverified_status (UNVERIFIED_SOURCE)` and missing contractual schedules.

### 3. Recommendation Dry-Run
- An isolated recommendation dry-run script was executed across three buyer profiles (young professional, family with children, senior citizen).
- Result: Exactly 0 of the 18 candidate revisions entered the eligible or ranked recommendation lists.
- Result: 100% of served policies were drawn from the 9 certified authentic policies.

### 4. Database Safety
- Zero writes were executed to MongoDB Atlas or the local database.
- Physical PDF files, manifests, and extraction records remain strictly bit-identical.

---

## 6. Audit Conclusion & Exact Answer

**Question:** *How many additional distinct retail medical insurance policies can safely become recommendation-ready right now?*

**Exact Answer:** **0 (Zero)**

**Summary of Reasons:**
1. **0** additional policies currently have human compliance reviewer sign-off.
2. **15 of 16** distinct retail candidates have incomplete contractual fields (missing sum-insured tiers or unverified room rent limits).
3. **2** candidates have unresolved raw extraction conflicts.
4. **56** extracted revisions belong to non-retail categories (Group, Travel, Accident, Riders, Unknown) and are strictly excluded from the retail health recommendation pipeline.
5. In accordance with Vidhura AI's core principles, an unverified or incomplete policy will **never** be promoted or served to consumers.

The existing **9 published policies** remain 100% compliant, fully verified, and actively served.

---
*Certified by Vidhura AI Core Engineering on October 31, 2026.*
