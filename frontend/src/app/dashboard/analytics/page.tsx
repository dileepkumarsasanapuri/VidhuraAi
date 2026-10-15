"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  BarChart3, Shield, AlertTriangle, FileText, Activity,
  TrendingDown, Upload, Loader2, Sparkles
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import ScoreRing from "@/components/ScoreRing";
import StatCard from "@/components/StatCard";
import { api, AnalyticsData } from "@/lib/api";

const colorMap: Record<string, string> = {
  Health: "bg-indigo-500",
  Life: "bg-cyan-500",
  Motor: "bg-emerald-500",
  Travel: "bg-amber-500",
  Other: "bg-purple-500",
};

export default function AnalyticsPage() {
  const [data, setData] = useState<AnalyticsData | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function fetchAnalytics() {
      try {
        const response = await api.getAnalytics();
        if (response.data) {
          setData(response.data);
        }
      } catch (e) {
        console.error("Failed to fetch analytics", e);
      } finally {
        setIsLoading(false);
      }
    }
    fetchAnalytics();
  }, []);

  const totalPolicies = (data as any)?.totalPolicies ?? 0;
  const hasRealPolicies = totalPolicies > 0;

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/analytics" />
      <main className="flex-1 overflow-y-auto">
        <header className="sticky top-0 z-30 glass border-b border-border px-6 h-16 flex items-center justify-between">
          <div className="ml-12 lg:ml-0">
            <h1 className="text-lg font-bold">Analytics & Health</h1>
            <p className="text-xs text-muted">Real-time protection insights derived from your active policies</p>
          </div>
          {hasRealPolicies && (
            <span className="text-xs text-muted bg-white/5 px-3 py-1.5 rounded-xl">
              {totalPolicies} active polic{totalPolicies === 1 ? 'y' : 'ies'} analyzed
            </span>
          )}
        </header>

        <div className="p-6 space-y-6 max-w-6xl mx-auto">
          {isLoading ? (
            <div className="flex items-center justify-center py-24">
              <Loader2 className="w-8 h-8 text-primary animate-spin" />
            </div>
          ) : !data || !hasRealPolicies ? (
            <div className="max-w-md mx-auto text-center py-16 px-6 glass rounded-2xl border border-white/5">
              <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center border border-primary/20">
                <BarChart3 className="w-8 h-8 text-primary-light" />
              </div>
              <h3 className="text-lg font-bold mb-2">No analytics available</h3>
              <p className="text-sm text-muted mb-6 leading-relaxed">
                You haven&apos;t uploaded any insurance policies yet. Upload your genuine policy PDFs to generate real-time protection scores, coverage adequacy breakdowns, and risk distributions.
              </p>
              <Link
                href="/dashboard/upload"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-all glow-primary"
              >
                <Upload className="w-4 h-4" /> Upload Policy to View Analytics
              </Link>
            </div>
          ) : (
            <>
              {/* KPIs */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <StatCard
                  title="Overall Trust Score"
                  value={`${data.kpis?.trustScore?.value || 0}/100`}
                  icon={Shield}
                  color="primary"
                  delay={0}
                />
                <StatCard
                  title="Claim Preparedness"
                  value={`${data.kpis?.claimPreparedness?.value || 0}%`}
                  icon={Activity}
                  color="success"
                  delay={0.1}
                />
                <StatCard
                  title="Hidden Risks / Conditions"
                  value={(data.kpis?.hiddenRisks?.value ?? 0).toString()}
                  icon={AlertTriangle}
                  color="danger"
                  delay={0.2}
                />
                <StatCard
                  title="Total Analyzed"
                  value={`${totalPolicies} ${totalPolicies === 1 ? 'Policy' : 'Policies'}`}
                  icon={FileText}
                  color="warning"
                  delay={0.3}
                />
              </div>

              <div className="grid lg:grid-cols-3 gap-6">
                {/* Policy Health Overview */}
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.2 }}
                  className="glass rounded-2xl p-6"
                >
                  <h3 className="text-base font-semibold mb-6">Policy Portfolio Health</h3>
                  <div className="flex justify-center mb-6">
                    <ScoreRing
                      score={data.policyHealth?.overall || 0}
                      label="Overall Score"
                      size={140}
                      strokeWidth={10}
                    />
                  </div>
                  <div className="space-y-3">
                    {[
                      { label: "Transparency", score: data.policyHealth?.transparency || 0, color: "bg-indigo-500" },
                      { label: "Risk Protection", score: data.policyHealth?.riskProtection || 0, color: "bg-cyan-500" },
                      { label: "Claim Readiness", score: data.policyHealth?.claimReadiness || 0, color: "bg-emerald-500" },
                      { label: "Value / Fairness", score: data.policyHealth?.valueForMoney || 0, color: "bg-amber-500" },
                    ].map((item) => (
                      <div key={item.label}>
                        <div className="flex justify-between text-xs mb-1">
                          <span className="text-muted">{item.label}</span>
                          <span className="font-medium">{item.score}%</span>
                        </div>
                        <div className="w-full h-1.5 rounded-full bg-white/5 overflow-hidden">
                          <motion.div
                            initial={{ width: 0 }}
                            animate={{ width: `${item.score}%` }}
                            transition={{ duration: 1, delay: 0.4 }}
                            className={`h-full rounded-full ${item.color}`}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                </motion.div>

                {/* Risk Distribution */}
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.3 }}
                  className="lg:col-span-2 glass rounded-2xl p-6 flex flex-col justify-between"
                >
                  <div>
                    <h3 className="text-base font-semibold mb-2">Detected Risk Distribution</h3>
                    <p className="text-xs text-muted mb-4">
                      Breakdown of hidden conditions, strict exclusions, and waiting periods in your documents.
                    </p>
                    {data.riskDistribution && data.riskDistribution.length > 0 ? (
                      <div className="grid sm:grid-cols-2 gap-3 mt-4">
                        {data.riskDistribution.map((r, i) => (
                          <div key={i} className="glass rounded-xl p-4 border border-white/5 flex items-center justify-between">
                            <div>
                              <p className="text-sm font-medium">{r.category}</p>
                              <span
                                className={`text-[10px] uppercase font-bold px-2 py-0.5 rounded-full ${
                                  r.severity === "High"
                                    ? "bg-red-500/20 text-red-400"
                                    : r.severity === "Medium"
                                    ? "bg-amber-500/20 text-amber-400"
                                    : "bg-blue-500/20 text-blue-400"
                                }`}
                              >
                                {r.severity} Severity
                              </span>
                            </div>
                            <span className="text-2xl font-bold text-foreground">{r.count}</span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="py-12 text-center text-muted text-xs">
                        <Shield className="w-8 h-8 text-emerald-400 mx-auto mb-2 opacity-60" />
                        No critical risk flags or hidden conditions detected in your active policies.
                      </div>
                    )}
                  </div>

                  <div className="mt-6 p-4 rounded-xl bg-white/5 text-xs text-muted">
                    💡 Tip: Review individual policy details to explore full clause breakdowns and waiting period tables.
                  </div>
                </motion.div>
              </div>

              {/* Coverage Breakdown */}
              {data.coverageBreakdown && data.coverageBreakdown.length > 0 && (
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.4 }}
                  className="glass rounded-2xl p-6"
                >
                  <h3 className="text-base font-semibold mb-2">Coverage Portfolio by Category</h3>
                  <p className="text-xs text-muted mb-6">Active policies categorized with primary sum insured and adequacy score.</p>
                  <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
                    {data.coverageBreakdown.map((c, i) => (
                      <div key={i} className="glass rounded-xl p-4">
                        <div className="flex items-center justify-between mb-3">
                          <span className="text-sm font-medium">{c.type}</span>
                          <span className="text-xs text-primary-light font-bold">{c.amount}</span>
                        </div>
                        <div className="w-full h-2 rounded-full bg-white/5 overflow-hidden mb-2">
                          <motion.div
                            initial={{ width: 0 }}
                            animate={{ width: `${Math.min(c.percentage, 100)}%` }}
                            transition={{ duration: 1, delay: 0.5 + i * 0.1 }}
                            className={`h-full rounded-full ${colorMap[c.type] || "bg-primary"}`}
                          />
                        </div>
                        <div className="flex justify-between text-[10px] text-muted">
                          <span>Health Score</span>
                          <span>{c.percentage}%</span>
                        </div>
                      </div>
                    ))}
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
