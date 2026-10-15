"use client";

import React, { useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  AlertTriangle,
  FileText,
  CheckCircle,
  XCircle,
  HelpCircle,
  ChevronRight,
  Calculator,
  Clock,
  TrendingUp,
  ShieldAlert,
  Building2,
  Percent,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import ScoreRing from "@/components/ScoreRing";
import { api, ClaimPrediction } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

interface ClaimScenario {
  type: string;
  disease: string;
  hospitalBill: number;
  result: ClaimPrediction;
}

const presets: { label: string; type: string; disease: string; bill: number }[] = [
  { label: "🦟 Dengue Inpatient Care", type: "Hospitalization", disease: "Dengue Inpatient Care", bill: 95000 },
  { label: "👁️ Cataract Eye Surgery", type: "Surgery", disease: "Cataract Surgery", bill: 75000 },
  { label: "🦵 Knee Joint Replacement", type: "Surgery", disease: "Knee Replacement", bill: 350000 },
  { label: "🫀 Heart Bypass Surgery", type: "Surgery", disease: "Bypass Surgery", bill: 450000 },
  { label: "🤖 Modern Robotic Surgery", type: "Surgery", disease: "Modern Robotic Surgery", bill: 280000 },
  { label: "🩺 Renal Stone Laser Treatment", type: "Surgery", disease: "Renal Stone Laser", bill: 85000 },
  { label: "🩹 Hernia Mesh Repair", type: "Surgery", disease: "Hernia Mesh Surgery", bill: 95000 },
  { label: "🏥 Acute Appendectomy", type: "Surgery", disease: "Appendectomy", bill: 120000 },
];

export default function ClaimsPage() {
  const toast = useToast();
  const [disease, setDisease] = useState("Dengue");
  const [billStr, setBillStr] = useState("95000");
  const [type, setType] = useState("Hospitalization");
  const [hospitalType, setHospitalType] = useState<"network" | "non-network">("network");
  const [roomType, setRoomType] = useState<string>("single");
  const [memberAge, setMemberAge] = useState<number>(35);
  const [isPreExisting, setIsPreExisting] = useState<boolean>(false);
  const [availablePolicies, setAvailablePolicies] = useState<any[]>([]);
  const [selectedPolicyId, setSelectedPolicyId] = useState<string>("");
  const [result, setResult] = useState<ClaimScenario | null>(null);
  const [loading, setLoading] = useState(false);

  React.useEffect(() => {
    async function loadPolicies() {
      try {
        const response = await api.getPolicies();
        if (response.data) {
          setAvailablePolicies(response.data);
          if (response.data.length > 0) setSelectedPolicyId(response.data[0].id);
        }
      } catch (error) {
        console.error("Failed to load policies", error);
      }
    }
    loadPolicies();
  }, []);

  const handleSimulate = async () => {
    const bill = parseInt(billStr) || 0;
    if (bill > 0) {
      setLoading(true);
      try {
        const response = await api.predictClaim({
          type,
          disease,
          hospitalBill: bill,
          policyId: selectedPolicyId,
          memberAge,
          isPreExisting,
          hospitalType,
          roomType,
        });
        setResult({ type, disease, hospitalBill: bill, result: response.data });
        toast.success("Claim predicted and evaluated successfully!", "Simulation Ready");
      } catch (error) {
        console.error("Failed to predict claim", error);
        toast.error("Failed to predict claim outcome. Please verify inputs.", "Prediction Failed");
      } finally {
        setLoading(false);
      }
    }
  };

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/claims" />
      <main className="flex-1 overflow-y-auto">
        <header className="sticky top-0 z-30 glass border-b border-border px-6 h-16 flex items-center justify-between">
          <div className="ml-12 lg:ml-0">
            <h1 className="text-lg font-bold">ClaimShield AI</h1>
            <p className="text-xs text-muted">Predict claim outcomes, deductions & approval probability</p>
          </div>
          {availablePolicies.length > 0 && (
            <span className="text-xs text-muted bg-white/5 px-3 py-1.5 rounded-xl hidden sm:inline-block">
              {availablePolicies.length} Active {availablePolicies.length === 1 ? "Policy" : "Policies"}
            </span>
          )}
        </header>

        <div className="p-6 max-w-5xl mx-auto space-y-6">
          {/* Input form */}
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="glass rounded-2xl p-6">
            <h3 className="text-base font-semibold mb-4 flex items-center gap-2">
              <Calculator className="w-5 h-5 text-primary-light" /> Claim Outcome Simulator & Fine-Print Auditor
            </h3>

            {/* Row 1: Primary Claim Inputs */}
            <div className="grid sm:grid-cols-3 gap-4 mb-4">
              <div>
                <label className="text-xs text-muted mb-1 block">Claim Category</label>
                <select
                  value={type}
                  onChange={(e) => setType(e.target.value)}
                  className="w-full glass rounded-xl px-3 py-2.5 text-sm outline-none bg-background border border-white/5"
                >
                  <option value="Hospitalization">Hospitalization</option>
                  <option value="Surgery">Surgery</option>
                  <option value="Day Care">Day Care Procedure</option>
                  <option value="OPD">OPD Consultation</option>
                </select>
              </div>
              <div>
                <label className="text-xs text-muted mb-1 block">Ailment / Surgical Procedure</label>
                <input
                  type="text"
                  value={disease}
                  onChange={(e) => setDisease(e.target.value)}
                  className="w-full glass rounded-xl px-3 py-2.5 text-sm outline-none bg-background border border-white/5 focus:border-primary-light"
                  placeholder="e.g., Cataract Surgery"
                />
              </div>
              <div>
                <label className="text-xs text-muted mb-1 block">Total Hospital Bill (₹)</label>
                <input
                  type="number"
                  value={billStr}
                  onChange={(e) => setBillStr(e.target.value)}
                  className="w-full glass rounded-xl px-3 py-2.5 text-sm outline-none bg-background border border-white/5 focus:border-primary-light"
                  placeholder="e.g., 95000"
                />
              </div>
            </div>

            {/* Row 2: Actuarial Fine-Print Parameters */}
            <div className="grid sm:grid-cols-4 gap-4 mb-4 pt-4 border-t border-white/5">
              <div>
                <label className="text-xs text-muted mb-1 block">Hospital Network</label>
                <select
                  value={hospitalType}
                  onChange={(e) => setHospitalType(e.target.value as any)}
                  className="w-full glass rounded-xl px-3 py-2 text-xs outline-none bg-background border border-white/5"
                >
                  <option value="network">Network (Cashless Approval)</option>
                  <option value="non-network">Non-Network (Reimbursement)</option>
                </select>
              </div>
              <div>
                <label className="text-xs text-muted mb-1 block">Room Category</label>
                <select
                  value={roomType}
                  onChange={(e) => setRoomType(e.target.value)}
                  className="w-full glass rounded-xl px-3 py-2 text-xs outline-none bg-background border border-white/5"
                >
                  <option value="single">Single Standard AC Room</option>
                  <option value="deluxe">Deluxe Room</option>
                  <option value="suite">Suite / Executive Room</option>
                </select>
              </div>
              <div>
                <label className="text-xs text-muted mb-1 block">Patient Age (Years)</label>
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={memberAge}
                  onChange={(e) => setMemberAge(parseInt(e.target.value) || 35)}
                  className="w-full glass rounded-xl px-3 py-2 text-xs outline-none bg-background border border-white/5 focus:border-primary-light"
                />
              </div>
              <div>
                <label className="text-xs text-muted mb-1 block">Pre-Existing Disease (PED)?</label>
                <button
                  type="button"
                  onClick={() => setIsPreExisting(!isPreExisting)}
                  className={`w-full py-2 px-3 rounded-xl text-xs font-semibold transition-all border ${
                    isPreExisting
                      ? "bg-rose-500/20 text-rose-300 border-rose-500/40"
                      : "bg-emerald-500/10 text-emerald-300 border-emerald-500/30"
                  }`}
                >
                  {isPreExisting ? "⚠️ Yes (PED Check)" : "✅ No (Clean History)"}
                </button>
              </div>
            </div>

            {/* Policy Selector & Exact Parameters */}
            <div className="mb-4">
              <label className="text-xs text-muted mb-1 block">Audited Policy Contract</label>
              {availablePolicies.length > 0 ? (
                <>
                  <select
                    value={selectedPolicyId}
                    onChange={(e) => setSelectedPolicyId(e.target.value)}
                    className="w-full md:w-1/2 glass rounded-xl px-3 py-2.5 text-sm outline-none bg-background border border-white/5"
                  >
                    {availablePolicies.map((p) => (
                      <option key={p.id} value={p.id} className="bg-background">
                        {p.name} ({p.provider})
                      </option>
                    ))}
                  </select>

                  {/* Exact Extracted Policy Terms Pill */}
                  {(() => {
                    const activeP = availablePolicies.find((p) => p.id === selectedPolicyId);
                    if (activeP && activeP.analysis) {
                      const a = activeP.analysis;
                      return (
                        <div className="mt-3 p-3 rounded-xl bg-white/5 border border-white/10 text-xs space-y-1.5">
                          <div className="flex items-center gap-2 text-primary-light font-semibold">
                            <span>📄 Contract Terms Extracted for {activeP.name}:</span>
                          </div>
                          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[11px] text-muted">
                            <div className="glass p-2 rounded-lg">
                              <span className="block text-foreground font-medium">Room Rent:</span>
                              <span>{a.roomRentLimit || "Single Private AC Room (No Capping)"}</span>
                            </div>
                            <div className="glass p-2 rounded-lg">
                              <span className="block text-foreground font-medium">Co-Payment:</span>
                              <span className="text-emerald-400 font-semibold">{a.copay || "0% (Nil)"}</span>
                            </div>
                            <div className="glass p-2 rounded-lg">
                              <span className="block text-foreground font-medium">Deductible:</span>
                              <span className="text-cyan-400 font-semibold">{a.deductible || "₹0 (Nil)"}</span>
                            </div>
                            <div className="glass p-2 rounded-lg">
                              <span className="block text-foreground font-medium">Sub-Limits:</span>
                              <span>{a.diseaseSubLimits || "None (No disease caps)"}</span>
                            </div>
                          </div>
                        </div>
                      );
                    }
                    return null;
                  })()}
                </>
              ) : (
                <div className="flex flex-wrap items-center gap-2 py-1.5 text-xs">
                  <span className="bg-white/5 border border-white/10 px-3 py-1.5 rounded-xl text-primary-light font-medium">
                    Auditing under Benchmark Comprehensive Health Plan (₹10L Tier, Standard IRDAI Rules)
                  </span>
                  <Link href="/dashboard/upload" className="text-muted hover:text-white underline ml-1">
                    Upload custom policy →
                  </Link>
                </div>
              )}
            </div>

            {/* Presets */}
            <div className="flex flex-wrap gap-2 mb-6">
              {presets.map((p) => (
                <button
                  key={p.label}
                  onClick={() => {
                    setType(p.type);
                    setDisease(p.disease);
                    setBillStr(p.bill.toString());
                  }}
                  className="text-xs px-3 py-1.5 rounded-lg glass glass-hover text-muted hover:text-foreground border border-white/5"
                >
                  {p.label}
                </button>
              ))}
            </div>

            <button
              onClick={handleSimulate}
              disabled={loading}
              className="px-6 py-2.5 text-sm font-semibold rounded-xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 disabled:opacity-50 transition-opacity glow-primary flex items-center gap-2"
            >
              <Calculator className="w-4 h-4" />
              {loading ? "Calculating..." : "Predict Claim Outcome"}
            </button>
          </motion.div>

          {/* Results */}
          {result && (
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
              {/* Header banner */}
              <div className="glass rounded-xl p-4 border border-primary/20 flex flex-col sm:flex-row justify-between sm:items-center gap-3">
                <div>
                  <h4 className="text-xs uppercase font-bold text-primary-light">Audited Policy</h4>
                  <p className="text-sm font-bold text-foreground">
                    {availablePolicies.find((p) => p.id === selectedPolicyId)?.name || "Standard Comprehensive Health Plan"}
                  </p>
                </div>
                {result.result.timeline && (
                  <div className="flex items-center gap-2 text-xs text-muted bg-white/5 px-3 py-1.5 rounded-xl border border-white/5">
                    <Clock className="w-3.5 h-3.5 text-cyan-400" />
                    <span>{result.result.timeline}</span>
                  </div>
                )}
              </div>

              {/* Stat Cards Overview */}
              <div className="grid sm:grid-cols-4 gap-4">
                <div className="glass rounded-xl p-4 text-center flex flex-col items-center justify-center">
                  <ScoreRing score={result.result.approvalProbability} label="Approval Probability" size={90} strokeWidth={6} />
                </div>
                <div className="glass rounded-xl p-5 border border-white/5">
                  <p className="text-xs text-muted mb-1">Total Hospital Bill</p>
                  <p className="text-2xl font-bold">₹{result.hospitalBill.toLocaleString()}</p>
                  <span className="text-[10px] text-muted block mt-1">Submitted claim amount</span>
                </div>
                <div className="glass rounded-xl p-5 border border-emerald-500/20 bg-emerald-500/5">
                  <p className="text-xs text-emerald-400 mb-1">Projected Insurance Payout</p>
                  <p className="text-2xl font-bold text-emerald-400">₹{Math.round(result.result.coveredAmount).toLocaleString()}</p>
                  <span className="text-[10px] text-emerald-400/70 block mt-1">Admissible covered expenses</span>
                </div>
                <div className="glass rounded-xl p-5 border border-rose-500/20 bg-rose-500/5">
                  <p className="text-xs text-rose-400 mb-1">Estimated Out-of-Pocket</p>
                  <p className="text-2xl font-bold text-rose-400">₹{Math.round(result.result.outOfPocket).toLocaleString()}</p>
                  <span className="text-[10px] text-rose-400/70 block mt-1">Deductibles, limits & co-pays</span>
                </div>
              </div>

              <div className="grid md:grid-cols-2 gap-6">
                {/* Cost breakdown */}
                <div className="glass rounded-2xl p-5 border border-white/5 space-y-3">
                  <h4 className="text-sm font-semibold flex items-center gap-2">
                    <Percent className="w-4 h-4 text-primary-light" /> Itemized Cost & Deductions Breakdown
                  </h4>
                  <div className="space-y-2 pt-2">
                    <div className="flex justify-between py-2 border-b border-border text-sm">
                      <span className="text-muted">Hospital Bill</span>
                      <span className="font-semibold text-foreground">₹{result.hospitalBill.toLocaleString()}</span>
                    </div>

                    {result.result.breakdown.deductible > 0 && (
                      <div className="flex justify-between py-2 border-b border-border text-sm">
                        <span className="text-muted">Standard Policy Deductible</span>
                        <span className="font-medium text-amber-400">-₹{Math.round(result.result.breakdown.deductible).toLocaleString()}</span>
                      </div>
                    )}

                    {result.result.breakdown.coPay > 0 && (
                      <div className="flex justify-between py-2 border-b border-border text-sm">
                        <span className="text-muted">Senior Citizen Co-Pay (20%)</span>
                        <span className="font-medium text-rose-400">-₹{Math.round(result.result.breakdown.coPay).toLocaleString()}</span>
                      </div>
                    )}

                    {result.result.breakdown.roomRentExcess > 0 && (
                      <div className="flex justify-between py-2 border-b border-border text-sm">
                        <span className="text-muted">Room Rent Proportional Capping</span>
                        <span className="font-medium text-rose-400">-₹{Math.round(result.result.breakdown.roomRentExcess).toLocaleString()}</span>
                      </div>
                    )}

                    {result.result.breakdown.subLimitDeduction > 0 && (
                      <div className="flex justify-between py-2 border-b border-border text-sm">
                        <span className="text-muted">Disease / Procedure Sub-Limit Capping</span>
                        <span className="font-medium text-rose-400">-₹{Math.round(result.result.breakdown.subLimitDeduction).toLocaleString()}</span>
                      </div>
                    )}

                    {result.result.breakdown.networkDiscount > 0 && (
                      <div className="flex justify-between py-2 border-b border-border text-sm">
                        <span className="text-muted">Network Hospital Tariff Discount</span>
                        <span className="font-medium text-cyan-400">Save ₹{Math.round(result.result.breakdown.networkDiscount).toLocaleString()}</span>
                      </div>
                    )}

                    <div className="flex justify-between py-3 border-t border-white/10 text-sm font-bold bg-white/5 px-3 rounded-xl mt-3">
                      <span className="text-emerald-400">Net Approved Payout</span>
                      <span className="text-emerald-400">₹{Math.round(result.result.coveredAmount).toLocaleString()}</span>
                    </div>
                  </div>
                </div>

                {/* Rejection risks */}
                <div className="glass rounded-2xl p-5 border border-red-500/20 space-y-3">
                  <h4 className="text-sm font-semibold flex items-center gap-2">
                    <AlertTriangle className="w-4 h-4 text-red-400" /> Potential Rejection & Deduction Risks
                  </h4>
                  <ul className="space-y-2 pt-2">
                    {result.result.rejectionReasons.map((r, i) => (
                      <li key={i} className="flex items-start gap-2.5 text-xs text-red-200/90 glass rounded-xl p-2.5 border border-red-500/10">
                        <XCircle className="w-4 h-4 text-red-400 mt-0.5 flex-shrink-0" />
                        <span>{r}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                {/* Required docs */}
                <div className="glass rounded-2xl p-5 border border-white/5 space-y-3">
                  <h4 className="text-sm font-semibold flex items-center gap-2">
                    <FileText className="w-4 h-4 text-primary-light" /> Mandatory Documents Checklist
                  </h4>
                  <ul className="space-y-2 pt-2">
                    {result.result.requiredDocuments.map((d, i) => (
                      <li key={i} className="flex items-center gap-2.5 text-xs text-muted glass rounded-xl p-2.5 border border-white/5">
                        <CheckCircle className={`w-4 h-4 flex-shrink-0 ${d.critical ? "text-emerald-400" : "text-emerald-400/50"}`} />
                        <span className={d.critical ? "font-medium text-foreground" : ""}>{d.name}</span>
                        {d.critical && (
                          <span className="ml-auto text-[10px] bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-2 py-0.5 rounded font-mono">
                            Mandatory
                          </span>
                        )}
                      </li>
                    ))}
                  </ul>
                </div>

                {/* ClaimCoach Tips */}
                <div className="glass rounded-2xl p-5 border border-emerald-500/20 space-y-3">
                  <h4 className="text-sm font-semibold flex items-center gap-2">
                    <HelpCircle className="w-4 h-4 text-emerald-400" /> ClaimCoach Actuarial Tips
                  </h4>
                  <ul className="space-y-2 pt-2">
                    {result.result.tips.map((t, i) => (
                      <li key={i} className="flex items-start gap-2.5 text-xs text-muted glass rounded-xl p-2.5 border border-emerald-500/10">
                        <ChevronRight className="w-4 h-4 text-emerald-400 mt-0.5 flex-shrink-0" />
                        <span>{t}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              {/* Similar claims actuarial benchmarks */}
              {result.result.similarClaims && (
                <div className="glass rounded-2xl p-5 border border-primary/20 bg-gradient-to-r from-primary/5 to-accent/5">
                  <div className="flex items-center gap-2 mb-3">
                    <TrendingUp className="w-4 h-4 text-primary-light" />
                    <h4 className="text-xs uppercase font-bold text-primary-light">Historical Actuarial Benchmarks</h4>
                  </div>
                  <div className="grid grid-cols-3 gap-3 text-center">
                    <div className="glass rounded-xl p-3">
                      <span className="text-[10px] text-muted block mb-0.5">Approved in Database</span>
                      <span className="text-lg font-bold text-emerald-400">{result.result.similarClaims.approved} Claims</span>
                    </div>
                    <div className="glass rounded-xl p-3">
                      <span className="text-[10px] text-muted block mb-0.5">Rejected / Disputed</span>
                      <span className="text-lg font-bold text-rose-400">{result.result.similarClaims.rejected} Claims</span>
                    </div>
                    <div className="glass rounded-xl p-3">
                      <span className="text-[10px] text-muted block mb-0.5">Average Approved Payout</span>
                      <span className="text-lg font-bold text-foreground">₹{result.result.similarClaims.avgPayout.toLocaleString()}</span>
                    </div>
                  </div>
                </div>
              )}
            </motion.div>
          )}
        </div>
      </main>
    </div>
  );
}
