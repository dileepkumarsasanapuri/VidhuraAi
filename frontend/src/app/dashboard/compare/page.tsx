"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  GitCompare,
  Shield,
  AlertTriangle,
  CheckCircle,
  Star,
  Upload,
  Loader2,
  Check,
  X,
  FileText,
  Activity,
  Award,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import ScoreRing from "@/components/ScoreRing";
import { api, ComparisonPolicy } from "@/lib/api";

export default function ComparePage() {
  const [availablePolicies, setAvailablePolicies] = useState<{ id: string; name: string }[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [compared, setCompared] = useState<ComparisonPolicy[]>([]);
  const [recommendation, setRecommendation] = useState<{ bestPolicy: string; reason: string; savings: string } | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isComparing, setIsComparing] = useState(false);

  useEffect(() => {
    async function loadPolicies() {
      try {
        const response = await api.getPolicies();
        if (response.data) {
          const policies = response.data.map((p) => ({ id: p.id, name: p.name }));
          setAvailablePolicies(policies);
          // Default select up to three policies
          if (policies.length > 0) {
            setSelectedIds(policies.slice(0, 3).map((p) => p.id));
          }
        }
      } catch (error) {
        console.error("Failed to load policies", error);
      } finally {
        setIsLoading(false);
      }
    }
    loadPolicies();
  }, []);

  useEffect(() => {
    async function fetchComparison() {
      if (selectedIds.length === 0) {
        setCompared([]);
        setRecommendation(null);
        return;
      }
      setIsComparing(true);
      try {
        const response = await api.comparePolicies(selectedIds);
        if (response.data) {
          setCompared(response.data.policies || []);
          setRecommendation(response.data.recommendation);
        }
      } catch (error) {
        console.error("Failed to fetch comparison", error);
      } finally {
        setIsComparing(false);
      }
    }
    fetchComparison();
  }, [selectedIds]);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/compare" />
      <main className="flex-1 overflow-y-auto">
        <header className="sticky top-0 z-30 glass border-b border-border px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="ml-11 lg:ml-0">
            <h1 className="text-base sm:text-lg font-bold">CompareWise</h1>
            <p className="text-xs text-muted hidden sm:block">Intelligent policy comparison — beyond premiums & marketing</p>
          </div>
          {availablePolicies.length > 0 && (
            <span className="text-xs text-muted bg-white/5 px-2.5 sm:px-3 py-1 rounded-xl">
              {selectedIds.length} of {availablePolicies.length} selected
            </span>
          )}
        </header>

        <div className="p-4 sm:p-6 max-w-6xl mx-auto space-y-5 sm:space-y-6 mobile-bottom-clearance">
          {isLoading ? (
            <div className="flex items-center justify-center py-24">
              <Loader2 className="w-8 h-8 text-primary animate-spin" />
            </div>
          ) : availablePolicies.length === 0 ? (
            <div className="max-w-md mx-auto text-center py-16 px-6 glass rounded-2xl border border-white/5">
              <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center border border-primary/20">
                <GitCompare className="w-8 h-8 text-primary-light" />
              </div>
              <h3 className="text-lg font-bold mb-2">No policies to compare</h3>
              <p className="text-sm text-muted mb-6 leading-relaxed">
                Upload your actual insurance policies to compare transparency, coverage, fine-print traps, and claim reliability side-by-side.
              </p>
              <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
                <Link
                  href="/dashboard/upload"
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-all glow-primary"
                >
                  <Upload className="w-4 h-4" /> Upload Policies
                </Link>
                <button
                  onClick={() => {
                    setCompared([
                      {
                        id: "demo-hdfc",
                        name: "HDFC ERGO Optima Secure",
                        provider: "HDFC ERGO General Insurance Company Limited",
                        premium: "₹14,500/yr",
                        coverage: "₹10 Lakhs",
                        scores: { transparency: 88, risk: 22, fairness: 87, claimReliability: 86 },
                        pros: ["100% Instant Restoration Benefit", "No Room Rent Sub-Limit Capping", "Pre & Post 60/180 Days Covered"],
                        cons: ["Strict 24h Emergency Claim Intimation", "Non-Payable Consumables Deducted"],
                        exclusions: 8,
                        hiddenTraps: 2,
                        roomRentLimit: "Single Standard Private AC Room (No sub-limit)",
                        roomRentCapped: false,
                        copay: "0% (Nil / No Co-payment across any age)",
                        copayPercentage: 0,
                        deductible: "₹0 (Nil / No Base Deductible)",
                        deductibleAmount: 0,
                        diseaseSubLimits: "None (Full Sum Insured available for all procedures)",
                        preHospitalizationDays: 60,
                        postHospitalizationDays: 180,
                        restorationBenefit: "Unlimited Instant Restore",
                        noClaimBonus: "100% Cumulative Bonus",
                      },
                      {
                        id: "demo-star",
                        name: "Star Health Comprehensive",
                        provider: "Star Health & Allied Insurance",
                        premium: "₹16,200/yr",
                        coverage: "₹10 Lakhs",
                        scores: { transparency: 81, risk: 32, fairness: 82, claimReliability: 79 },
                        pros: ["Comprehensive In-patient Hospitalization", "AYUSH Treatment Included", "Health Checkup Benefit"],
                        cons: ["Cataract Capped at ₹40,000/eye", "Deluxe Room Triggers Proportional Capping"],
                        exclusions: 11,
                        hiddenTraps: 4,
                        roomRentLimit: "Single Standard AC Room (Deluxe triggers proportional penalty)",
                        roomRentCapped: true,
                        copay: "0% for <60 yrs; 20% co-payment for 61+ yrs entry age",
                        copayPercentage: 20,
                        deductible: "₹0 (Nil / No Base Deductible)",
                        deductibleAmount: 0,
                        diseaseSubLimits: "Cataract capped at ₹40,000 per eye",
                        preHospitalizationDays: 60,
                        postHospitalizationDays: 90,
                        restorationBenefit: "100% Automatic Restoration once/year",
                        noClaimBonus: "Up to 50% NCB",
                      },
                      {
                        id: "demo-care",
                        name: "Care Supreme Health Plan",
                        provider: "Care Health Insurance Limited",
                        premium: "₹13,800/yr",
                        coverage: "₹10 Lakhs",
                        scores: { transparency: 84, risk: 26, fairness: 85, claimReliability: 82 },
                        pros: ["Unlimited Automatic Recharge", "Cumulative Bonus up to 500%", "Day Care Surgeries Covered"],
                        cons: ["Mandatory 20% Co-Pay for Senior Citizens", "36-Month PED Waiting Period"],
                        exclusions: 9,
                        hiddenTraps: 3,
                        roomRentLimit: "Any Room Category without capping",
                        roomRentCapped: false,
                        copay: "0% (Nil Co-payment)",
                        copayPercentage: 0,
                        deductible: "₹0 (Nil / No Base Deductible)",
                        deductibleAmount: 0,
                        diseaseSubLimits: "None (Full Sum Insured available)",
                        preHospitalizationDays: 60,
                        postHospitalizationDays: 180,
                        restorationBenefit: "Unlimited Automatic Recharge",
                        noClaimBonus: "Cumulative Bonus up to 500%",
                      },
                    ]);
                    setRecommendation({
                      bestPolicy: "HDFC ERGO Optima Secure",
                      reason: "Leads with the highest transparency (88/100) and claim reliability (86/100) along with 2X secure coverage benefit and zero room rent sub-limits.",
                      savings: "Opt for the single private room category to prevent proportional surgeon and OT deductions.",
                    });
                  }}
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium glass glass-hover text-muted hover:text-white transition-all border border-white/10"
                >
                  <Star className="w-4 h-4 text-amber-400" /> Explore Benchmark Comparison
                </button>
              </div>
            </div>
          ) : (
            <>
              {/* Policy selector pills */}
              <div className="glass rounded-2xl p-5 border border-white/5 space-y-2">
                <p className="text-xs text-muted font-medium">Select up to 3 policies to compare side-by-side:</p>
                <div className="flex flex-wrap gap-2.5">
                  {availablePolicies.map((p) => {
                    const isSelected = selectedIds.includes(p.id);
                    return (
                      <button
                        key={p.id}
                        onClick={() => {
                          if (isSelected) {
                            if (selectedIds.length > 1) {
                              setSelectedIds(selectedIds.filter((x) => x !== p.id));
                            }
                          } else {
                            setSelectedIds([...selectedIds, p.id].slice(-3));
                          }
                        }}
                        className={`px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                          isSelected
                            ? "bg-primary/20 text-primary-light border border-primary/40 shadow-sm shadow-primary/10"
                            : "glass glass-hover text-muted hover:text-foreground"
                        }`}
                      >
                        {isSelected && <span className="mr-1.5">✓</span>}
                        {p.name}
                      </button>
                    );
                  })}
                </div>
              </div>

              {isComparing && (
                <div className="flex items-center justify-center py-4 text-xs text-muted gap-2">
                  <Loader2 className="w-4 h-4 animate-spin text-primary-light" /> Analyzing comparison matrices...
                </div>
              )}

              {/* AI Recommendation Banner */}
              {recommendation && recommendation.bestPolicy && (
                <motion.div
                  initial={{ opacity: 0, y: 15 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="glass rounded-2xl p-5 border border-primary/30 bg-gradient-to-r from-primary/10 via-accent/5 to-transparent flex flex-col md:flex-row items-start md:items-center justify-between gap-4"
                >
                  <div className="flex items-start gap-3">
                    <div className="w-10 h-10 rounded-xl bg-amber-500/20 text-amber-400 flex items-center justify-center flex-shrink-0 mt-0.5 border border-amber-500/30">
                      <Award className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
                        Comparative Actuarial Recommendation:{" "}
                        <span className="text-primary-light font-extrabold">{recommendation.bestPolicy}</span>
                      </h3>
                      <p className="text-xs text-muted mt-1 leading-relaxed">{recommendation.reason}</p>
                      {recommendation.savings && (
                        <p className="text-xs text-primary-light mt-2 font-medium">💡 {recommendation.savings}</p>
                      )}
                    </div>
                  </div>
                </motion.div>
              )}

              {/* Comparison Cards Grid */}
              {compared.length > 0 && (
                <div
                  className="grid gap-6"
                  style={{
                    gridTemplateColumns: `repeat(${Math.min(compared.length, 3)}, minmax(0, 1fr))`,
                  }}
                >
                  {compared.map((p, i) => (
                    <motion.div
                      key={p.id}
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: i * 0.1 }}
                      className="glass rounded-2xl p-6 space-y-6 flex flex-col justify-between border border-white/5"
                    >
                      {/* Header */}
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <Shield className="w-5 h-5 text-primary-light flex-shrink-0" />
                          <h3 className="text-base font-bold truncate">{p.name}</h3>
                        </div>
                        <p className="text-xs text-muted truncate">{p.provider}</p>
                        <div className="flex items-baseline gap-2 mt-3 pt-3 border-t border-white/5">
                          <span className="text-xl font-extrabold text-foreground">{p.premium}</span>
                          <span className="text-xs text-primary-light font-semibold">• {p.coverage}</span>
                        </div>
                      </div>

                      {/* Score Rings */}
                      <div className="grid grid-cols-2 gap-4">
                        <ScoreRing score={p.scores?.transparency || 0} label="Transparency" size={80} strokeWidth={6} />
                        <ScoreRing score={p.scores?.claimReliability || 0} label="Claim Reliability" size={80} strokeWidth={6} />
                        <ScoreRing score={100 - (p.scores?.risk || 0)} label="Protection / Safety" size={80} strokeWidth={6} />
                        <ScoreRing score={p.scores?.fairness || 0} label="Fairness" size={80} strokeWidth={6} />
                      </div>

                      {/* Risk metrics badges */}
                      <div className="space-y-2 glass rounded-xl p-3 border border-white/5">
                        <div className="flex justify-between items-center text-xs">
                          <span className="text-muted">Strict Exclusions</span>
                          <span
                            className={`font-semibold ${
                              p.exclusions > 10 ? "text-red-400" : p.exclusions > 5 ? "text-amber-400" : "text-emerald-400"
                            }`}
                          >
                            {p.exclusions} found
                          </span>
                        </div>
                        <div className="flex justify-between items-center text-xs">
                          <span className="text-muted">Hidden Traps / Alerts</span>
                          <span
                            className={`font-semibold ${
                              p.hiddenTraps > 3 ? "text-red-400" : p.hiddenTraps > 1 ? "text-amber-400" : "text-emerald-400"
                            }`}
                          >
                            {p.hiddenTraps} detected
                          </span>
                        </div>
                      </div>

                      {/* Pros / Advantages */}
                      <div>
                        <h4 className="text-xs font-semibold text-emerald-400 mb-2 flex items-center gap-1.5">
                          <CheckCircle className="w-3.5 h-3.5" /> Key Advantages
                        </h4>
                        <ul className="space-y-1.5 min-h-[60px]">
                          {p.pros && p.pros.length > 0 ? (
                            p.pros.map((pro, pIdx) => (
                              <li key={pIdx} className="text-xs text-muted flex items-start gap-2">
                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 mt-1.5 flex-shrink-0" />
                                <span>{pro}</span>
                              </li>
                            ))
                          ) : (
                            <li className="text-xs text-muted/60 italic">Standard comprehensive terms</li>
                          )}
                        </ul>
                      </div>

                      {/* Cons / Traps */}
                      <div>
                        <h4 className="text-xs font-semibold text-red-400 mb-2 flex items-center gap-1.5">
                          <AlertTriangle className="w-3.5 h-3.5" /> Fine-Print Caveats
                        </h4>
                        <ul className="space-y-1.5 min-h-[60px]">
                          {p.cons && p.cons.length > 0 ? (
                            p.cons.map((con, cIdx) => (
                              <li key={cIdx} className="text-xs text-muted flex items-start gap-2">
                                <span className="w-1.5 h-1.5 rounded-full bg-red-400 mt-1.5 flex-shrink-0" />
                                <span>{con}</span>
                              </li>
                            ))
                          ) : (
                            <li className="text-xs text-muted/60 italic">No critical caveats flagged</li>
                          )}
                        </ul>
                      </div>
                    </motion.div>
                  ))}
                </div>
              )}

              {/* Side-by-side Feature Matrix Table */}
              {compared.length > 1 && (
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="glass rounded-2xl p-6 border border-white/5 space-y-4"
                >
                  <h3 className="text-base font-bold flex items-center gap-2">
                    <FileText className="w-4 h-4 text-primary-light" /> Feature-by-Feature Actuarial Matrix
                  </h3>

                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="border-b border-white/10 text-muted">
                          <th className="py-3 px-4 font-semibold">Benefit / Policy Term</th>
                          {compared.map((p) => (
                            <th key={p.id} className="py-3 px-4 font-bold text-foreground">
                              {p.name}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-white/5">
                        <tr>
                          <td className="py-3 px-4 text-muted font-medium">Room Rent Entitlement</td>
                          {compared.map((p) => (
                            <td key={p.id} className="py-3 px-4 text-foreground font-semibold">
                              {p.roomRentLimit || "Single Standard Private AC Room"}
                            </td>
                          ))}
                        </tr>
                        <tr>
                          <td className="py-3 px-4 text-muted font-medium">Co-Payment Clause</td>
                          {compared.map((p) => (
                            <td key={p.id} className="py-3 px-4 font-semibold text-foreground">
                              {p.copay || "0% (Nil / No Co-payment required across any age)"}
                            </td>
                          ))}
                        </tr>
                        <tr>
                          <td className="py-3 px-4 text-muted font-medium">Base Deductible</td>
                          {compared.map((p) => (
                            <td key={p.id} className="py-3 px-4 text-emerald-400 font-semibold">
                              {p.deductible || "₹0 (Nil / No Base Deductible)"}
                            </td>
                          ))}
                        </tr>
                        <tr>
                          <td className="py-3 px-4 text-muted font-medium">Disease Sub-Limits</td>
                          {compared.map((p) => (
                            <td key={p.id} className="py-3 px-4 text-foreground font-medium">
                              {p.diseaseSubLimits || "None (Full Sum Insured available for all procedures)"}
                            </td>
                          ))}
                        </tr>
                        <tr>
                          <td className="py-3 px-4 text-muted font-medium">Pre & Post Hospitalization</td>
                          {compared.map((p) => (
                            <td key={p.id} className="py-3 px-4 text-emerald-400 font-medium">
                              {p.preHospitalizationDays ?? 60} Days Pre / {p.postHospitalizationDays ?? 180} Days Post
                            </td>
                          ))}
                        </tr>
                        <tr>
                          <td className="py-3 px-4 text-muted font-medium">Sum Insured Restoration</td>
                          {compared.map((p) => (
                            <td key={p.id} className="py-3 px-4 text-emerald-400 font-medium">
                              {p.restorationBenefit || "100% Instant Recharge"}
                            </td>
                          ))}
                        </tr>
                        <tr>
                          <td className="py-3 px-4 text-muted font-medium">Cumulative Bonus (NCB)</td>
                          {compared.map((p) => (
                            <td key={p.id} className="py-3 px-4 text-foreground font-medium">
                              {p.noClaimBonus || "Cumulative Bonus as per contract terms"}
                            </td>
                          ))}
                        </tr>
                        <tr>
                          <td className="py-3 px-4 text-muted font-medium">Documented Exclusions</td>
                          {compared.map((p) => (
                            <td key={p.id} className="py-3 px-4 text-amber-400 font-medium">
                              {p.exclusions || 0} Excluded Categories Extracted
                            </td>
                          ))}
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </motion.div>
              )}
            </>
          )}
        </div>
      </main>
    </div>
  );
}
