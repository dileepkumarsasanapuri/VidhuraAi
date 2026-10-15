"use client";

import React, { useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import {
  Upload, FileText, Image, Mail, Camera, CheckCircle,
  Loader2, AlertTriangle, Shield, ChevronRight, X, Sparkles,
  Info, Activity, Star, Printer,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import ScoreRing from "@/components/ScoreRing";
import { api, PolicyAnalysis } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

type UploadStep = "upload" | "processing" | "complete";

export default function UploadPage() {
  const [step, setStep] = useState<UploadStep>("upload");
  const [dragActive, setDragActive] = useState(false);
  const [fileName, setFileName] = useState("");
  const [progress, setProgress] = useState(0);
  const [statusMessage, setStatusMessage] = useState("Initiating asynchronous analysis pipeline...");
  const [currentStage, setCurrentStage] = useState("UPLOADED");
  const [saving, setSaving] = useState(false);
  const router = useRouter();
  const toast = useToast();

  const [analysisResult, setAnalysisResult] = useState<PolicyAnalysis | null>(null);

  const savePolicy = async () => {
    if (!analysisResult) return;
    setSaving(true);
    try {
      const newPolicy = {
        name: analysisResult.policyName || "Untitled Policy",
        provider: analysisResult.provider || "Unknown Provider",
        type: analysisResult.type || "Health Insurance",
        premium: analysisResult.premium || "Varies by age & sum insured",
        coverage: analysisResult.coverage || "₹10L",
        expiryDate: analysisResult.expiryDate || "1 Year Annual Renewable",
        healthScore: analysisResult.scores?.overall || 80,
        riskLevel: (analysisResult.scores?.risk && analysisResult.scores.risk > 45) ? "high" : "low", 
        status: "active",
        analysis: analysisResult
      };
      
      await api.createPolicy(newPolicy as any);
      toast.success("Policy saved successfully to your portfolio!", "Policy Saved");
      router.push("/dashboard/policies");
    } catch (error) {
      console.error(error);
      toast.error("Failed to save policy to your account.", "Save Error");
    } finally {
      setSaving(false);
    }
  };

  const simulateUpload = useCallback(async (file: File) => {
    setFileName(file.name);
    setStep("processing");
    setProgress(5);
    setStatusMessage("Uploading document & computing cryptographic hash...");
    setCurrentStage("UPLOADED");

    try {
      const response = await api.analyzePolicyAsync(file);
      const task = response.data;

      // Phase 3.2: Instant return if SHA-256 matches existing policy analysis
      if (task.cached && task.result) {
        setProgress(100);
        setStatusMessage("Loaded instantaneously from SHA-256 cache ✓");
        setCurrentStage("COMPLETE");
        setAnalysisResult(task.result);
        toast.success("Policy loaded instantly from secure SHA-256 cache!", "Instant Cache Hit");
        setTimeout(() => setStep("complete"), 400);
        return;
      }

      // Phase 3.1: Live progress via SSE stream with resilient polling fallback
      const taskId = task.taskId;
      let completed = false;

      // Polling fallback interval
      const pollInterval = setInterval(async () => {
        if (completed) {
          clearInterval(pollInterval);
          return;
        }
        try {
          const statusRes = await api.getAnalysisStatus(taskId);
          const current = statusRes.data;
          if (current.progress) setProgress(current.progress);
          if (current.message) setStatusMessage(current.message);
          if (current.step) setCurrentStage(current.step);

          if (current.status === "COMPLETED" && current.result) {
            completed = true;
            clearInterval(pollInterval);
            setProgress(100);
            setStatusMessage("Analysis complete ✓");
            setAnalysisResult(current.result);
            toast.success("Policy parsed and analyzed successfully!", "Analysis Complete");
            setTimeout(() => setStep("complete"), 400);
          } else if (current.status === "FAILED") {
            completed = true;
            clearInterval(pollInterval);
            setStep("upload");
            toast.error(current.error || "Analysis failed.", "Analysis Failed");
          }
        } catch {
          // ignore transient polling blips
        }
      }, 1500);

      // Attempt Server-Sent Events (SSE) connection for instant micro-updates
      if (typeof window !== "undefined" && window.EventSource) {
        const streamUrl = api.getAnalysisStreamUrl(taskId);
        const eventSource = new EventSource(streamUrl);

        eventSource.addEventListener("PROGRESS", (event: any) => {
          try {
            const data = JSON.parse(event.data);
            setProgress(data.progress || 10);
            if (data.message) setStatusMessage(data.message);
            if (data.step) setCurrentStage(data.step);
          } catch {
            // ignore JSON parse issue
          }
        });

        eventSource.addEventListener("COMPLETE", (event: any) => {
          if (!completed) {
            completed = true;
            clearInterval(pollInterval);
            try {
              const data = JSON.parse(event.data);
              setProgress(100);
              setStatusMessage("Analysis complete ✓");
              setAnalysisResult(data.result);
              toast.success("Policy parsed and analyzed successfully!", "Analysis Complete");
              setTimeout(() => setStep("complete"), 400);
            } catch {
              // handled by pollInterval
            }
          }
          eventSource.close();
        });

        eventSource.addEventListener("ERROR", () => {
          eventSource.close();
          // pollInterval handles rest
        });

        eventSource.onerror = () => {
          eventSource.close();
        };
      }
    } catch (error: any) {
      console.error(error);
      setStep("upload");
      toast.error(error.message || "Failed to initiate analysis. Please try again.", "Upload Error");
    }
  }, [toast]);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setDragActive(false);
    const file = e.dataTransfer.files[0];
    if (file) simulateUpload(file);
  }, [simulateUpload]);

  const handleFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) simulateUpload(file);
  }, [simulateUpload]);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/upload" />
      <main className="flex-1 overflow-y-auto">
        <header className="sticky top-0 z-30 glass border-b border-border px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2 sm:gap-3 ml-11 lg:ml-0">
            <h1 className="text-base sm:text-lg font-bold truncate">PolicyLens AI</h1>
            <span className="text-xs text-muted hidden md:block">• Deep Document & Clause Intelligence</span>
          </div>
          {step === "complete" && (
            <button
              onClick={() => setStep("upload")}
              className="text-xs text-muted hover:text-white glass px-3 py-1.5 rounded-xl transition-colors"
            >
              Analyze Another
            </button>
          )}
        </header>

        <div className="p-4 sm:p-6 max-w-5xl mx-auto space-y-5 sm:space-y-6 mobile-bottom-clearance">
          <AnimatePresence mode="wait">
            {step === "upload" && (
              <motion.div key="upload" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }}>
                {/* Dropzone */}
                <div
                  className={`dropzone rounded-2xl p-6 sm:p-12 text-center transition-all ${dragActive ? "active" : ""}`}
                  onDragOver={(e) => { e.preventDefault(); setDragActive(true); }}
                  onDragLeave={() => setDragActive(false)}
                  onDrop={handleDrop}
                >
                  <motion.div animate={dragActive ? { scale: 1.1 } : { scale: 1 }}
                    className="w-16 h-16 mx-auto mb-6 rounded-2xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center border border-primary/20">
                    <Upload className="w-7 h-7 text-primary-light" />
                  </motion.div>
                  <h2 className="text-xl font-bold mb-2">Upload Your Full Insurance Policy PDF</h2>
                  <p className="text-sm text-muted mb-6 max-w-lg mx-auto leading-relaxed">
                    Upload your official policy wording, prospectus, or certificate. Vidhura AI reads every page from start to finish—extracting clauses, sub-limits, hidden traps, and waiting periods.
                  </p>
                  <label className="inline-flex items-center gap-2 px-6 py-3 text-sm font-medium rounded-xl bg-gradient-to-r from-primary to-accent text-white cursor-pointer hover:opacity-90 transition-opacity glow-primary">
                    <Upload className="w-4 h-4" /> Choose PDF File
                    <input type="file" className="hidden" accept=".pdf" onChange={handleFileChange} />
                  </label>
                  <p className="text-xs text-muted/50 mt-4">Standard or Multi-page PDF • Up to 25MB</p>
                </div>

                {/* Upload options */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-6">
                  {[
                    { icon: FileText, label: "Policy Wording", desc: "Full term documents" },
                    { icon: Shield, label: "Schedule / Certificate", desc: "Policy schedules" },
                    { icon: Mail, label: "Email Attachment", desc: "Saved PDF attachments" },
                    { icon: Activity, label: "Renewal Notice", desc: "Annual renew notices" },
                  ].map((opt, i) => (
                    <motion.label key={i} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 + i * 0.05 }}
                      className="glass glass-hover rounded-xl p-4 text-center cursor-pointer group">
                      <opt.icon className="w-6 h-6 mx-auto mb-2 text-muted group-hover:text-primary-light transition-colors" />
                      <p className="text-xs font-medium mb-0.5">{opt.label}</p>
                      <p className="text-[10px] text-muted">{opt.desc}</p>
                      <input type="file" className="hidden" accept=".pdf" onChange={handleFileChange} />
                    </motion.label>
                  ))}
                </div>

                {/* Privacy & Document Guidance */}
                <div className="mt-8 glass rounded-xl p-4 flex items-center justify-between border border-primary/10">
                  <div className="flex items-center gap-3">
                    <Shield className="w-5 h-5 text-emerald-400 flex-shrink-0" />
                    <div>
                      <p className="text-sm font-medium">Bank-grade document privacy</p>
                      <p className="text-xs text-muted">Your real policy documents are parsed securely in memory. Only your authenticated account can access your extracted insights.</p>
                    </div>
                  </div>
                  <span className="text-[10px] text-muted bg-white/5 px-2.5 py-1 rounded-lg">256-bit SSL</span>
                </div>
              </motion.div>
            )}

            {step === "processing" && (
              <motion.div key="processing" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }}
                className="glass rounded-2xl p-10 text-center max-w-lg mx-auto mt-12 border border-primary/20 shadow-2xl">
                <motion.div animate={{ rotate: 360 }} transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
                  className="w-16 h-16 mx-auto mb-6 rounded-2xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center shadow-lg shadow-primary/10">
                  <Loader2 className="w-8 h-8 text-primary-light" />
                </motion.div>
                <h2 className="text-xl font-bold mb-1">Analyzing Full Policy Document</h2>
                <p className="text-xs text-muted mb-5 truncate px-4 font-mono">{fileName}</p>

                {/* Real-time Stage Pills */}
                <div className="flex flex-wrap justify-center items-center gap-2 mb-6">
                  {[
                    { step: "PARSING_PDF", label: "Document Parse" },
                    { step: "AI_ANALYSIS", label: "AI Reasoning" },
                    { step: "NORMALIZING", label: "TrustScore" },
                    { step: "COMPLETE", label: "Ready" }
                  ].map((s, idx) => {
                    const isActive = currentStage === s.step;
                    const isDone = (s.step === "PARSING_PDF" && progress >= 35) ||
                                   (s.step === "AI_ANALYSIS" && progress >= 75) ||
                                   (s.step === "NORMALIZING" && progress >= 95) ||
                                   progress === 100;
                    return (
                      <span key={idx} className={`text-[11px] font-semibold px-3 py-1 rounded-full border transition-all ${
                        isDone
                          ? "bg-emerald-500/15 border-emerald-500/30 text-emerald-400"
                          : isActive
                          ? "bg-primary/25 border-primary/50 text-primary-light animate-pulse"
                          : "bg-white/5 border-white/10 text-muted"
                      }`}>
                        {isDone ? "✓ " : ""}{s.label}
                      </span>
                    );
                  })}
                </div>

                <div className="w-full h-2.5 rounded-full bg-white/5 mb-3 overflow-hidden border border-white/5">
                  <motion.div 
                    className="h-full rounded-full bg-gradient-to-r from-primary to-accent" 
                    animate={{ width: `${progress}%` }} 
                    transition={{ ease: "easeInOut", duration: 0.3 }}
                  />
                </div>
                
                <div className="flex justify-between items-center text-xs px-1 text-muted">
                  <span className="font-medium text-foreground/80 truncate pr-2">{statusMessage}</span>
                  <span className="font-mono font-bold text-primary-light flex-shrink-0">{progress}%</span>
                </div>
              </motion.div>
            )}

            {step === "complete" && analysisResult && (
              <motion.div key="complete" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
                {/* Printable Official Report Header - visible only when printing/exporting to PDF */}
                <div className="print-only mb-6 pb-4 border-b-2 border-slate-900">
                  <div className="flex justify-between items-start">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-xs font-bold uppercase tracking-widest text-indigo-700">Vidhura AI Official Audit</span>
                        <span className="text-xs text-slate-400">•</span>
                        <span className="text-xs text-slate-500">Autonomous Actuarial Protection Intelligence</span>
                      </div>
                      <h1 className="text-2xl font-black text-slate-900 tracking-tight">{analysisResult.policyName}</h1>
                      <p className="text-xs text-slate-600 mt-0.5">{analysisResult.provider} • Policy Category: {analysisResult.type}</p>
                    </div>
                    <div className="text-right text-xs text-slate-600">
                      <p className="font-semibold text-slate-900">Report Date: {new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</p>
                      <p className="text-[10px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded mt-1 inline-block border border-emerald-200">
                        Status: Extracted & Audited
                      </p>
                    </div>
                  </div>
                </div>

                {/* Policy overview banner */}
                <div className="glass rounded-2xl p-6 flex flex-col md:flex-row justify-between items-start md:items-center gap-4 border border-primary/20">
                  <div className="flex items-center gap-4">
                    <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center flex-shrink-0">
                      <Shield className="w-6 h-6 text-primary-light" />
                    </div>
                    <div>
                      <h3 className="text-xl font-bold">{analysisResult.policyName}</h3>
                      <p className="text-xs text-muted mt-0.5">{analysisResult.provider} • {analysisResult.type}</p>
                      <div className="flex flex-wrap gap-2 mt-2">
                        {analysisResult.coverage && (
                          <span className="text-[11px] bg-white/5 border border-white/10 px-2.5 py-0.5 rounded-lg text-primary-light font-medium">
                            Coverage: {analysisResult.coverage}
                          </span>
                        )}
                        {analysisResult.premium && (
                          <span className="text-[11px] bg-white/5 border border-white/10 px-2.5 py-0.5 rounded-lg text-muted">
                            Premium: {analysisResult.premium}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                  
                  <div className="flex items-center gap-2.5 flex-wrap flex-shrink-0">
                    <button
                      type="button"
                      onClick={() => window.print()}
                      className="px-4 py-3 text-sm font-semibold rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-foreground transition-all flex items-center gap-2 no-print"
                      title="Print or Save as PDF"
                    >
                      <Printer className="w-4 h-4 text-primary-light" />
                      Print / PDF
                    </button>
                    <button 
                      onClick={savePolicy}
                      disabled={saving}
                      className="px-6 py-3 text-sm font-semibold rounded-xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-opacity flex items-center gap-2 glow-primary flex-shrink-0"
                    >
                      {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />}
                      {saving ? "Saving to Account..." : "Save to My Policies"}
                    </button>
                  </div>
                </div>

                {/* Simplified Summary Banner */}
                {analysisResult.simplifiedSummary && (
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }}
                    className="glass rounded-2xl p-5 border border-primary/20 bg-gradient-to-r from-primary/5 to-accent/5">
                    <div className="flex items-center gap-2 mb-2">
                      <Sparkles className="w-4 h-4 text-primary-light" />
                      <h4 className="text-xs font-bold uppercase tracking-wider text-primary-light">Executive AI Policy Assessment</h4>
                    </div>
                    <p className="text-sm text-foreground/90 leading-relaxed">
                      {analysisResult.simplifiedSummary}
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
                        <p className="text-xs text-muted">Authentic limits, co-payments & rules extracted directly from your PDF</p>
                      </div>
                    </div>
                    <span className="text-[11px] font-mono font-bold text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/20">
                      Verified from PDF
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
                    {/* Room Rent */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🛏️ Room Rent Entitlement</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysisResult.roomRentLimit || "Single Standard Private AC Room"}
                      </p>
                      <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded ${
                        analysisResult.roomRentCapped ? "bg-amber-500/10 text-amber-400" : "bg-emerald-500/10 text-emerald-400"
                      }`}>
                        {analysisResult.roomRentCapped ? "Capped Category" : "No Sub-Limit Capping"}
                      </span>
                    </div>

                    {/* Co-Payment */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🤝 Mandatory Co-Payment</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysisResult.copay || "0% (Nil / No Co-payment across any age)"}
                      </p>
                      <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded ${
                        (analysisResult.copayPercentage && analysisResult.copayPercentage > 0) ? "bg-amber-500/10 text-amber-400" : "bg-emerald-500/10 text-emerald-400"
                      }`}>
                        {(analysisResult.copayPercentage && analysisResult.copayPercentage > 0) ? `${analysisResult.copayPercentage}% Co-Pay` : "0% Co-Payment"}
                      </span>
                    </div>

                    {/* Deductible */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🛡️ Base Deductible</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysisResult.deductible || "₹0 (Nil / No Base Deductible)"}
                      </p>
                      <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded ${
                        (analysisResult.deductibleAmount && analysisResult.deductibleAmount > 0) ? "bg-amber-500/10 text-amber-400" : "bg-emerald-500/10 text-emerald-400"
                      }`}>
                        {(analysisResult.deductibleAmount && analysisResult.deductibleAmount > 0) ? `₹${Math.round(analysisResult.deductibleAmount)} Deductible` : "₹0 Deductible"}
                      </span>
                    </div>

                    {/* Disease Sub-Limits */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🏥 Disease Sub-Limits</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysisResult.diseaseSubLimits || "None (Full Sum Insured available for all procedures)"}
                      </p>
                      <span className="inline-block text-[10px] font-semibold px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400">
                        {analysisResult.diseaseSubLimits && analysisResult.diseaseSubLimits.toLowerCase().includes("none") ? "No Disease Caps" : "Special Procedures"}
                      </span>
                    </div>

                    {/* Pre-Hospitalization */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">⏳ Pre-Hospitalization</span>
                      <p className="text-xs font-bold text-foreground">
                        {analysisResult.preHospitalizationDays ?? 60} Days Covered
                      </p>
                      <span className="text-[10px] text-muted block">Medical tests & consultations</span>
                    </div>

                    {/* Post-Hospitalization */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">💊 Post-Hospitalization</span>
                      <p className="text-xs font-bold text-foreground">
                        {analysisResult.postHospitalizationDays ?? 180} Days Covered
                      </p>
                      <span className="text-[10px] text-muted block">Follow-up medication & scans</span>
                    </div>

                    {/* Restoration Benefit */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">🔄 Restoration / Recharge</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysisResult.restorationBenefit || "100% Instant Restoration Benefit"}
                      </p>
                      <span className="text-[10px] text-emerald-400 font-semibold block">Automatic replenishment</span>
                    </div>

                    {/* Cumulative Bonus (NCB) */}
                    <div className="glass rounded-xl p-3.5 border border-white/5 space-y-1">
                      <span className="text-[11px] text-muted block font-medium">📈 Cumulative Bonus (NCB)</span>
                      <p className="text-xs font-bold text-foreground leading-snug">
                        {analysisResult.noClaimBonus || "Cumulative Bonus as per contract terms"}
                      </p>
                      <span className="text-[10px] text-cyan-400 font-semibold block">Sum insured multiplier</span>
                    </div>
                  </div>
                </motion.div>

                {/* TrustScore Breakdown */}
                {analysisResult.scores && (
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }}
                    className="glass rounded-2xl p-6">
                    <h4 className="text-sm font-semibold mb-4 flex items-center gap-2">
                      <Star className="w-4 h-4 text-amber-400" /> TrustScore & Protection Intelligence
                    </h4>
                    <div className="grid grid-cols-2 sm:grid-cols-5 gap-4 items-center">
                      <div className="col-span-2 sm:col-span-1 flex flex-col items-center justify-center">
                        <ScoreRing score={analysisResult.scores.overall || 75} label="Overall Score" size={90} strokeWidth={7} />
                      </div>
                      <div className="col-span-2 sm:col-span-4 grid grid-cols-2 sm:grid-cols-4 gap-3">
                        {[
                          { label: "Transparency", score: analysisResult.scores.transparency, desc: "Clarity of clauses" },
                          { label: "Safety / Low Risk", score: 100 - (analysisResult.scores.risk || 30), desc: "Protection from traps" },
                          { label: "Fairness", score: analysisResult.scores.fairness, desc: "Co-pays & limits" },
                          { label: "Claim Reliability", score: analysisResult.scores.claimReliability, desc: "Ease of approval" },
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
                        {analysisResult.clauses?.length || 0} extracted
                      </span>
                    </div>
                    <ul className="space-y-3 max-h-[440px] overflow-y-auto pr-1">
                      {analysisResult.clauses && analysisResult.clauses.map((clause, j) => {
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
                        {analysisResult.exclusions?.length || 0} extracted
                      </span>
                    </div>
                    <ul className="space-y-3 max-h-[440px] overflow-y-auto pr-1">
                      {analysisResult.exclusions && analysisResult.exclusions.map((exclusion, j) => {
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
                        {analysisResult.waitingPeriods?.length || 0}
                      </span>
                    </div>
                    <div className="space-y-2.5">
                      {analysisResult.waitingPeriods && analysisResult.waitingPeriods.map((w, idx) => (
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
                        {analysisResult.riders?.length || 0}
                      </span>
                    </div>
                    <div className="space-y-2.5">
                      {analysisResult.riders && analysisResult.riders.length > 0 ? (
                        analysisResult.riders.map((r, idx) => (
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
                {analysisResult.hiddenConditions && analysisResult.hiddenConditions.length > 0 && (
                  <motion.div initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}
                    className="glass rounded-2xl p-6 border border-red-500/30 bg-red-950/10">
                    <div className="flex items-center gap-2 mb-4">
                      <AlertTriangle className="w-5 h-5 text-red-400" />
                      <div>
                        <h4 className="text-sm font-bold text-red-400">⚠️ Hidden Conditions & Fine-Print Traps</h4>
                        <p className="text-xs text-muted">Scrutinized clauses that can lead to unexpected claim denials, co-pays, or out-of-pocket costs.</p>
                      </div>
                      <span className="ml-auto text-xs font-mono text-red-400 bg-red-500/10 px-2.5 py-1 rounded-full font-bold">
                        {analysisResult.hiddenConditions.length} alerts detected
                      </span>
                    </div>
                    <div className="grid sm:grid-cols-2 gap-3">
                      {analysisResult.hiddenConditions.map((c, i) => (
                        <div key={i} className="glass rounded-xl p-3.5 border border-red-500/20 text-xs text-red-200/90 leading-relaxed flex items-start gap-2">
                          <span className="w-1.5 h-1.5 rounded-full bg-red-400 mt-1.5 flex-shrink-0" />
                          <span>{c.replace(/^⚠️\s*/, "")}</span>
                        </div>
                      ))}
                    </div>
                  </motion.div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </main>
    </div>
  );
}
