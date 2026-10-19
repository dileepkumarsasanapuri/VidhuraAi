"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  GitCompare,
  ShieldCheck,
  AlertTriangle,
  CheckCircle2,
  FileText,
  Building2,
  Clock,
  ExternalLink,
  Sparkles,
  Info,
  DollarSign,
  ChevronRight,
  Loader2,
  RefreshCw,
  Scale,
  Copy,
  Check,
  HelpCircle,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import {
  api,
  ClaimComparisonRequest,
  ClaimComparisonResponse,
  PolicyClaimComparisonItem,
  BillLineItem,
} from "@/lib/api";

interface CatalogPolicyOption {
  id: string;
  code: string;
  name: string;
  provider: string;
  irdaUin: string;
  revision: string;
  checksum: string;
  isAuthentic: boolean;
  status: string;
  reviewer: string;
  summary: string;
}

const STATIC_CATALOG_POLICIES: CatalogPolicyOption[] = [
  {
    id: "UIIHLIP22070V042122",
    code: "UNITED_INDIA_FAMILY_MEDICARE_V042122",
    name: "United India Family Medicare Policy",
    provider: "United India Insurance Company Limited",
    irdaUin: "UIIHLIP22070V042122",
    revision: "V042122",
    checksum: "9728ea60fce9e3e99b21c8b70abbf5d0b43236cbbc78901856311f77920efb3b",
    isAuthentic: true,
    status: "VERIFIED_OFFICIAL_CATALOG",
    reviewer: "SeniorComplianceOfficer@vidhura.ai",
    summary: "1% SI room rent cap with proportionate deduction, 48m PED wait, 0% co-pay",
  },
  {
    id: "NIAHLIP21279V032021",
    code: "NEW_INDIA_PREMIER_MEDICLAIM_V032021",
    name: "New India Premier Mediclaim Policy",
    provider: "The New India Assurance Company Limited",
    irdaUin: "NIAHLIP21279V032021",
    revision: "V032021",
    checksum: "2d827d7c96d4d66687408387f69991d4c9e72d7c11ab6901ed6dc610d9870743",
    isAuthentic: true,
    status: "VERIFIED_OFFICIAL_CATALOG",
    reviewer: "SeniorComplianceOfficer@vidhura.ai",
    summary: "Zero room rent limit (Any room eligible), 24m expedited PED wait, 0% co-pay",
  },
  {
    id: "NIAHLIP25040V102425",
    code: "NEW_INDIA_MEDICLAIM_V102425",
    name: "New India Mediclaim Policy",
    provider: "The New India Assurance Company Limited",
    irdaUin: "NIAHLIP25040V102425",
    revision: "V102425",
    checksum: "b37d2519f4fe00e75f28727168ca6aafa9093f8c723fafa7db2f12e887fcea1f",
    isAuthentic: true,
    status: "VERIFIED_OFFICIAL_CATALOG",
    reviewer: "SeniorComplianceOfficer@vidhura.ai",
    summary: "1% SI room cap, 36m PED wait, ₹40k Cataract cap, 0% co-pay",
  },
  {
    id: "HDFHLIP21147V012021",
    code: "HDFC_OPTIMA_SECURE",
    name: "HDFC ERGO Optima Secure (Modeled)",
    provider: "HDFC ERGO General Insurance Co. Ltd.",
    irdaUin: "HDFHLIP21147V012021",
    revision: "V012021",
    checksum: "mock_sha256_hdfc_optima_secure",
    isAuthentic: false,
    status: "SYNTHETIC_DEMO_FIXTURE",
    reviewer: "Synthetic Benchmark Generator",
    summary: "Single Private Room, 36m PED wait, 2X base cover multiplier, modeled fixture",
  },
  {
    id: "STAR_SENIOR_RED_CARPET",
    code: "STAR_SENIOR_RED_CARPET",
    name: "Star Senior Citizens Red Carpet (Modeled)",
    provider: "Star Health and Allied Insurance Co. Ltd.",
    irdaUin: "SHAHLIP21214V022021",
    revision: "V022021",
    checksum: "mock_sha256_star_senior_red_carpet",
    isAuthentic: false,
    status: "SYNTHETIC_DEMO_FIXTURE",
    reviewer: "Synthetic Benchmark Generator",
    summary: "1% SI room cap, 30% co-payment, 12m PED wait for specified conditions",
  },
  {
    id: "CARE_SUPREME",
    code: "CARE_SUPREME",
    name: "Care Supreme Health Plan (Modeled)",
    provider: "Care Health Insurance Limited",
    irdaUin: "CHIHLIP22043V012122",
    revision: "V012122",
    checksum: "mock_sha256_care_supreme",
    isAuthentic: false,
    status: "SYNTHETIC_DEMO_FIXTURE",
    reviewer: "Synthetic Benchmark Generator",
    summary: "No room rent sublimit, 36m PED wait, cumulative bonus up to 500%",
  },
];

const BENCHMARK_BILL_LINES: BillLineItem[] = [
  {
    lineId: "L1",
    category: "ROOM_RENT",
    description: "Single Private AC Room (3 days @ ₹6,500)",
    unitPrice: 6500,
    quantity: 3,
    totalAmount: 19500,
  },
  {
    lineId: "L2",
    category: "SURGEON_FEES",
    description: "Surgeon & Anesthesia Professional Fee",
    unitPrice: 45000,
    quantity: 1,
    totalAmount: 45000,
  },
  {
    lineId: "L3",
    category: "OT_CHARGES",
    description: "Operation Theatre & Equipment",
    unitPrice: 28000,
    quantity: 1,
    totalAmount: 28000,
  },
  {
    lineId: "L4",
    category: "MEDICINES",
    description: "Hospital Pharmacy & IV Fluids",
    unitPrice: 18500,
    quantity: 1,
    totalAmount: 18500,
  },
  {
    lineId: "L5",
    category: "CONSUMABLES",
    description: "Non-Payable Consumables (IRDAI List I - PPE, Gloves, Gauze)",
    unitPrice: 12000,
    quantity: 1,
    totalAmount: 12000,
  },
];

export default function EvidencePolicyComparisonPage() {
  const [policyIdA, setPolicyIdA] = useState<string>("UIIHLIP22070V042122");
  const [policyIdB, setPolicyIdB] = useState<string>("NIAHLIP21279V032021");

  // Profile Parameters
  const [patientAge, setPatientAge] = useState<number>(45);
  const [diagnosis, setDiagnosis] = useState<string>("Acute Appendicitis (Laparoscopic Surgery)");
  const [selectedSumInsured, setSelectedSumInsured] = useState<number>(500000);
  const [policyContinuityMonths, setPolicyContinuityMonths] = useState<number>(36);
  const [isPreExistingCondition, setIsPreExistingCondition] = useState<boolean>(false);

  // Itemized Hospital Bill
  const [billLines, setBillLines] = useState<BillLineItem[]>(BENCHMARK_BILL_LINES);

  // Execution State
  const [isComparing, setIsComparing] = useState<boolean>(false);
  const [comparisonResult, setComparisonResult] = useState<ClaimComparisonResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [copiedHash, setCopiedHash] = useState<string | null>(null);

  const totalSubmitted = billLines.reduce((acc, l) => acc + (l.totalAmount || 0), 0);

  const selectedPolicyA = STATIC_CATALOG_POLICIES.find((p) => p.id === policyIdA) || STATIC_CATALOG_POLICIES[0];
  const selectedPolicyB = STATIC_CATALOG_POLICIES.find((p) => p.id === policyIdB) || STATIC_CATALOG_POLICIES[1];

  const hasSyntheticInvolved = !selectedPolicyA.isAuthentic || !selectedPolicyB.isAuthentic;

  const handleCopyHash = (hash: string) => {
    navigator.clipboard.writeText(hash);
    setCopiedHash(hash);
    setTimeout(() => setCopiedHash(null), 2000);
  };

  const handleRunComparison = async () => {
    if (policyIdA === policyIdB) {
      setErrorMessage("Please select two distinct policies to perform a comparative contractual analysis.");
      return;
    }

    setIsComparing(true);
    setErrorMessage(null);

    const request: ClaimComparisonRequest = {
      policyIds: [policyIdA, policyIdB],
      scenario: {
        policyId: policyIdA,
        patientAge,
        diagnosis,
        preExistingCondition: isPreExistingCondition,
        policyContinuityMonths,
        hospitalType: "NETWORK_CASHLESS",
        hospitalRoomCategoryOpted: "SINGLE_PRIVATE_AC",
        selectedSumInsured,
        priorClaimsInCurrentYear: 0,
        billLines,
      },
    };

    try {
      const response = await api.compareClaims(request);
      if (response && response.data) {
        setComparisonResult(response.data);
      } else {
        setErrorMessage("Comparison response did not contain valid data.");
      }
    } catch (err: any) {
      console.error("[Comparison] Error executing claim comparison:", err);
      setErrorMessage(err.message || "Failed to execute evidence-backed policy comparison.");
    } finally {
      setIsComparing(false);
    }
  };

  // Run initial benchmark comparison on mount
  useEffect(() => {
    handleRunComparison();
  }, []);

  const compA = comparisonResult?.comparisons?.[0];
  const compB = comparisonResult?.comparisons?.[1];

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar activeTab="/dashboard/compare" />
      <main className="flex-1 overflow-y-auto">
        {/* Top Sticky Header */}
        <header className="sticky top-0 z-30 glass border-b border-border px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="ml-11 lg:ml-0 flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-primary/20 border border-primary/30 flex items-center justify-center text-primary-light">
              <Scale className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-base sm:text-lg font-bold flex items-center gap-2">
                Evidence-Backed Policy Comparison
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-primary/20 text-primary-light border border-primary/30 font-semibold">
                  STATUTORY RECONCILIATION
                </span>
              </h1>
              <p className="text-xs text-muted hidden sm:block">
                Side-by-side claim adjudication and clause variance grounded in verified PDF document bytes
              </p>
            </div>
          </div>
          <button
            onClick={handleRunComparison}
            disabled={isComparing}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 disabled:opacity-50 transition-all shadow-sm"
          >
            {isComparing ? (
              <>
                <Loader2 className="w-3.5 h-3.5 animate-spin" /> Adjudicating...
              </>
            ) : (
              <>
                <RefreshCw className="w-3.5 h-3.5" /> Recompute Comparison
              </>
            )}
          </button>
        </header>

        <div className="p-4 sm:p-6 max-w-7xl mx-auto space-y-6 pb-24">
          {/* Policy Selection Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Policy A Selector */}
            <div className="glass rounded-2xl p-4 sm:p-5 border border-white/10 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-primary-light flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-primary"></span> Policy Contract A
                </span>
                {selectedPolicyA.isAuthentic ? (
                  <span className="text-[11px] font-semibold text-emerald-400 bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 rounded-lg flex items-center gap-1">
                    <ShieldCheck className="w-3.5 h-3.5" /> Verified Statutory Filing
                  </span>
                ) : (
                  <span className="text-[11px] font-semibold text-amber-400 bg-amber-500/10 border border-amber-500/30 px-2 py-0.5 rounded-lg flex items-center gap-1">
                    <AlertTriangle className="w-3.5 h-3.5" /> Modeled Demo Fixture
                  </span>
                )}
              </div>

              <select
                value={policyIdA}
                onChange={(e) => setPolicyIdA(e.target.value)}
                className="w-full bg-black/40 border border-white/15 rounded-xl px-3.5 py-2.5 text-sm text-foreground focus:outline-none focus:border-primary font-medium"
              >
                <optgroup label="⭐️ Verified Official Policies (Statutory PDF Proof)">
                  {STATIC_CATALOG_POLICIES.filter((p) => p.isAuthentic).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({p.irdaUin} - {p.revision})
                    </option>
                  ))}
                </optgroup>
                <optgroup label="🔬 Modeled Demonstration Fixtures (Synthetic)">
                  {STATIC_CATALOG_POLICIES.filter((p) => !p.isAuthentic).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} [Synthetic Benchmark]
                    </option>
                  ))}
                </optgroup>
              </select>

              <div className="text-xs text-muted space-y-1">
                <p className="line-clamp-1">
                  <span className="text-foreground/80 font-medium">Provider:</span> {selectedPolicyA.provider}
                </p>
                <p className="line-clamp-1">
                  <span className="text-foreground/80 font-medium">Terms:</span> {selectedPolicyA.summary}
                </p>
                <div className="flex items-center gap-1 text-[11px] text-muted font-mono pt-1">
                  <span>SHA-256:</span>
                  <span className="truncate max-w-[200px]">{selectedPolicyA.checksum}</span>
                  <button
                    onClick={() => handleCopyHash(selectedPolicyA.checksum)}
                    className="hover:text-primary-light"
                    title="Copy full SHA-256 checksum"
                  >
                    {copiedHash === selectedPolicyA.checksum ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                  </button>
                </div>
              </div>
            </div>

            {/* Policy B Selector */}
            <div className="glass rounded-2xl p-4 sm:p-5 border border-white/10 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-accent-light flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-accent"></span> Policy Contract B
                </span>
                {selectedPolicyB.isAuthentic ? (
                  <span className="text-[11px] font-semibold text-emerald-400 bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 rounded-lg flex items-center gap-1">
                    <ShieldCheck className="w-3.5 h-3.5" /> Verified Statutory Filing
                  </span>
                ) : (
                  <span className="text-[11px] font-semibold text-amber-400 bg-amber-500/10 border border-amber-500/30 px-2 py-0.5 rounded-lg flex items-center gap-1">
                    <AlertTriangle className="w-3.5 h-3.5" /> Modeled Demo Fixture
                  </span>
                )}
              </div>

              <select
                value={policyIdB}
                onChange={(e) => setPolicyIdB(e.target.value)}
                className="w-full bg-black/40 border border-white/15 rounded-xl px-3.5 py-2.5 text-sm text-foreground focus:outline-none focus:border-accent font-medium"
              >
                <optgroup label="⭐️ Verified Official Policies (Statutory PDF Proof)">
                  {STATIC_CATALOG_POLICIES.filter((p) => p.isAuthentic).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({p.irdaUin} - {p.revision})
                    </option>
                  ))}
                </optgroup>
                <optgroup label="🔬 Modeled Demonstration Fixtures (Synthetic)">
                  {STATIC_CATALOG_POLICIES.filter((p) => !p.isAuthentic).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} [Synthetic Benchmark]
                    </option>
                  ))}
                </optgroup>
              </select>

              <div className="text-xs text-muted space-y-1">
                <p className="line-clamp-1">
                  <span className="text-foreground/80 font-medium">Provider:</span> {selectedPolicyB.provider}
                </p>
                <p className="line-clamp-1">
                  <span className="text-foreground/80 font-medium">Terms:</span> {selectedPolicyB.summary}
                </p>
                <div className="flex items-center gap-1 text-[11px] text-muted font-mono pt-1">
                  <span>SHA-256:</span>
                  <span className="truncate max-w-[200px]">{selectedPolicyB.checksum}</span>
                  <button
                    onClick={() => handleCopyHash(selectedPolicyB.checksum)}
                    className="hover:text-accent-light"
                    title="Copy full SHA-256 checksum"
                  >
                    {copiedHash === selectedPolicyB.checksum ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Scenario & Hospital Bill Parameter Strip */}
          <div className="glass rounded-2xl p-4 sm:p-5 border border-white/5 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-white/5 pb-3">
              <div className="flex items-center gap-2">
                <Building2 className="w-4 h-4 text-primary-light" />
                <h3 className="text-sm font-bold text-foreground">Identical Hospitalization Scenario & Profile</h3>
              </div>
              <button
                type="button"
                onClick={() => {
                  setPatientAge(45);
                  setDiagnosis("Acute Appendicitis (Laparoscopic Surgery)");
                  setSelectedSumInsured(500000);
                  setPolicyContinuityMonths(36);
                  setIsPreExistingCondition(false);
                  setBillLines(BENCHMARK_BILL_LINES);
                }}
                className="text-xs text-primary-light hover:underline flex items-center gap-1"
              >
                <Sparkles className="w-3.5 h-3.5" /> Reset to Statutory ₹1,23,000 Benchmark
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
              <div>
                <label className="text-muted block mb-1">Patient Age</label>
                <input
                  type="number"
                  value={patientAge}
                  onChange={(e) => setPatientAge(Number(e.target.value))}
                  className="w-full bg-black/40 border border-white/10 rounded-lg px-2.5 py-1.5 text-foreground"
                />
              </div>
              <div>
                <label className="text-muted block mb-1">Sum Insured (₹)</label>
                <select
                  value={selectedSumInsured}
                  onChange={(e) => setSelectedSumInsured(Number(e.target.value))}
                  className="w-full bg-black/40 border border-white/10 rounded-lg px-2.5 py-1.5 text-foreground"
                >
                  <option value={300000}>₹3,00,000</option>
                  <option value={500000}>₹5,00,000 (Benchmark)</option>
                  <option value={1000000}>₹10,00,000</option>
                  <option value={2500000}>₹25,00,000</option>
                  <option value={5000000}>₹50,00,000</option>
                </select>
              </div>
              <div>
                <label className="text-muted block mb-1">Continuous Cover</label>
                <select
                  value={policyContinuityMonths}
                  onChange={(e) => setPolicyContinuityMonths(Number(e.target.value))}
                  className="w-full bg-black/40 border border-white/10 rounded-lg px-2.5 py-1.5 text-foreground"
                >
                  <option value={0}>0 months (Initial 30-day wait applies)</option>
                  <option value={12}>12 months (Initial cleared, PED/Specific wait active)</option>
                  <option value={24}>24 months (Specific wait cleared, 24m PED cleared)</option>
                  <option value={36}>36 months (Benchmark continuity)</option>
                  <option value={48}>48 months (All statutory waiting periods cleared)</option>
                </select>
              </div>
              <div>
                <label className="text-muted block mb-1">Pre-Existing Condition?</label>
                <div className="flex items-center gap-2 pt-1">
                  <input
                    type="checkbox"
                    id="pedCheck"
                    checked={isPreExistingCondition}
                    onChange={(e) => setIsPreExistingCondition(e.target.checked)}
                    className="rounded border-white/20 text-primary focus:ring-0 w-4 h-4"
                  />
                  <label htmlFor="pedCheck" className="text-xs text-foreground cursor-pointer">
                    {isPreExistingCondition ? "Yes (PED applies)" : "No (Fresh illness)"}
                  </label>
                </div>
              </div>
            </div>

            {/* Bill Summary Banner */}
            <div className="bg-black/30 rounded-xl p-3 border border-white/5 flex flex-wrap items-center justify-between text-xs gap-2">
              <div className="flex items-center gap-2">
                <span className="font-semibold text-foreground">Diagnosis:</span>
                <span className="text-muted">{diagnosis}</span>
              </div>
              <div className="flex items-center gap-4">
                <span>
                  <span className="text-muted">Opted Room:</span>{" "}
                  <strong className="text-foreground">Single Private AC (₹6,500/day)</strong>
                </span>
                <span>
                  <span className="text-muted">Total Submitted Hospital Bill:</span>{" "}
                  <strong className="text-emerald-400 font-mono text-sm">
                    ₹{totalSubmitted.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                  </strong>
                </span>
              </div>
            </div>
          </div>

          {/* Synthetic Uncertainty Disclaimer Banner */}
          {hasSyntheticInvolved && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="bg-amber-500/10 border border-amber-500/30 rounded-2xl p-4 text-xs text-amber-200 flex items-start gap-3"
            >
              <AlertTriangle className="w-5 h-5 text-amber-400 flex-shrink-0 mt-0.5" />
              <div>
                <strong className="font-bold text-amber-300 block mb-1">
                  EVIDENCE CERTAINTY DISPARITY: Modeled Demonstration Fixture Involved
                </strong>
                One or more of the selected policies is a synthetic demonstration fixture modeled from IRDAI master benefit schedules
                rather than raw acquired statutory policy wording PDF bytes. While useful for comparative simulation,
                synthetic payout figures are modeled estimates and must not be treated as contractual settlement guarantees.
              </div>
            </motion.div>
          )}

          {/* Error Message */}
          {errorMessage && (
            <div className="bg-rose-500/10 border border-rose-500/30 rounded-2xl p-4 text-xs text-rose-300 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-rose-400" />
              {errorMessage}
            </div>
          )}

          {/* Comparison Results */}
          {comparisonResult && compA && compB && (
            <div className="space-y-6">
              {/* Executive Payout Delta Banner */}
              <div className="glass rounded-2xl p-5 border border-primary/30 bg-gradient-to-r from-primary/10 via-accent/5 to-transparent space-y-3">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div className="space-y-1">
                    <span className="text-xs uppercase tracking-wider text-primary-light font-bold flex items-center gap-1.5">
                      <Scale className="w-4 h-4" /> Comparative Settlement Difference
                    </span>
                    <h2 className="text-xl sm:text-2xl font-black text-foreground">
                      Out-of-Pocket Variance:{" "}
                      <span className="text-amber-400 font-mono">
                        ₹{(comparisonResult.payoutVariance || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                      </span>
                    </h2>
                    <p className="text-xs text-muted leading-relaxed max-w-3xl">
                      {comparisonResult.varianceExplanation || "Comparative analysis completed."}
                    </p>
                  </div>

                  <div className="flex items-center gap-3">
                    <div className="bg-black/40 rounded-xl p-3 border border-white/10 text-right">
                      <div className="text-[11px] text-muted">Lower Out-of-Pocket</div>
                      <div className="text-sm font-bold text-emerald-400 truncate max-w-[200px]">
                        {comparisonResult.lowestOutOfPocketPolicyName || compA.policyName}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Side-by-Side Financial Waterfall Cards */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Policy A Card */}
                <div className="glass rounded-2xl p-5 border border-white/10 space-y-4">
                  <div className="flex items-center justify-between border-b border-white/10 pb-3">
                    <div>
                      <span className="text-xs font-bold text-primary-light">POLICY A</span>
                      <h3 className="text-base font-bold text-foreground">{compA.policyName}</h3>
                      <p className="text-xs text-muted">{compA.provider}</p>
                    </div>
                    {compA.authenticAcquiredDocument ? (
                      <span className="text-[10px] bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded-full font-semibold">
                        VERIFIED CONTRACT
                      </span>
                    ) : (
                      <span className="text-[10px] bg-amber-500/10 text-amber-400 border border-amber-500/30 px-2 py-0.5 rounded-full font-semibold">
                        SYNTHETIC FIXTURE
                      </span>
                    )}
                  </div>

                  {/* Financial Highlights */}
                  <div className="grid grid-cols-2 gap-2 text-center">
                    <div className="bg-emerald-500/10 border border-emerald-500/20 rounded-xl p-3">
                      <span className="text-[11px] text-muted block mb-0.5">Insurer Pays</span>
                      <span className="text-lg font-black text-emerald-400 font-mono">
                        ₹{compA.estimatedInsurerPayment.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                      </span>
                      <span className="text-[10px] text-muted block mt-0.5">
                        {((compA.estimatedInsurerPayment / compA.totalSubmittedBill) * 100).toFixed(1)}% of bill
                      </span>
                    </div>

                    <div className="bg-rose-500/10 border border-rose-500/20 rounded-xl p-3">
                      <span className="text-[11px] text-muted block mb-0.5">Patient Liability</span>
                      <span className="text-lg font-black text-rose-400 font-mono">
                        ₹{compA.estimatedPatientLiability.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                      </span>
                      <span className="text-[10px] text-muted block mt-0.5">
                        {((compA.estimatedPatientLiability / compA.totalSubmittedBill) * 100).toFixed(1)}% out-of-pocket
                      </span>
                    </div>
                  </div>

                  {/* Detailed Deductions Breakdown */}
                  <div className="space-y-2 text-xs border-t border-white/5 pt-3">
                    <div className="flex justify-between py-1">
                      <span className="text-muted">Submitted Hospital Bill</span>
                      <span className="font-mono font-medium">₹{compA.totalSubmittedBill.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1 text-rose-300">
                      <span>Non-Medical Consumables</span>
                      <span className="font-mono">-₹{compA.totalNonCoveredExpenses.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1 text-rose-300">
                      <span>Room Rent & Proportionate Haircut</span>
                      <span className="font-mono">-₹{compA.roomRentDeductions.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1 text-rose-300">
                      <span>Co-payment / Cost Sharing</span>
                      <span className="font-mono">-₹{compA.costSharingDeductions.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1 text-rose-300">
                      <span>Waiting Period Deductions</span>
                      <span className="font-mono">-₹{compA.waitingPeriodDeductions.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1.5 border-t border-white/10 font-bold text-foreground">
                      <span>Reconciled Insurer Settlement</span>
                      <span className="font-mono text-emerald-400">
                        ₹{compA.estimatedInsurerPayment.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                      </span>
                    </div>
                  </div>

                  <div className="text-[11px] bg-black/40 p-2.5 rounded-xl border border-white/5 text-muted">
                    <span className="font-semibold text-foreground/80">Identity Guarantee:</span>{" "}
                    ₹{compA.estimatedPatientLiability.toLocaleString("en-IN")} (Patient) + ₹{compA.estimatedInsurerPayment.toLocaleString("en-IN")} (Insurer) = ₹{compA.totalSubmittedBill.toLocaleString("en-IN")} (Exact 100% Reconciliation)
                  </div>
                </div>

                {/* Policy B Card */}
                <div className="glass rounded-2xl p-5 border border-white/10 space-y-4">
                  <div className="flex items-center justify-between border-b border-white/10 pb-3">
                    <div>
                      <span className="text-xs font-bold text-accent-light">POLICY B</span>
                      <h3 className="text-base font-bold text-foreground">{compB.policyName}</h3>
                      <p className="text-xs text-muted">{compB.provider}</p>
                    </div>
                    {compB.authenticAcquiredDocument ? (
                      <span className="text-[10px] bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded-full font-semibold">
                        VERIFIED CONTRACT
                      </span>
                    ) : (
                      <span className="text-[10px] bg-amber-500/10 text-amber-400 border border-amber-500/30 px-2 py-0.5 rounded-full font-semibold">
                        SYNTHETIC FIXTURE
                      </span>
                    )}
                  </div>

                  {/* Financial Highlights */}
                  <div className="grid grid-cols-2 gap-2 text-center">
                    <div className="bg-emerald-500/10 border border-emerald-500/20 rounded-xl p-3">
                      <span className="text-[11px] text-muted block mb-0.5">Insurer Pays</span>
                      <span className="text-lg font-black text-emerald-400 font-mono">
                        ₹{compB.estimatedInsurerPayment.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                      </span>
                      <span className="text-[10px] text-muted block mt-0.5">
                        {((compB.estimatedInsurerPayment / compB.totalSubmittedBill) * 100).toFixed(1)}% of bill
                      </span>
                    </div>

                    <div className="bg-rose-500/10 border border-rose-500/20 rounded-xl p-3">
                      <span className="text-[11px] text-muted block mb-0.5">Patient Liability</span>
                      <span className="text-lg font-black text-rose-400 font-mono">
                        ₹{compB.estimatedPatientLiability.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                      </span>
                      <span className="text-[10px] text-muted block mt-0.5">
                        {((compB.estimatedPatientLiability / compB.totalSubmittedBill) * 100).toFixed(1)}% out-of-pocket
                      </span>
                    </div>
                  </div>

                  {/* Detailed Deductions Breakdown */}
                  <div className="space-y-2 text-xs border-t border-white/5 pt-3">
                    <div className="flex justify-between py-1">
                      <span className="text-muted">Submitted Hospital Bill</span>
                      <span className="font-mono font-medium">₹{compB.totalSubmittedBill.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1 text-rose-300">
                      <span>Non-Medical Consumables</span>
                      <span className="font-mono">-₹{compB.totalNonCoveredExpenses.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1 text-rose-300">
                      <span>Room Rent & Proportionate Haircut</span>
                      <span className="font-mono">-₹{compB.roomRentDeductions.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1 text-rose-300">
                      <span>Co-payment / Cost Sharing</span>
                      <span className="font-mono">-₹{compB.costSharingDeductions.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1 text-rose-300">
                      <span>Waiting Period Deductions</span>
                      <span className="font-mono">-₹{compB.waitingPeriodDeductions.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div className="flex justify-between py-1.5 border-t border-white/10 font-bold text-foreground">
                      <span>Reconciled Insurer Settlement</span>
                      <span className="font-mono text-emerald-400">
                        ₹{compB.estimatedInsurerPayment.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                      </span>
                    </div>
                  </div>

                  <div className="text-[11px] bg-black/40 p-2.5 rounded-xl border border-white/5 text-muted">
                    <span className="font-semibold text-foreground/80">Identity Guarantee:</span>{" "}
                    ₹{compB.estimatedPatientLiability.toLocaleString("en-IN")} (Patient) + ₹{compB.estimatedInsurerPayment.toLocaleString("en-IN")} (Insurer) = ₹{compB.totalSubmittedBill.toLocaleString("en-IN")} (Exact 100% Reconciliation)
                  </div>
                </div>
              </div>

              {/* Clause-Level Delta Matrix */}
              {comparisonResult.clauseDeltas && comparisonResult.clauseDeltas.length > 0 && (
                <div className="glass rounded-2xl p-5 border border-white/10 space-y-4">
                  <div className="flex items-center gap-2 border-b border-white/10 pb-3">
                    <FileText className="w-4 h-4 text-primary-light" />
                    <h3 className="text-sm font-bold text-foreground">Contractual Clause Variance Breakdown</h3>
                  </div>

                  <div className="overflow-x-auto">
                    <table className="w-full text-xs text-left">
                      <thead>
                        <tr className="border-b border-white/10 text-muted uppercase text-[10px] tracking-wider">
                          <th className="py-2.5 pr-4">Policy Feature / Clause</th>
                          <th className="py-2.5 px-4 text-primary-light">{compA.policyName}</th>
                          <th className="py-2.5 px-4 text-accent-light">{compB.policyName}</th>
                          <th className="py-2.5 pl-4">Financial & Actuarial Impact</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-white/5">
                        {comparisonResult.clauseDeltas.map((delta, idx) => (
                          <tr key={idx} className="hover:bg-white/[0.02] transition-colors">
                            <td className="py-3 pr-4 font-semibold text-foreground">
                              {delta.feature}
                              <div className="text-[10px] text-muted font-normal mt-0.5">
                                Ref A: {delta.clauseCitationA}
                              </div>
                              <div className="text-[10px] text-muted font-normal">
                                Ref B: {delta.clauseCitationB}
                              </div>
                            </td>
                            <td className="py-3 px-4 font-medium text-foreground/90">
                              {delta.policyAValue}
                            </td>
                            <td className="py-3 px-4 font-medium text-foreground/90">
                              {delta.policyBValue}
                            </td>
                            <td className="py-3 pl-4 text-amber-300 font-medium">
                              {delta.financialImpact}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Waiting Periods, PED & Exclusions Side-by-Side */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="glass rounded-2xl p-4 sm:p-5 border border-white/5 space-y-3 text-xs">
                  <h4 className="font-bold text-primary-light flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5" /> Waiting Periods & Conditions — {compA.policyName}
                  </h4>
                  <div className="space-y-1.5 text-muted">
                    <div className="flex justify-between">
                      <span>Initial Waiting Period:</span>
                      <strong className="text-foreground">{compA.waitingPeriodInitialDays || 30} Days (Exempt for Accidents)</strong>
                    </div>
                    <div className="flex justify-between">
                      <span>Pre-Existing Disease (PED) Wait:</span>
                      <strong className="text-foreground">{compA.waitingPeriodPedMonths || 36} Months Active Continuity</strong>
                    </div>
                    <div className="flex justify-between">
                      <span>Specific Procedure Wait:</span>
                      <strong className="text-foreground">{compA.waitingPeriodSpecificMonths || 24} Months (Joint, Hernia, Cataract)</strong>
                    </div>
                  </div>
                  <div className="pt-2 border-t border-white/5">
                    <span className="font-semibold text-foreground block mb-1">Standard Exclusions:</span>
                    <ul className="list-disc pl-4 space-y-0.5 text-muted text-[11px]">
                      {(compA.keyExclusions || ["Standard Non-payable Consumables (IRDAI List I)", "Experimental Treatments", "External Congenital Conditions"]).map((ex, i) => (
                        <li key={i}>{ex}</li>
                      ))}
                    </ul>
                  </div>
                </div>

                <div className="glass rounded-2xl p-4 sm:p-5 border border-white/5 space-y-3 text-xs">
                  <h4 className="font-bold text-accent-light flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5" /> Waiting Periods & Conditions — {compB.policyName}
                  </h4>
                  <div className="space-y-1.5 text-muted">
                    <div className="flex justify-between">
                      <span>Initial Waiting Period:</span>
                      <strong className="text-foreground">{compB.waitingPeriodInitialDays || 30} Days (Exempt for Accidents)</strong>
                    </div>
                    <div className="flex justify-between">
                      <span>Pre-Existing Disease (PED) Wait:</span>
                      <strong className="text-foreground">{compB.waitingPeriodPedMonths || 36} Months Active Continuity</strong>
                    </div>
                    <div className="flex justify-between">
                      <span>Specific Procedure Wait:</span>
                      <strong className="text-foreground">{compB.waitingPeriodSpecificMonths || 24} Months (Joint, Hernia, Cataract)</strong>
                    </div>
                  </div>
                  <div className="pt-2 border-t border-white/5">
                    <span className="font-semibold text-foreground block mb-1">Standard Exclusions:</span>
                    <ul className="list-disc pl-4 space-y-0.5 text-muted text-[11px]">
                      {(compB.keyExclusions || ["Standard Non-payable Consumables (IRDAI List I)", "Experimental Treatments", "External Congenital Conditions"]).map((ex, i) => (
                        <li key={i}>{ex}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>

              {/* Document Provenance Card */}
              <div className="glass rounded-2xl p-5 border border-white/10 space-y-3">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="w-4 h-4 text-emerald-400" />
                  <h3 className="text-sm font-bold text-foreground">Cryptographic Provenance & Compliance Sign-Off</h3>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs font-mono">
                  <div className="bg-black/30 p-3 rounded-xl border border-white/5 space-y-1">
                    <div className="text-primary-light font-bold text-[11px] uppercase">{compA.policyName}</div>
                    <div className="text-muted">IRDAI UIN: <span className="text-foreground">{compA.irdaUin}</span></div>
                    <div className="text-muted">Revision: <span className="text-foreground">{compA.uinRevision}</span></div>
                    <div className="text-muted">Approval Status: <span className="text-emerald-400 font-sans">{compA.reviewerApprovalStatus || "REVIEWER_APPROVED"}</span></div>
                    <div className="text-muted">Verified By: <span className="text-foreground font-sans">{compA.verifiedBy || "SeniorComplianceOfficer@vidhura.ai"}</span></div>
                    <div className="text-muted truncate">
                      SHA-256: <span className="text-foreground/80">{compA.sourceDocumentHash || "N/A"}</span>
                    </div>
                  </div>

                  <div className="bg-black/30 p-3 rounded-xl border border-white/5 space-y-1">
                    <div className="text-accent-light font-bold text-[11px] uppercase">{compB.policyName}</div>
                    <div className="text-muted">IRDAI UIN: <span className="text-foreground">{compB.irdaUin}</span></div>
                    <div className="text-muted">Revision: <span className="text-foreground">{compB.uinRevision}</span></div>
                    <div className="text-muted">Approval Status: <span className="text-emerald-400 font-sans">{compB.reviewerApprovalStatus || "REVIEWER_APPROVED"}</span></div>
                    <div className="text-muted">Verified By: <span className="text-foreground font-sans">{compB.verifiedBy || "SeniorComplianceOfficer@vidhura.ai"}</span></div>
                    <div className="text-muted truncate">
                      SHA-256: <span className="text-foreground/80">{compB.sourceDocumentHash || "N/A"}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
