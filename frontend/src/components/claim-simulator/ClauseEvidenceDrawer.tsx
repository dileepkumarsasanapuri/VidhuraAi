"use client";

import React, { useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { X, FileText, ShieldCheck, Hash, BookOpen, AlertCircle, ExternalLink, Calculator } from "lucide-react";
import { DeductionItem, ClaimSimulationResponse } from "@/lib/api";
import ProvenanceBadge from "./ProvenanceBadge";

interface ClauseEvidenceDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  deduction: DeductionItem | null;
  simulation: ClaimSimulationResponse | null;
}

export default function ClauseEvidenceDrawer({
  isOpen,
  onClose,
  deduction,
  simulation,
}: ClauseEvidenceDrawerProps) {
  // Close drawer on ESC key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    if (isOpen) {
      window.addEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "hidden";
    }
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "";
    };
  }, [isOpen, onClose]);

  if (!isOpen || !deduction) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex justify-end">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          className="fixed inset-0 bg-black/70 backdrop-blur-sm"
          onClick={onClose}
          aria-hidden="true"
        />

        {/* Drawer Panel */}
        <motion.div
          initial={{ x: "100%" }}
          animate={{ x: 0 }}
          exit={{ x: "100%" }}
          transition={{ type: "spring", damping: 28, stiffness: 280 }}
          className="relative w-full max-w-xl h-full bg-slate-900 border-l border-white/10 shadow-2xl flex flex-col z-10 overflow-hidden"
          role="dialog"
          aria-modal="true"
          aria-labelledby="drawer-title"
        >
          {/* Header */}
          <div className="p-6 border-b border-white/10 flex items-start justify-between bg-slate-950/60 backdrop-blur-md">
            <div>
              <div className="flex items-center gap-2 mb-1.5">
                <span className="px-2 py-0.5 rounded text-[11px] font-mono font-semibold bg-primary/20 text-primary-light border border-primary/30 uppercase tracking-wide">
                  {deduction.deductionType.replace(/_/g, " ")}
                </span>
                <span className="text-xs text-slate-400">
                  Category: <span className="text-slate-200 font-medium">{deduction.expenseCategory}</span>
                </span>
              </div>
              <h2 id="drawer-title" className="text-lg font-bold text-white flex items-center gap-2">
                <BookOpen className="w-5 h-5 text-accent" />
                Contractual Clause Evidence
              </h2>
            </div>
            <button
              onClick={onClose}
              className="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
              aria-label="Close clause drawer"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Drawer Body */}
          <div className="flex-1 overflow-y-auto p-6 space-y-6">
            {/* Amount Deducted Summary Box */}
            <div className="p-4 rounded-xl bg-slate-950 border border-white/10 flex items-center justify-between">
              <div>
                <p className="text-xs text-slate-400 mb-0.5">Patient Liability Adjustment</p>
                <p className="text-2xl font-bold text-rose-400 font-mono">
                  -₹{deduction.amountDeducted.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </p>
              </div>
              <ProvenanceBadge
                policyCode={simulation?.policyCode}
                sourceHash={simulation?.sourceDocumentHash}
              />
            </div>

            {/* Verbatim Contractual Clause */}
            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
                <span className="flex items-center gap-1.5 text-slate-300">
                  <FileText className="w-4 h-4 text-primary-light" />
                  Verbatim Official Wording
                </span>
                <span className="px-2 py-0.5 rounded bg-white/5 border border-white/10 font-mono text-[11px] text-accent">
                  {deduction.sectionReference || "Statutory Clause"}
                </span>
              </div>
              <blockquote className="p-4 rounded-xl bg-primary/5 border-l-4 border-primary text-slate-200 text-sm leading-relaxed italic shadow-inner">
                &ldquo;{deduction.verbatimClauseText || "Verbatim contractual clause extracted from official policy wording."}&rdquo;
              </blockquote>
            </div>

            {/* Actuarial & Plain-English Explanation */}
            <div className="space-y-2">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Calculation Rationale & Impact
              </h3>
              <div className="p-4 rounded-xl bg-white/5 border border-white/10 text-sm text-slate-300 leading-relaxed">
                {deduction.explanation}
              </div>
            </div>

            {/* Formula / Proportionate Ratio Breakdown */}
            {deduction.calculationFormula && (
              <div className="space-y-2">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                  <Calculator className="w-3.5 h-3.5 text-accent" />
                  Applied Actuarial Identity / Formula
                </h3>
                <div className="p-3 rounded-lg bg-slate-950/80 border border-white/10 font-mono text-xs text-accent-light break-all">
                  {deduction.calculationFormula}
                </div>
              </div>
            )}

            {/* Policy & Provenance Metadata */}
            {simulation && (
              <div className="space-y-3 pt-2">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Authoritative Statutory Filing Provenance
                </h3>
                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div className="p-3 rounded-lg bg-white/5 border border-white/5">
                    <p className="text-slate-500 mb-0.5">Policy Product</p>
                    <p className="text-slate-200 font-medium">{simulation.policyName}</p>
                    <p className="text-[11px] text-slate-400">{simulation.provider}</p>
                  </div>
                  <div className="p-3 rounded-lg bg-white/5 border border-white/5">
                    <p className="text-slate-500 mb-0.5">IRDAI UIN & Revision</p>
                    <p className="text-slate-200 font-mono font-medium">{simulation.irdaUin}</p>
                    <p className="text-[11px] text-accent font-mono">Revision: {simulation.uinRevision}</p>
                  </div>
                </div>

                {simulation.sourceDocumentHash && (
                  <div className="p-3 rounded-lg bg-white/5 border border-white/5 flex items-start gap-2.5">
                    <Hash className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                    <div className="overflow-hidden">
                      <p className="text-slate-400 text-xs font-medium">Source Document SHA-256 Checksum</p>
                      <p className="text-[11px] font-mono text-slate-300 truncate" title={simulation.sourceDocumentHash}>
                        {simulation.sourceDocumentHash}
                      </p>
                    </div>
                  </div>
                )}

                <div className="flex items-center justify-between px-3 py-2 rounded-lg bg-emerald-500/5 border border-emerald-500/20 text-xs">
                  <span className="flex items-center gap-1.5 text-emerald-300 font-medium">
                    <ShieldCheck className="w-4 h-4 text-emerald-400" />
                    Clause Verification Status
                  </span>
                  <span className="px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 font-mono text-[10px]">
                    {deduction.clauseConfidence || "VERIFIED_CONTRACT_CLAUSE"}
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="p-4 border-t border-white/10 bg-slate-950/60 backdrop-blur-md flex justify-end">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-xl bg-white/10 hover:bg-white/20 text-white text-sm font-medium transition-colors"
            >
              Close Drawer
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
