"use client";

import React, { useState } from "react";
import { motion } from "framer-motion";
import {
  GitCompare,
  Check,
  Building2,
  TrendingUp,
  AlertTriangle,
  ShieldCheck,
  HelpCircle,
  Clock,
  ChevronRight,
  ExternalLink,
  Sparkles,
  Info,
  DollarSign,
  FileText,
} from "lucide-react";
import {
  ClaimComparisonResponse,
  ClaimSimulationRequest,
  PolicyClaimComparisonItem,
  api,
} from "@/lib/api";
import ProvenanceBadge from "./ProvenanceBadge";

interface CrossPolicyComparisonViewProps {
  currentScenario: ClaimSimulationRequest;
  onSelectFullSimulation?: (simulation: any) => void;
}

const AVAILABLE_COMPARISON_POLICIES = [
  {
    id: "UIIHLIP22070V042122",
    code: "UIIHLIP22070V042122",
    name: "Family Medicare Policy",
    provider: "United India Insurance Company Limited",
    irdaUin: "UIIHLIP22070V042122",
    revision: "V042122",
    sourceHash: "9728ea60fce9e3e99b21c8b70abbf5d0b43236cbbc78901856311f77920efb3b",
    isSynthetic: false,
    keyFeatures: "1% SI room rent cap, 10% co-pay, ₹24k Cataract cap, Restoration for different illness",
  },
  {
    id: "NIAHLIP25040V102425",
    code: "NIAHLIP25040V102425",
    name: "New India Mediclaim Policy",
    provider: "The New India Assurance Company Limited",
    irdaUin: "NIAHLIP25040V102425",
    revision: "V102425",
    sourceHash: "b37d2519f4fe00e75f28727168ca6aafa9093f8c723fafa7db2f12e887fcea1f",
    isSynthetic: false,
    keyFeatures: "Single Private AC room, Nil co-pay, ₹40k Cataract cap, 100% recharge for same/different illness",
  },
  {
    id: "NIAHLIP21279V032021",
    code: "NIAHLIP21279V032021",
    name: "New India Premier Mediclaim",
    provider: "The New India Assurance Company Limited",
    irdaUin: "NIAHLIP21279V032021",
    revision: "V032021",
    sourceHash: "2d827d7c96d4d66687408387f69991d4c9e72d7c11ab6901ed6dc610d9870743",
    isSynthetic: false,
    keyFeatures: "Higher sum insured tiers, Single Private room, Nil co-pay, Restoration once per policy year",
  },
  {
    id: "HDFHLIP21147V012021",
    code: "HDFHLIP21147V012021",
    name: "Optima Secure",
    provider: "HDFC ERGO General Insurance Co. Ltd.",
    irdaUin: "HDFHLIP21147V012021",
    revision: "V012021",
    sourceHash: "mock_sha256_fixture",
    isSynthetic: true,
    keyFeatures: "Illustrative fixture: 1% SI cap, Restore benefit, 2X base coverage multiplier model",
  },
];

export default function CrossPolicyComparisonView({
  currentScenario,
  onSelectFullSimulation,
}: CrossPolicyComparisonViewProps) {
  const [selectedPolicyIds, setSelectedPolicyIds] = useState<string[]>([
    "UIIHLIP22070V042122",
    "NIAHLIP25040V102425",
    "HDFHLIP21147V012021",
  ]);
  const [isComparing, setIsComparing] = useState(false);
  const [comparisonResult, setComparisonResult] = useState<ClaimComparisonResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const togglePolicy = (policyId: string) => {
    if (selectedPolicyIds.includes(policyId)) {
      if (selectedPolicyIds.length <= 2) {
        return; // maintain at least 2 policies
      }
      setSelectedPolicyIds(selectedPolicyIds.filter((id) => id !== policyId));
    } else {
      setSelectedPolicyIds([...selectedPolicyIds, policyId]);
    }
  };

  const handleRunComparison = async () => {
    setIsComparing(true);
    setErrorMessage(null);

    try {
      const response = await api.compareClaims({
        policyIds: selectedPolicyIds,
        scenario: currentScenario,
      });

      if (response.success && response.data) {
        setComparisonResult(response.data);
      } else {
        setErrorMessage(response.message || "Failed to generate cross-policy comparison.");
      }
    } catch (err: any) {
      setErrorMessage(err.message || "Error running cross-policy claim comparison.");
    } finally {
      setIsComparing(false);
    }
  };

  const formatInr = (val?: number) => {
    if (val === undefined || val === null) return "₹0.00";
    return "₹" + val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-300">
      {/* 1. Header & Policy Selection Panel */}
      <div className="glass rounded-2xl p-6 border border-white/10 space-y-5">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-white/10 pb-4">
          <div>
            <h2 className="text-lg font-bold text-white flex items-center gap-2">
              <GitCompare className="w-5 h-5 text-accent" />
              Cross-Policy Claim Comparison
            </h2>
            <p className="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
              Run the exact same hospitalization scenario and itemized bill lines across multiple approved policies simultaneously.
              Trace differences in room rent limits, disease sublimits, cost-sharing, and restoration triggers side-by-side.
            </p>
          </div>

          <button
            type="button"
            onClick={handleRunComparison}
            disabled={isComparing || selectedPolicyIds.length < 2}
            className="px-6 py-3 rounded-xl bg-gradient-to-r from-primary to-accent hover:opacity-90 disabled:opacity-50 text-white text-xs font-bold flex items-center gap-2 shadow-xl shadow-primary/25 transition-all shrink-0"
          >
            {isComparing ? (
              <>
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                <span>Evaluating Comparison...</span>
              </>
            ) : (
              <>
                <GitCompare className="w-4 h-4" />
                <span>Compare {selectedPolicyIds.length} Policies</span>
              </>
            )}
          </button>
        </div>

        {/* Policy Selector Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
          {AVAILABLE_COMPARISON_POLICIES.map((pol) => {
            const isSelected = selectedPolicyIds.includes(pol.id);
            return (
              <div
                key={pol.id}
                role="button"
                tabIndex={0}
                aria-pressed={isSelected}
                onClick={() => togglePolicy(pol.id)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    togglePolicy(pol.id);
                  }
                }}
                className={`p-4 rounded-xl border text-left cursor-pointer transition-all flex flex-col justify-between ${
                  isSelected
                    ? "bg-primary/20 border-primary shadow-lg shadow-primary/10"
                    : "bg-white/5 border-white/10 hover:bg-white/10 hover:border-white/20 opacity-70"
                }`}
              >
                <div>
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <span className="text-xs font-mono text-slate-400 font-semibold">{pol.revision}</span>
                    <div
                      className={`w-4 h-4 rounded-md border flex items-center justify-center transition-colors ${
                        isSelected ? "bg-primary border-primary text-white" : "border-slate-500"
                      }`}
                    >
                      {isSelected && <Check className="w-3 h-3" />}
                    </div>
                  </div>
                  <h3 className="text-sm font-bold text-white mb-0.5">{pol.name}</h3>
                  <p className="text-[11px] text-slate-400 mb-2">{pol.provider}</p>
                  <p className="text-[10px] text-slate-300 leading-normal mb-3">{pol.keyFeatures}</p>
                </div>
                <ProvenanceBadge
                  policyCode={pol.code}
                  sourceHash={pol.sourceHash}
                  isSynthetic={pol.isSynthetic}
                />
              </div>
            );
          })}
        </div>
      </div>

      {/* Error Message */}
      {errorMessage && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* 2. Side-by-Side Comparison Results */}
      {comparisonResult && (
        <div className="space-y-6">
          {/* Executive Payout Winner Banner */}
          <div className="p-5 rounded-2xl glass border border-white/10 grid grid-cols-1 md:grid-cols-3 gap-4 items-center">
            <div>
              <span className="text-[10px] text-slate-400 uppercase tracking-wider block">Diagnosis Evaluated</span>
              <span className="text-base font-bold text-white">{comparisonResult.scenarioDiagnosis}</span>
              <span className="text-xs text-slate-400 block mt-0.5">
                Total Submitted: <span className="font-mono text-slate-200">{formatInr(comparisonResult.totalSubmittedBill)}</span>
              </span>
            </div>

            <div className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
              <span className="text-[10px] text-emerald-400 uppercase tracking-wider font-semibold block flex items-center gap-1">
                <Sparkles className="w-3.5 h-3.5" /> Highest Insurer Settlement
              </span>
              <span className="text-sm font-bold text-white mt-0.5 block">{comparisonResult.highestPayoutPolicyName}</span>
            </div>

            <div className="p-3.5 rounded-xl bg-primary/10 border border-primary/20">
              <span className="text-[10px] text-primary-light uppercase tracking-wider font-semibold block flex items-center gap-1">
                <ShieldCheck className="w-3.5 h-3.5" /> Lowest Out-of-Pocket Expense
              </span>
              <span className="text-sm font-bold text-white mt-0.5 block">{comparisonResult.lowestOutOfPocketPolicyName}</span>
            </div>
          </div>

          {/* Comparative Cards Matrix */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {comparisonResult.comparisons.map((item) => {
              const insurerShare = item.totalSubmittedBill > 0
                ? Math.round((item.estimatedInsurerPayment / item.totalSubmittedBill) * 100)
                : 0;

              return (
                <div
                  key={item.policyId}
                  className="glass rounded-2xl p-6 border border-white/10 flex flex-col justify-between space-y-6 hover:border-white/20 transition-all"
                >
                  <div className="space-y-4">
                    {/* Header */}
                    <div className="flex items-start justify-between gap-2 border-b border-white/10 pb-3">
                      <div>
                        <h3 className="text-base font-bold text-white">{item.policyName}</h3>
                        <p className="text-xs text-slate-400 mt-0.5">{item.provider}</p>
                      </div>
                      <ProvenanceBadge
                        policyCode={item.policyCode}
                        sourceHash={item.sourceDocumentHash}
                        isSynthetic={item.syntheticExample}
                        status={item.calculationStatus}
                      />
                    </div>

                    {/* Financial Summary */}
                    <div className="grid grid-cols-2 gap-3">
                      <div className="p-3 rounded-xl bg-slate-950 border border-white/5">
                        <span className="text-[10px] text-emerald-400 uppercase tracking-wider block font-semibold">
                          Insurer Payment
                        </span>
                        <span className="text-lg font-bold font-mono text-emerald-400">
                          {formatInr(item.estimatedInsurerPayment)}
                        </span>
                        <span className="text-[10px] text-slate-400 block mt-0.5">({insurerShare}% of bill)</span>
                      </div>

                      <div className="p-3 rounded-xl bg-slate-950 border border-white/5">
                        <span className="text-[10px] text-rose-400 uppercase tracking-wider block font-semibold">
                          Patient Liability
                        </span>
                        <span className="text-lg font-bold font-mono text-rose-400">
                          {formatInr(item.estimatedPatientLiability)}
                        </span>
                        <span className="text-[10px] text-slate-400 block mt-0.5">({100 - insurerShare}% out-of-pocket)</span>
                      </div>
                    </div>

                    {/* Payout Progress Ratio */}
                    <div className="space-y-1">
                      <div className="h-2 rounded-full bg-slate-800 overflow-hidden flex">
                        <div
                          className="h-full bg-emerald-400 transition-all"
                          style={{ width: `${insurerShare}%` }}
                        />
                        <div
                          className="h-full bg-rose-400 transition-all"
                          style={{ width: `${100 - insurerShare}%` }}
                        />
                      </div>
                    </div>

                    {/* Clause Breakdown Metrics */}
                    <div className="space-y-2 text-xs pt-2 border-t border-white/5">
                      <div className="flex justify-between items-center py-1 border-b border-white/5">
                        <span className="text-slate-400">Room Rent Deductions</span>
                        <span className={`font-mono font-medium ${item.roomRentDeductions > 0 ? "text-rose-400" : "text-slate-200"}`}>
                          {item.roomRentDeductions > 0 ? `-${formatInr(item.roomRentDeductions)}` : "₹0.00 (Nil)"}
                        </span>
                      </div>

                      <div className="flex justify-between items-center py-1 border-b border-white/5">
                        <span className="text-slate-400">Disease Sublimits</span>
                        <span className={`font-mono font-medium ${item.sublimitDeductions > 0 ? "text-rose-400" : "text-slate-200"}`}>
                          {item.sublimitDeductions > 0 ? `-${formatInr(item.sublimitDeductions)}` : "₹0.00 (Nil)"}
                        </span>
                      </div>

                      <div className="flex justify-between items-center py-1 border-b border-white/5">
                        <span className="text-slate-400">Co-Pay / Deductible</span>
                        <span className={`font-mono font-medium ${item.costSharingDeductions > 0 ? "text-rose-400" : "text-slate-200"}`}>
                          {item.costSharingDeductions > 0 ? `-${formatInr(item.costSharingDeductions)}` : "₹0.00 (Nil)"}
                        </span>
                      </div>

                      <div className="flex justify-between items-center py-1 border-b border-white/5">
                        <span className="text-slate-400">Restoration Status</span>
                        <span className={`font-medium ${item.restorationTriggered ? "text-accent" : "text-slate-400"}`}>
                          {item.restorationTriggered ? `Triggered (+${formatInr(item.restorationAmountGranted)})` : "Not Triggered"}
                        </span>
                      </div>

                      <div className="flex justify-between items-center py-1">
                        <span className="text-slate-400">Contractual Clauses Applied</span>
                        <span className="font-mono text-slate-200">{item.appliedClauseCount} Verified Clauses</span>
                      </div>
                    </div>
                  </div>

                  {/* Inspect Full Waterfall Button */}
                  {onSelectFullSimulation && (
                    <button
                      type="button"
                      onClick={() => onSelectFullSimulation(item.fullSimulation)}
                      className="w-full py-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-semibold text-slate-200 flex items-center justify-center gap-1.5 transition-colors group"
                    >
                      <span>View Itemized Deduction Waterfall</span>
                      <ChevronRight className="w-3.5 h-3.5 text-slate-400 group-hover:text-white group-hover:translate-x-0.5 transition-all" />
                    </button>
                  )}
                </div>
              );
            })}
          </div>

          {/* Legal / Actuarial Notice */}
          <div className="p-4 rounded-xl bg-slate-950/60 border border-white/5 text-[11px] text-slate-400 leading-relaxed flex items-start gap-2.5">
            <Info className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
            <div>
              <span className="text-slate-300 font-semibold">Important Disclaimer: </span>
              {comparisonResult.disclaimer}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
