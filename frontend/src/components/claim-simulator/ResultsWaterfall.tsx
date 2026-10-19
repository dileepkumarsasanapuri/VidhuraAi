"use client";

import React, { useState } from "react";
import { motion } from "framer-motion";
import {
  ShieldCheck,
  AlertTriangle,
  FileText,
  Calculator,
  RotateCcw,
  Copy,
  Check,
  ExternalLink,
  ChevronRight,
  Info,
  Clock,
  HelpCircle,
  TrendingDown,
  RefreshCw,
} from "lucide-react";
import { ClaimSimulationResponse, DeductionItem } from "@/lib/api";
import ProvenanceBadge from "./ProvenanceBadge";
import ClauseEvidenceDrawer from "./ClauseEvidenceDrawer";

interface ResultsWaterfallProps {
  simulation: ClaimSimulationResponse;
  onReset: () => void;
}

export default function ResultsWaterfall({ simulation, onReset }: ResultsWaterfallProps) {
  const [selectedDeduction, setSelectedDeduction] = useState<DeductionItem | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [copiedId, setCopiedId] = useState(false);

  const handleInspectClause = (deduction: DeductionItem) => {
    setSelectedDeduction(deduction);
    setIsDrawerOpen(true);
  };

  const handleCopyId = () => {
    if (simulation.simulationId) {
      navigator.clipboard.writeText(simulation.simulationId);
      setCopiedId(true);
      setTimeout(() => setCopiedId(false), 2000);
    }
  };

  const isManualReview = simulation.calculationStatus === "MANUAL_REVIEW_REQUIRED";
  const formatInr = (val?: number) => {
    if (val === undefined || val === null) return "₹0.00";
    return "₹" + val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-300">
      {/* Simulation Header & Status Bar */}
      <div className="glass rounded-2xl p-6 border border-white/10 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <ProvenanceBadge
              status={simulation.calculationStatus}
              policyCode={simulation.policyCode}
              sourceHash={simulation.sourceDocumentHash}
            />
            <span className="text-xs text-slate-400 font-mono">
              Engine: <span className="text-slate-200">{simulation.calculationEngineVersion}</span>
            </span>
            <span className="text-xs text-slate-400 flex items-center gap-1 font-mono">
              <Clock className="w-3.5 h-3.5 text-slate-500" />
              {new Date(simulation.evaluatedAt).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}
            </span>
          </div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            {simulation.policyName}
            <span className="text-xs font-normal text-slate-400 font-mono">({simulation.uinRevision})</span>
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            IRDAI Filing: <span className="font-mono text-slate-300">{simulation.irdaUin}</span> • Provider: {simulation.provider}
          </p>
        </div>

        <div className="flex items-center gap-3">
          {simulation.simulationId && (
            <button
              onClick={handleCopyId}
              className="px-3 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-mono text-slate-300 flex items-center gap-1.5 transition-colors"
              title="Copy Simulation ID for historical verification"
            >
              {copiedId ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5 text-slate-400" />}
              <span>ID: {simulation.simulationId.slice(0, 8)}...</span>
            </button>
          )}

          <button
            onClick={onReset}
            className="px-4 py-2 rounded-xl bg-primary hover:bg-primary-dark text-white text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-lg shadow-primary/20"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Modify Bill</span>
          </button>
        </div>
      </div>

      {/* Manual Review Required Alert (Fail-Closed) */}
      {isManualReview && (
        <div className="p-6 rounded-2xl bg-amber-500/10 border border-amber-500/30 text-amber-200 space-y-3">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-6 h-6 text-amber-400 shrink-0 mt-0.5" />
            <div>
              <h3 className="text-base font-bold text-white">Manual Contractual Review Required</h3>
              <p className="text-sm text-amber-300/90 mt-1">
                {simulation.statusReason ||
                  "The claim engine suspended mathematical settlement estimation because material terms in the policy wording require human reviewer clarification."}
              </p>
            </div>
          </div>
          {simulation.unresolvedRules && simulation.unresolvedRules.length > 0 && (
            <div className="mt-3 p-3 rounded-xl bg-black/30 border border-amber-500/20">
              <p className="text-xs font-semibold text-amber-400 mb-1.5 uppercase tracking-wide">
                Unresolved Contractual Clauses:
              </p>
              <ul className="list-disc list-inside text-xs text-slate-300 space-y-1 font-mono">
                {simulation.unresolvedRules.map((r, idx) => (
                  <li key={idx}>{r}</li>
                ))}
              </ul>
            </div>
          )}
          <p className="text-xs text-amber-400/80 italic">
            Vidhura AI strictly avoids fabricating numbers when contractual clauses are ambiguous or pending review.
          </p>
        </div>
      )}

      {/* Executive Financial Summary Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Submitted Bill */}
        <div className="glass rounded-2xl p-5 border border-white/10 flex flex-col justify-between">
          <p className="text-xs text-slate-400 font-medium">Submitted Hospital Bill</p>
          <div className="mt-3">
            <p className="text-2xl font-bold font-mono text-white">
              {formatInr(simulation.totalSubmittedBill)}
            </p>
            <p className="text-[11px] text-slate-500 mt-1">Itemized across claimed lines</p>
          </div>
        </div>

        {/* Total Deductions */}
        <div className="glass rounded-2xl p-5 border border-white/10 flex flex-col justify-between">
          <p className="text-xs text-slate-400 font-medium flex items-center justify-between">
            <span>Total Deductions & Exclusions</span>
            <TrendingDown className="w-4 h-4 text-rose-400" />
          </p>
          <div className="mt-3">
            <p className="text-2xl font-bold font-mono text-rose-400">
              {formatInr(simulation.totalDeductions)}
            </p>
            <p className="text-[11px] text-slate-500 mt-1">
              {simulation.deductionBreakdown?.length || 0} policy clause deductions
            </p>
          </div>
        </div>

        {/* Estimated Insurer Payment */}
        <div className="glass rounded-2xl p-5 border border-emerald-500/30 bg-emerald-500/5 flex flex-col justify-between relative overflow-hidden">
          <div className="absolute top-0 right-0 w-24 h-24 bg-emerald-500/10 rounded-full blur-2xl pointer-events-none" />
          <p className="text-xs text-emerald-400 font-semibold tracking-wide uppercase">
            Estimated Insurer Payment
          </p>
          <div className="mt-3">
            <p className="text-3xl font-extrabold font-mono text-emerald-400">
              {isManualReview ? "Pending Review" : formatInr(simulation.estimatedInsurerPayment)}
            </p>
            <p className="text-[11px] text-emerald-400/70 mt-1">Payable under approved policy terms</p>
          </div>
        </div>

        {/* Estimated Patient Liability */}
        <div className="glass rounded-2xl p-5 border border-amber-500/30 bg-amber-500/5 flex flex-col justify-between relative overflow-hidden">
          <div className="absolute top-0 right-0 w-24 h-24 bg-amber-500/10 rounded-full blur-2xl pointer-events-none" />
          <p className="text-xs text-amber-400 font-semibold tracking-wide uppercase">
            Estimated Patient Liability
          </p>
          <div className="mt-3">
            <p className="text-3xl font-extrabold font-mono text-amber-400">
              {formatInr(simulation.estimatedPatientLiability)}
            </p>
            <p className="text-[11px] text-amber-400/70 mt-1">Out-of-pocket patient payment</p>
          </div>
        </div>
      </div>

      {/* Mathematical Reconciliation Identity Guarantee */}
      <div className="glass rounded-2xl p-5 border border-accent/20 bg-accent/5 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="p-2.5 rounded-xl bg-accent/10 border border-accent/30 text-accent shrink-0">
            <Calculator className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h4 className="text-sm font-bold text-white">Mathematical Reconciliation Identity</h4>
              {simulation.reconciled && (
                <span className="inline-flex items-center gap-1 text-[11px] font-mono font-medium px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                  <Check className="w-3 h-3" /> Reconciled Exactly
                </span>
              )}
            </div>
            <p className="text-xs font-mono text-accent-light break-all">
              {simulation.reconciliationIdentity}
            </p>
          </div>
        </div>

        <div className="text-right text-xs text-slate-400 shrink-0">
          <span className="text-slate-300 font-medium">Reconciliation Formula:</span>
          <br />
          <span className="font-mono text-[11px]">Patient Liability = Submitted Bill - Insurer Payment</span>
        </div>
      </div>

      {/* Restoration Benefit Information (if evaluated) */}
      {simulation.restorationTriggered && (
        <div className="glass rounded-2xl p-5 border border-cyan-500/30 bg-cyan-500/5 flex items-start gap-3">
          <div className="p-2.5 rounded-xl bg-cyan-500/10 border border-cyan-500/30 text-cyan-400 shrink-0">
            <RefreshCw className="w-5 h-5" />
          </div>
          <div className="space-y-1">
            <h4 className="text-sm font-bold text-white flex items-center gap-2">
              Sum Insured Restoration Triggered
              <span className="px-2 py-0.5 rounded bg-cyan-500/20 text-cyan-300 font-mono text-xs">
                +{formatInr(simulation.restorationAmountGranted)}
              </span>
            </h4>
            <p className="text-xs text-slate-300 leading-relaxed">
              {simulation.restorationExplanation}
            </p>
          </div>
        </div>
      )}

      {/* Transparent Deduction Waterfall */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <TrendingDown className="w-5 h-5 text-rose-400" />
              Transparent Deduction Waterfall
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Every reduction from your submitted bill is anchored in a verified policy clause. Click any deduction to inspect evidence.
            </p>
          </div>
          <span className="text-xs text-slate-400 font-mono">
            {simulation.deductionBreakdown?.length || 0} Deductions
          </span>
        </div>

        {simulation.deductionBreakdown && simulation.deductionBreakdown.length > 0 ? (
          <div className="space-y-3">
            {simulation.deductionBreakdown.map((item, index) => (
              <motion.div
                key={index}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
                className="glass rounded-xl p-4 border border-white/10 hover:border-primary/40 transition-all flex flex-col md:flex-row md:items-center justify-between gap-4 group cursor-pointer"
                onClick={() => handleInspectClause(item)}
              >
                <div className="flex items-start gap-3.5">
                  <div className="w-7 h-7 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-center justify-center font-mono font-bold text-xs shrink-0 mt-0.5">
                    {index + 1}
                  </div>
                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-xs font-semibold text-white">
                        {item.deductionType.replace(/_/g, " ")}
                      </span>
                      <span className="text-[11px] px-2 py-0.5 rounded bg-white/5 border border-white/10 text-slate-300 font-mono">
                        {item.expenseCategory}
                      </span>
                      {item.sectionReference && (
                        <span className="text-[11px] px-2 py-0.5 rounded bg-primary/10 text-primary-light border border-primary/20 font-mono">
                          {item.sectionReference}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-300 leading-relaxed">
                      {item.explanation}
                    </p>
                    {item.calculationFormula && (
                      <p className="text-[11px] font-mono text-accent/90">
                        Formula: {item.calculationFormula}
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex items-center justify-between md:justify-end gap-4 shrink-0 pl-10 md:pl-0">
                  <div className="text-right">
                    <p className="text-base font-bold font-mono text-rose-400">
                      -{formatInr(item.amountDeducted)}
                    </p>
                    <p className="text-[10px] text-slate-500">Patient Liability</p>
                  </div>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleInspectClause(item);
                    }}
                    className="p-2 rounded-lg bg-white/5 hover:bg-white/10 text-slate-400 hover:text-white transition-colors"
                    aria-label={`Inspect clause for ${item.deductionType}`}
                  >
                    <ChevronRight className="w-4 h-4 text-slate-400 group-hover:text-primary-light group-hover:translate-x-0.5 transition-all" />
                  </button>
                </div>
              </motion.div>
            ))}
          </div>
        ) : (
          <div className="p-8 rounded-2xl glass border border-white/10 text-center space-y-2">
            <ShieldCheck className="w-8 h-8 text-emerald-400 mx-auto" />
            <p className="text-sm font-semibold text-white">Zero Contractual Deductions Applied</p>
            <p className="text-xs text-slate-400 max-w-md mx-auto">
              All submitted expenses were determined to be fully eligible under the approved policy terms with no copayments, sublimits, or room rent penalties.
            </p>
          </div>
        )}
      </div>

      {/* Legal & Actuarial Disclaimer */}
      <div className="p-4 rounded-xl bg-slate-950/60 border border-white/5 text-[11px] text-slate-400 leading-relaxed">
        <span className="text-slate-300 font-semibold">Contractual Disclaimer: </span>
        {simulation.legalDisclaimer}
      </div>

      {/* Clause Evidence Drawer */}
      <ClauseEvidenceDrawer
        isOpen={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        deduction={selectedDeduction}
        simulation={simulation}
      />
    </div>
  );
}
