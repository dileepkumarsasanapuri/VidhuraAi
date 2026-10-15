"use client";

import React from "react";
import { motion } from "framer-motion";
import {
  Shield, FileText, BarChart3, AlertTriangle, TrendingUp,
  Activity, Bell, Search, ChevronRight,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import StatCard from "@/components/StatCard";
import ScoreRing from "@/components/ScoreRing";
import RiskCard, { RiskItem } from "@/components/RiskCard";
import PolicyCard, { PolicyData } from "@/components/PolicyCard";
import Link from "next/link";
import { useAuth } from "@/lib/AuthContext";

import { api, Policy } from "@/lib/api";

export default function DashboardPage() {
  const { user } = useAuth();
  const [policies, setPolicies] = React.useState<any[]>([]);
  const [dynamicRisks, setDynamicRisks] = React.useState<RiskItem[]>([]);
  const [dynamicActivity, setDynamicActivity] = React.useState<any[]>([]);
  const [analytics, setAnalytics] = React.useState<any>(null);
  const userName = user?.name || "User";

  React.useEffect(() => {
    async function fetchData() {
      try {
        const [polRes, anaRes] = await Promise.all([
          api.getPolicies(),
          api.getAnalytics()
        ]);
        if (polRes.data) {
          setPolicies(polRes.data);
          
          // Generate risks dynamically
          const extractedRisks: RiskItem[] = [];
          const activities: any[] = [];
          
          polRes.data.forEach((p: any, pIdx: number) => {
            activities.push({
              action: "Policy uploaded",
              detail: p.name,
              time: p.createdAt ? new Date(p.createdAt).toLocaleDateString() : "Recently",
              icon: FileText
            });

            if (p.analysis && p.analysis.hiddenConditions) {
              p.analysis.hiddenConditions.slice(0, 2).forEach((cond: string, cIdx: number) => {
                extractedRisks.push({
                  id: `hc-${pIdx}-${cIdx}`,
                  title: `Hidden Condition in ${p.name.split(' ')[0]}`,
                  description: cond.replace('⚠️ ', ''),
                  severity: "high",
                  category: "Hidden Condition"
                });
              });
            }
            if (p.analysis && p.analysis.waitingPeriods) {
              p.analysis.waitingPeriods.slice(0, 1).forEach((wp: any, wIdx: number) => {
                extractedRisks.push({
                  id: `wp-${pIdx}-${wIdx}`,
                  title: `Waiting Period: ${wp.condition}`,
                  description: `${wp.duration}. ${wp.note}`,
                  severity: "medium",
                  category: "Waiting Period"
                });
              });
            }
          });
          setDynamicRisks(extractedRisks);
          setDynamicActivity(activities.reverse().slice(0, 4));
        }
        if (anaRes.data) {
          setAnalytics(anaRes.data);
        }
      } catch (e) {
        console.error("Failed to fetch dashboard data", e);
      }
    }
    fetchData();
  }, []);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard" />
      <main className="flex-1 overflow-y-auto">
        {/* Header */}
        <header className="sticky top-0 z-30 glass border-b border-border px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3 ml-11 lg:ml-0">
            <h1 className="text-base sm:text-lg font-bold">Dashboard</h1>
            <span className="text-xs text-muted hidden md:block">Insurance Intelligence Overview</span>
          </div>
          <div className="flex items-center gap-2 sm:gap-3">
            <div className="hidden md:flex items-center glass rounded-xl px-3 py-1.5 gap-2">
              <Search className="w-4 h-4 text-muted" />
              <input type="text" placeholder="Search policies..." className="bg-transparent text-sm outline-none w-40 placeholder:text-muted/50" />
            </div>
            <button className="relative p-2 glass rounded-xl hover:bg-white/5 transition-colors">
              <Bell className="w-4 h-4" />
              <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full" />
            </button>
          </div>
        </header>

        <div className="p-4 sm:p-6 space-y-5 sm:space-y-6 mobile-bottom-clearance">
          {/* Welcome */}
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
            className="glass rounded-2xl p-6 relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-r from-primary/5 to-accent/5 pointer-events-none" />
            <div className="relative z-10">
              <h2 className="text-xl font-bold mb-1">Welcome back, {userName} 👋</h2>
              <p className="text-sm text-muted mb-4">You have {policies.length} active polic{policies.length === 1 ? 'y' : 'ies'} with {dynamicRisks.length} alert{dynamicRisks.length === 1 ? '' : 's'} requiring attention.</p>
              <Link href="/dashboard/upload" className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-opacity">
                Upload New Policy <ChevronRight className="w-4 h-4" />
              </Link>
            </div>
          </motion.div>

          {/* Stats */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard title="Active Policies" value={policies.length.toString()} icon={FileText} color="primary" delay={0} />
            <StatCard
              title="Primary Coverage"
              value={policies.length > 0 && policies[0].coverage ? policies[0].coverage : "—"}
              icon={Shield}
              color="accent"
              delay={0.1}
            />
            <StatCard title="Hidden Risks Found" value={dynamicRisks.length.toString()} icon={AlertTriangle} color="danger" delay={0.2} />
            <StatCard
              title="Avg. Trust Score"
              value={policies.length > 0 ? `${analytics?.kpis?.trustScore?.value ?? 0}/100` : "—"}
              icon={BarChart3}
              color="warning"
              delay={0.3}
            />
          </div>

          {/* Main Grid */}
          <div className="grid lg:grid-cols-3 gap-6">
            {/* Policy Health Scores */}
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
              className="glass rounded-2xl p-6">
              <h3 className="text-base font-semibold mb-6">Policy Health Scores</h3>
              <div className="flex justify-around items-center min-h-[120px]">
                {policies.length > 0 ? (
                  policies.slice(0, 3).map((p, i) => (
                    <ScoreRing 
                      key={p.id} 
                      score={p.analysis?.scores?.overall || p.healthScore || 0} 
                      label={p.name.split(' ')[0]} 
                      color={i === 0 ? "#10b981" : i === 1 ? "#3b82f6" : "#f59e0b"} 
                      size={100} 
                    />
                  ))
                ) : (
                  <p className="text-sm text-muted">No policies analyzed yet.</p>
                )}
              </div>
              <div className="mt-6 glass rounded-xl p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted">Overall Protection Score</span>
                  <span className="text-sm font-bold gradient-text">{policies.length > 0 ? `${analytics?.policyHealth?.overall ?? 0}/100` : "0/100"}</span>
                </div>
                <div className="w-full h-2 rounded-full bg-white/5 overflow-hidden">
                  <motion.div initial={{ width: 0 }} animate={{ width: `${policies.length > 0 ? (analytics?.policyHealth?.overall ?? 0) : 0}%` }} transition={{ duration: 1.2, ease: "easeOut" }}
                    className="h-full rounded-full bg-gradient-to-r from-primary to-accent" />
                </div>
              </div>
            </motion.div>

            {/* Risk Alerts */}
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
              className="glass rounded-2xl p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-base font-semibold">Risk Alerts</h3>
                <span className={`text-xs px-2 py-0.5 rounded-full ${dynamicRisks.length > 0 ? 'bg-red-500/10 text-red-400' : 'bg-emerald-500/10 text-emerald-400'}`}>
                  {dynamicRisks.length} alerts
                </span>
              </div>
              <div className="space-y-3 max-h-[360px] overflow-y-auto pr-1">
                {dynamicRisks.length > 0 ? (
                  dynamicRisks.map((r, i) => <RiskCard key={r.id} risk={r} delay={i * 0.05} />)
                ) : (
                  <div className="text-center py-8">
                    <Shield className="w-8 h-8 text-emerald-400 mx-auto mb-2 opacity-50" />
                    <p className="text-sm text-muted">No hidden risks detected in your active policies.</p>
                  </div>
                )}
              </div>
            </motion.div>

            {/* Recent Activity */}
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}
              className="glass rounded-2xl p-6">
              <h3 className="text-base font-semibold mb-4">Recent Activity</h3>
              <div className="space-y-4">
                {dynamicActivity.length > 0 ? (
                  dynamicActivity.map((a, i) => (
                    <motion.div key={i} initial={{ opacity: 0, x: 10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.3 + i * 0.08 }}
                      className="flex items-start gap-3 group">
                      <div className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center flex-shrink-0 group-hover:bg-primary/10 transition-colors">
                        <a.icon className="w-4 h-4 text-muted group-hover:text-primary-light transition-colors" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium">{a.action}</p>
                        <p className="text-xs text-muted truncate">{a.detail}</p>
                      </div>
                      <span className="text-[10px] text-muted/60 flex-shrink-0">{a.time}</span>
                    </motion.div>
                  ))
                ) : (
                  <p className="text-xs text-muted">No recent activity found. Upload a policy to get started.</p>
                )}
              </div>
              <div className="mt-6 p-4 glass rounded-xl">
                <div className="flex items-center gap-2 mb-2">
                  <Activity className="w-4 h-4 text-primary-light" />
                  <span className="text-xs font-medium">Quick Scenario</span>
                </div>
                <p className="text-xs text-muted mb-3">Try asking: &quot;What if I get hospitalized?&quot;</p>
                <Link href="/dashboard/scenario" className="block text-center py-2 text-xs font-medium rounded-lg glass glass-hover">
                  Open ScenarioGPT →
                </Link>
              </div>
            </motion.div>
          </div>

          {/* Policies */}
          <div>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-semibold">Your Policies</h3>
              <Link href="/dashboard/policies" className="text-xs text-primary-light hover:underline">View All →</Link>
            </div>
            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
              {policies.length > 0 ? (
                policies.map((p, i) => <PolicyCard key={p.id} policy={p} delay={i * 0.1} />)
              ) : (
                <div className="col-span-full text-center py-8 glass rounded-2xl">
                  <FileText className="w-12 h-12 text-muted mx-auto mb-3 opacity-50" />
                  <p className="text-sm font-medium">No policies found</p>
                  <p className="text-xs text-muted mb-4">Upload your first insurance policy to unlock AI insights.</p>
                  <Link href="/dashboard/upload" className="inline-flex items-center gap-2 px-4 py-2 text-xs font-medium rounded-xl bg-primary text-white hover:opacity-90">
                    Upload Policy
                  </Link>
                </div>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
