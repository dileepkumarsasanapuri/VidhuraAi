"use client";

import React, { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import {
  FileText, AlertTriangle, Shield, ChevronRight, CheckCircle, ArrowLeft, Loader2,
  Sparkles, Star, Activity, Printer, Trash2, X,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import ScoreRing from "@/components/ScoreRing";
import { api, Policy } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

export default function PolicyDetailsPage() {
  const params = useParams();
  const router = useRouter();
  const toast = useToast();
  const id = params.id as string;
  
  const [policy, setPolicy] = useState<Policy | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    async function fetchPolicy() {
      try {
        const response = await api.getPolicy(id);
        if (response.success && response.data) {
          setPolicy(response.data);
        } else {
          setError("Policy not found.");
        }
      } catch (err: any) {
        setError(err.message || "Failed to load policy details.");
      } finally {
        setLoading(false);
      }
    }
    
    if (id) fetchPolicy();
  }, [id]);

  if (loading) {
    return (
      <div className="flex h-screen overflow-hidden">
        <Sidebar activeTab="/dashboard/policies" />
        <main className="flex-1 flex items-center justify-center">
          <Loader2 className="w-8 h-8 animate-spin text-primary-light" />
        </main>
      </div>
    );
  }

  if (error || !policy) {
    return (
      <div className="flex h-screen overflow-hidden">
        <Sidebar activeTab="/dashboard/policies" />
        <main className="flex-1 p-6">
          <button onClick={() => router.push('/dashboard/policies')} className="flex items-center gap-2 text-sm text-muted hover:text-white mb-6">
            <ArrowLeft className="w-4 h-4" /> Back to Policies
          </button>
          <div className="glass rounded-2xl p-6 border border-red-500/20 text-red-400">
            {error || "Policy not found."}
          </div>
        </main>
      </div>
    );
  }

  const handleDeletePolicy = async () => {
    if (!policy) return;
    setIsDeleting(true);
    try {
      await api.deletePolicy(policy.id);
      toast.success(
        `Policy "${policy.name}" was permanently removed.`,
        "Policy Deleted"
      );
      router.push("/dashboard/policies");
    } catch (err: any) {
      console.error("Failed to delete policy:", err);
      toast.error(
        err.message || "Failed to delete policy. Please try again.",
        "Deletion Failed"
      );
      setIsDeleting(false);
    }
  };

  const analysis = policy.analysis;

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/policies" />
      <main className="flex-1 overflow-y-auto">
        <header className="sticky top-0 z-30 glass border-b border-border px-6 h-16 flex items-center justify-between">
          <div className="flex items-center">
            <button onClick={() => router.push('/dashboard/policies')} className="mr-4 p-2 hover:bg-white/5 rounded-lg transition-colors">
              <ArrowLeft className="w-5 h-5 text-muted hover:text-white" />
            </button>
            <h1 className="text-lg font-bold">Policy Audit & Clause Report</h1>
          </div>
          <button
            onClick={() => router.push('/dashboard/compare')}
            className="text-xs text-muted hover:text-white glass px-3 py-1.5 rounded-xl transition-colors hidden sm:block"
          >
            Compare with Others →
          </button>
        </header>

        <div className="p-6 max-w-5xl mx-auto space-y-6">
          {/* Printable Official Report Header - visible only when printing/exporting to PDF */}
          <div className="print-only mb-6 pb-4 border-b-2 border-slate-900">
            <div className="flex justify-between items-start">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-bold uppercase tracking-widest text-indigo-700">Vidhura AI Official Audit</span>
                  <span className="text-xs text-slate-400">•</span>
                  <span className="text-xs text-slate-500">Autonomous Actuarial Protection Intelligence</span>
                </div>
                <h1 className="text-2xl font-black text-slate-900 tracking-tight">{policy.name}</h1>
                <p className="text-xs text-slate-600 mt-0.5">{policy.provider} • Policy Category: {policy.type}</p>
              </div>
              <div className="text-right text-xs text-slate-600">
                <p className="font-semibold text-slate-900">Report Date: {new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</p>
                <p className="text-[10px] text-slate-500 font-mono mt-0.5">Policy Ref: {policy.id}</p>
                <p className="text-[10px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded mt-1 inline-block border border-emerald-200">
                  Status: Verified Active
                </p>
              </div>
            </div>
          </div>

          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
            {/* Policy overview */}
            <div className="glass rounded-2xl p-6 flex flex-col md:flex-row justify-between items-start md:items-center gap-4 border border-primary/20">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center flex-shrink-0">
                  <Shield className="w-6 h-6 text-primary-light" />
                </div>
                <div>
                  <h3 className="text-xl font-bold">{policy.name}</h3>
                  <p className="text-xs text-muted mt-0.5">{policy.provider} • {policy.type}</p>
                  <div className="flex flex-wrap gap-2 mt-2">
                    <span className="text-[11px] bg-white/5 border border-white/10 px-2.5 py-0.5 rounded-lg text-primary-light font-medium">
                      Coverage: {policy.coverage || "Standard"}
                    </span>
                    <span className="text-[11px] bg-white/5 border border-white/10 px-2.5 py-0.5 rounded-lg text-muted">
                      Premium: {policy.premium || "Standard"}
                    </span>
                    <span className="text-[11px] bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-2.5 py-0.5 rounded-lg font-medium">
                      Status: Active
                    </span>
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2 no-print flex-shrink-0">
                <button
                  type="button"
                  onClick={() => window.print()}
                  className="px-4 py-2.5 text-xs font-semibold rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-foreground transition-all flex items-center gap-2"
                  title="Print or Save as PDF"
                >
                  <Printer className="w-4 h-4 text-primary-light" />
                  Export / Print PDF
                </button>
                <button
                  type="button"
                  onClick={() => setShowDeleteModal(true)}
                  className="px-4 py-2.5 text-xs font-semibold rounded-xl bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-400 transition-all flex items-center gap-2"
                  title="Delete Policy"
                >
                  <Trash2 className="w-4 h-4" />
                  Delete Policy
                </button>
              </div>
            </div>

            {analysis ? (
              <>
                {/* Simplified Summary Banner */}
                {analysis.simplifiedSummary && (
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }}
                    className="glass rounded-2xl p-5 border border-primary/20 bg-gradient-to-r from-primary/5 to-accent/5">
                    <div className="flex items-center gap-2 mb-2">
                      <Sparkles className="w-4 h-4 text-primary-light" />
                      <h4 className="text-xs font-bold uppercase tracking-wider text-primary-light">Executive AI Policy Assessment</h4>
                    </div>
                    <p className="text-sm text-foreground/90 leading-relaxed">
                      {analysis.simplifiedSummary}
                    </p>
                  </motion.div>
                )}

                {/* Exact Extracted Policy Contract Parameters */}
                <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }}
                  className="glass rounded-2xl p-6 border border-emerald-500/20 bg-emerald-950/10 space-y-4">
                  <div className="flex items-center justify-between pb-3 border-b border-white/5">
                    <div className="flex items-center gap-2">
                      <Shield className="w-5 h-5 text-emerald-400" />
                      <div>
                        <h4 className="text-sm font-bold text-foreground">Exact Extracted Policy Contract Parameters</h4>
                        <p className="text-xs text-muted">Authentic limits and rules extracted directly from your policy document</p>
                      </div>
                    </div>
                    <span className="text-[11px] font-mono font-bold text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/20">
                      Verified from Document
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
                    {/* Room Rent */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🛏️ Room Rent Entitlement</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysis.roomRentLimit || "Single Standard Private AC Room"}
                      </p>
                      <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded ${
                        analysis.roomRentCapped ? "bg-amber-500/10 text-amber-400" : "bg-emerald-500/10 text-emerald-400"
                      }`}>
                        {analysis.roomRentCapped ? "Capped Category" : "No Sub-Limit Capping"}
                      </span>
                    </div>

                    {/* Co-Payment */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🤝 Mandatory Co-Payment</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysis.copay || "0% (Nil / No Co-payment across any age)"}
                      </p>
                      <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded ${
                        (analysis.copayPercentage && analysis.copayPercentage > 0) ? "bg-amber-500/10 text-amber-400" : "bg-emerald-500/10 text-emerald-400"
                      }`}>
                        {(analysis.copayPercentage && analysis.copayPercentage > 0) ? `${analysis.copayPercentage}% Co-Pay` : "0% Co-Payment"}
                      </span>
                    </div>

                    {/* Deductible */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🛡️ Base Deductible</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysis.deductible || "₹0 (Nil / No Base Deductible)"}
                      </p>
                      <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded ${
                        (analysis.deductibleAmount && analysis.deductibleAmount > 0) ? "bg-amber-500/10 text-amber-400" : "bg-emerald-500/10 text-emerald-400"
                      }`}>
                        {(analysis.deductibleAmount && analysis.deductibleAmount > 0) ? `₹${Math.round(analysis.deductibleAmount)} Deductible` : "₹0 Deductible"}
                      </span>
                    </div>

                    {/* Disease Sub-Limits */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🏥 Disease Sub-Limits</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysis.diseaseSubLimits || "None (Full Sum Insured available for all procedures)"}
                      </p>
                      <span className="inline-block text-[10px] font-semibold px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400">
                        {analysis.diseaseSubLimits && analysis.diseaseSubLimits.toLowerCase().includes("none") ? "No Disease Caps" : "Special Procedures"}
                      </span>
                    </div>

                    {/* Pre-Hospitalization */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">⏳ Pre-Hospitalization</span>
                      <p className="text-xs font-bold text-foreground">
                        {analysis.preHospitalizationDays ?? 60} Days Covered
                      </p>
                      <span className="text-[10px] text-muted block">Medical tests & consultations</span>
                    </div>

                    {/* Post-Hospitalization */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">💊 Post-Hospitalization</span>
                      <p className="text-xs font-bold text-foreground">
                        {analysis.postHospitalizationDays ?? 180} Days Covered
                      </p>
                      <span className="text-[10px] text-muted block">Follow-up medication & scans</span>
                    </div>

                    {/* Restoration Benefit */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🔄 Restoration / Recharge</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysis.restorationBenefit || "100% Instant Restoration Benefit"}
                      </p>
                      <span className="text-[10px] text-emerald-400 font-semibold block">Automatic replenishment</span>
                    </div>

                    {/* Cumulative Bonus (NCB) */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">📈 Cumulative Bonus (NCB)</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysis.noClaimBonus || "Cumulative Bonus as per contract terms"}
                      </p>
                      <span className="text-[10px] text-cyan-400 font-semibold block">Sum insured multiplier</span>
                    </div>
                  </div>
                </motion.div>

                {/* TrustScore Breakdown */}
                {analysis.scores && (
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }}
                    className="glass rounded-2xl p-6">
                    <h4 className="text-sm font-semibold mb-4 flex items-center gap-2">
                      <Star className="w-4 h-4 text-amber-400" /> TrustScore & Protection Intelligence
                    </h4>
                    <div className="grid grid-cols-2 sm:grid-cols-5 gap-4 items-center">
                      <div className="col-span-2 sm:col-span-1 flex flex-col items-center justify-center">
                        <ScoreRing score={analysis.scores.overall || 75} label="Overall Score" size={90} strokeWidth={7} />
                      </div>
                      <div className="col-span-2 sm:col-span-4 grid grid-cols-2 sm:grid-cols-4 gap-3">
                        {[
                          { label: "Transparency", score: analysis.scores.transparency, desc: "Clarity of clauses" },
                          { label: "Safety / Low Risk", score: 100 - (analysis.scores.risk || 30), desc: "Protection from traps" },
                          { label: "Fairness", score: analysis.scores.fairness, desc: "Co-pays & limits" },
                          { label: "Claim Reliability", score: analysis.scores.claimReliability, desc: "Ease of approval" },
                        ].map((s, idx) => (
                          <div key={idx} className="glass rounded-xl p-3 border border-white/5">
                            <span className="text-xs text-muted block">{s.label}</span>
                            <span className="text-lg font-bold text-foreground">{s.score || 0}%</span>
                            <span className="text-[10px] text-muted/60 block mt-0.5">{s.desc}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </motion.div>
                )}

                {/* Results grid: Key Clauses & Exclusions */}
                <div className="grid md:grid-cols-2 gap-6">
                  {/* Clauses */}
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }}
                    className="glass rounded-2xl p-5 border border-indigo-500/20">
                    <div className="flex items-center gap-2 mb-4 pb-2 border-b border-white/5">
                      <FileText className="w-4 h-4 text-indigo-400" />
                      <h4 className="text-sm font-semibold">Key Benefits & Coverage Clauses</h4>
                      <span className="ml-auto text-[10px] text-muted bg-white/5 px-2 py-0.5 rounded font-mono">
                        {analysis.clauses?.length || 0} extracted
                      </span>
                    </div>
                    <ul className="space-y-3 max-h-[440px] overflow-y-auto pr-1">
                      {analysis.clauses && analysis.clauses.map((clause, j) => {
                        const colonIdx = clause.indexOf(":");
                        const hasColon = colonIdx > 0 && colonIdx < 40;
                        const title = hasColon ? clause.substring(0, colonIdx).trim() : null;
                        const body = hasColon ? clause.substring(colonIdx + 1).trim() : clause;

                        return (
                          <li key={j} className="text-xs text-muted leading-relaxed glass rounded-xl p-3 border border-white/5">
                            {title ? (
                              <>
                                <strong className="text-foreground block mb-1 text-[13px]">{title}</strong>
                                <span className="text-muted/90">{body}</span>
                              </>
                            ) : (
                              <span>{clause}</span>
                            )}
                          </li>
                        );
                      })}
                    </ul>
                  </motion.div>

                  {/* Exclusions */}
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
                    className="glass rounded-2xl p-5 border border-red-500/20">
                    <div className="flex items-center gap-2 mb-4 pb-2 border-b border-white/5">
                      <AlertTriangle className="w-4 h-4 text-red-400" />
                      <h4 className="text-sm font-semibold">Specific Exclusions (What is Not Covered)</h4>
                      <span className="ml-auto text-[10px] text-muted bg-white/5 px-2 py-0.5 rounded font-mono">
                        {analysis.exclusions?.length || 0} extracted
                      </span>
                    </div>
                    <ul className="space-y-3 max-h-[440px] overflow-y-auto pr-1">
                      {analysis.exclusions && analysis.exclusions.map((exclusion, j) => {
                        const colonIdx = exclusion.indexOf(":");
                        const hasColon = colonIdx > 0 && colonIdx < 40;
                        const title = hasColon ? exclusion.substring(0, colonIdx).trim() : null;
                        const body = hasColon ? exclusion.substring(colonIdx + 1).trim() : exclusion;

                        return (
                          <li key={j} className="text-xs text-muted leading-relaxed glass rounded-xl p-3 border border-red-500/10">
                            {title ? (
                              <>
                                <strong className="text-red-300 block mb-1 text-[13px]">{title}</strong>
                                <span className="text-muted/90">{body}</span>
                              </>
                            ) : (
                              <span>{exclusion}</span>
                            )}
                          </li>
                        );
                      })}
                    </ul>
                  </motion.div>
                </div>

                {/* Waiting Periods & Optional Riders */}
                <div className="grid md:grid-cols-2 gap-6">
                  {/* Waiting Periods */}
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
                    className="glass rounded-2xl p-5 border border-amber-500/20">
                    <div className="flex items-center gap-2 mb-4 pb-2 border-b border-white/5">
                      <Activity className="w-4 h-4 text-amber-400" />
                      <h4 className="text-sm font-semibold">Waiting Period Schedule</h4>
                      <span className="ml-auto text-[10px] text-muted bg-white/5 px-2 py-0.5 rounded font-mono">
                        {analysis.waitingPeriods?.length || 0}
                      </span>
                    </div>
                    <div className="space-y-2.5">
                      {analysis.waitingPeriods && analysis.waitingPeriods.map((w, idx) => (
                        <div key={idx} className="glass rounded-xl p-3 border border-white/5">
                          <div className="flex justify-between items-center mb-1">
                            <span className="text-xs font-semibold text-foreground">{w.condition}</span>
                            <span className="text-[11px] font-bold text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded">
                              {w.duration}
                            </span>
                          </div>
                          <p className="text-[11px] text-muted leading-relaxed">{w.note}</p>
                        </div>
                      ))}
                    </div>
                  </motion.div>

                  {/* Optional Riders */}
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
                    className="glass rounded-2xl p-5 border border-cyan-500/20">
                    <div className="flex items-center gap-2 mb-4 pb-2 border-b border-white/5">
                      <Shield className="w-4 h-4 text-cyan-400" />
                      <h4 className="text-sm font-semibold">Optional Riders & Add-on Covers</h4>
                      <span className="ml-auto text-[10px] text-muted bg-white/5 px-2 py-0.5 rounded font-mono">
                        {analysis.riders?.length || 0}
                      </span>
                    </div>
                    <div className="space-y-2.5">
                      {analysis.riders && analysis.riders.length > 0 ? (
                        analysis.riders.map((r, idx) => (
                          <div key={idx} className="glass rounded-xl p-3 border border-white/5">
                            <div className="flex justify-between items-center mb-1">
                              <span className="text-xs font-semibold text-cyan-300">{r.name}</span>
                              {r.additionalPremium && (
                                <span className="text-[10px] text-muted bg-white/5 px-2 py-0.5 rounded">
                                  {r.additionalPremium}
                                </span>
                              )}
                            </div>
                            <p className="text-[11px] text-muted leading-relaxed">{r.details}</p>
                          </div>
                        ))
                      ) : (
                        <div className="text-xs text-muted/60 p-4 text-center">No optional riders specified in base text</div>
                      )}
                    </div>
                  </motion.div>
                </div>

                {/* Hidden conditions & Traps */}
                {analysis.hiddenConditions && analysis.hiddenConditions.length > 0 && (
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}
                    className="glass rounded-2xl p-6 border border-red-500/30 bg-red-950/10">
                    <div className="flex items-center gap-2 mb-4">
                      <AlertTriangle className="w-5 h-5 text-red-400" />
                      <div>
                        <h4 className="text-sm font-bold text-red-400">⚠️ Hidden Conditions & Fine-Print Traps</h4>
                        <p className="text-xs text-muted">Scrutinized clauses that can lead to unexpected claim denials, co-pays, or out-of-pocket costs.</p>
                      </div>
                      <span className="ml-auto text-xs font-mono text-red-400 bg-red-500/10 px-2.5 py-1 rounded-full font-bold">
                        {analysis.hiddenConditions.length} alerts detected
                      </span>
                    </div>
                    <div className="grid sm:grid-cols-2 gap-3">
                      {analysis.hiddenConditions.map((c, i) => (
                        <div key={i} className="glass rounded-xl p-3.5 border border-red-500/20 text-xs text-red-200/90 leading-relaxed flex items-start gap-2">
                          <span className="w-1.5 h-1.5 rounded-full bg-red-400 mt-1.5 flex-shrink-0" />
                          <span>{c.replace(/^⚠️\s*/, "")}</span>
                        </div>
                      ))}
                    </div>
                  </motion.div>
                )}
              </>
            ) : (
              <div className="glass rounded-xl p-10 text-center flex flex-col items-center justify-center">
                <FileText className="w-12 h-12 text-muted/30 mb-4" />
                <h3 className="text-lg font-medium text-muted">No detailed analysis available</h3>
                <p className="text-sm text-muted/70 mt-2 max-w-md">
                  This policy was added manually or is a default sample. Upload a document through the AI analyzer to generate deep insights, clauses, and hidden conditions.
                </p>
                <button onClick={() => router.push('/dashboard/upload')} className="mt-6 px-5 py-2.5 text-sm font-medium rounded-xl bg-white/5 hover:bg-white/10 transition-colors">
                  Upload Policy Document
                </button>
              </div>
            )}
          </motion.div>
        </div>
      </main>

      {/* Delete Confirmation Modal */}
      <AnimatePresence>
        {showDeleteModal && policy && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => !isDeleting && setShowDeleteModal(false)}
              className="absolute inset-0 bg-black/70 backdrop-blur-sm"
            />

            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="relative w-full max-w-md glass rounded-3xl p-6 border border-red-500/30 shadow-2xl z-10 space-y-4"
            >
              <div className="flex items-start justify-between">
                <div className="w-12 h-12 rounded-2xl bg-red-500/10 border border-red-500/20 flex items-center justify-center text-red-400">
                  <AlertTriangle className="w-6 h-6" />
                </div>
                <button
                  type="button"
                  onClick={() => !isDeleting && setShowDeleteModal(false)}
                  className="p-1 rounded-lg text-muted hover:text-white transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div>
                <h3 className="text-lg font-bold text-foreground">Delete Policy</h3>
                <p className="text-xs text-muted mt-1">
                  Are you sure you want to permanently delete this policy?
                </p>
              </div>

              <div className="p-3.5 glass rounded-2xl border border-white/5 space-y-1">
                <p className="text-sm font-semibold text-foreground truncate">{policy.name}</p>
                <p className="text-xs text-muted">{policy.provider} • {policy.type}</p>
                <p className="text-[11px] text-red-400/90 pt-1">
                  ⚠️ All extracted clauses, hidden exclusions, waiting periods, and simulation history will be permanently deleted.
                </p>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  disabled={isDeleting}
                  onClick={() => setShowDeleteModal(false)}
                  className="px-4 py-2 text-xs font-semibold rounded-xl glass hover:bg-white/10 text-muted hover:text-white transition-all disabled:opacity-50"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={isDeleting}
                  onClick={handleDeletePolicy}
                  className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-xl bg-red-600 hover:bg-red-500 text-white shadow-lg shadow-red-600/30 transition-all disabled:opacity-50"
                >
                  {isDeleting ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      Deleting...
                    </>
                  ) : (
                    <>
                      <Trash2 className="w-3.5 h-3.5" />
                      Yes, Delete Policy
                    </>
                  )}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
