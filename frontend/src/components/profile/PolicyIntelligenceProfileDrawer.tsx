"use client";

import React, { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Shield,
  FileText,
  AlertTriangle,
  CheckCircle,
  Building2,
  Activity,
  DollarSign,
  Clock,
  ExternalLink,
  ChevronRight,
  Hash,
  X,
  HelpCircle,
  Layers,
  Award,
  Sparkles,
  MapPin,
  Loader2,
  RefreshCw,
} from "lucide-react";
import {
  api,
  PolicyIntelligenceProfile,
} from "@/lib/api";

interface PolicyIntelligenceProfileDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  policyCode: string;
  demographics?: {
    age?: number;
    sumInsured?: number;
    city?: string;
    pinCode?: string;
    familyConstruct?: string;
  };
}

export default function PolicyIntelligenceProfileDrawer({
  isOpen,
  onClose,
  policyCode,
  demographics,
}: PolicyIntelligenceProfileDrawerProps) {
  const [profile, setProfile] = useState<PolicyIntelligenceProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<
    "contract" | "performance" | "hospitals" | "premium" | "claim" | "scorecard"
  >("contract");

  useEffect(() => {
    if (!isOpen || !policyCode) return;

    let isMounted = true;
    async function fetchProfile() {
      setLoading(true);
      setError(null);
      try {
        const res = await api.getPolicyIntelligenceProfile(policyCode, {
          age: demographics?.age || 35,
          sumInsured: demographics?.sumInsured || 1000000,
          city: demographics?.city,
          pinCode: demographics?.pinCode,
          familyConstruct: demographics?.familyConstruct || "INDIVIDUAL",
        });

        if (isMounted) {
          if (res.success && res.data) {
            setProfile(res.data);
          } else {
            setError(res.message || "Unable to load intelligence profile.");
          }
        }
      } catch (err: any) {
        if (isMounted) {
          setError(err.message || "Failed to fetch Policy Intelligence Profile.");
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    fetchProfile();
    return () => {
      isMounted = false;
    };
  }, [isOpen, policyCode, demographics]);

  // Handle ESC key to close
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && isOpen) {
        onClose();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex justify-end bg-black/70 backdrop-blur-sm">
        {/* Backdrop click to dismiss */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="absolute inset-0"
        />

        {/* Drawer Container */}
        <motion.div
          initial={{ x: "100%" }}
          animate={{ x: 0 }}
          exit={{ x: "100%" }}
          transition={{ type: "spring", damping: 25, stiffness: 200 }}
          className="relative w-full max-w-4xl h-full bg-slate-950/95 border-l border-border/80 shadow-2xl flex flex-col z-10 overflow-hidden"
          role="dialog"
          aria-modal="true"
          aria-labelledby="profile-drawer-title"
        >
          {/* Header */}
          <div className="p-6 border-b border-border/60 bg-gradient-to-r from-slate-900/90 to-indigo-950/40 flex-shrink-0">
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-1.5">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="px-2.5 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                    Policy Intelligence Profile
                  </span>
                  {profile?.contractOverview.syntheticFixture ? (
                    <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-amber-500/10 text-amber-300 border border-amber-500/20">
                      Synthetic Demo Fixture
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-500/10 text-emerald-300 border border-emerald-500/20 flex items-center gap-1">
                      <CheckCircle className="w-3 h-3" />
                      Verified Statutory Filing
                    </span>
                  )}
                  <span className="text-xs text-muted-foreground font-mono">
                    UIN: {profile?.contractOverview.irdaUin || "Pending UIN"}
                    {profile?.contractOverview.uinRevision ? ` (${profile.contractOverview.uinRevision})` : ""}
                  </span>
                </div>
                <h2 id="profile-drawer-title" className="text-xl sm:text-2xl font-bold tracking-tight text-foreground">
                  {profile?.contractOverview.policyName || policyCode}
                </h2>
                <p className="text-xs text-muted-foreground">
                  {profile?.contractOverview.provider} • {profile?.contractOverview.category || "Comprehensive Health Insurance"}
                </p>
              </div>

              <div className="flex items-center gap-3">
                {profile?.evidenceQualityScorecard && (
                  <div className="hidden sm:flex flex-col items-end">
                    <span className="text-[10px] uppercase font-bold text-muted-foreground">Evidence Quality</span>
                    <span className="text-lg font-black text-emerald-400">
                      {Math.round(profile.evidenceQualityScorecard.overallQualityScore)}%
                    </span>
                  </div>
                )}
                <button
                  onClick={onClose}
                  className="p-2 rounded-xl hover:bg-white/10 text-muted-foreground hover:text-white transition-colors"
                  aria-label="Close Profile Drawer"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            {/* Navigation Tabs */}
            <div className="flex items-center gap-1 mt-6 overflow-x-auto pb-1 scrollbar-none border-b border-white/5">
              {[
                { id: "contract", label: "Contract Terms", icon: FileText },
                { id: "performance", label: "Insurer Performance", icon: Award },
                { id: "hospitals", label: "Hospital Network", icon: Building2 },
                { id: "premium", label: "Premium Quote", icon: DollarSign },
                { id: "claim", label: "Claim Simulation", icon: Activity },
                { id: "scorecard", label: "Evidence Quality", icon: Shield },
              ].map((tab) => {
                const Icon = tab.icon;
                const isActive = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id as any)}
                    className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold whitespace-nowrap transition-all ${
                      isActive
                        ? "bg-indigo-600/30 text-indigo-300 border border-indigo-500/40 shadow-sm"
                        : "text-muted-foreground hover:text-white hover:bg-white/5"
                    }`}
                  >
                    <Icon className="w-3.5 h-3.5" />
                    <span>{tab.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Drawer Body */}
          <div className="flex-1 overflow-y-auto p-6 space-y-6">
            {loading ? (
              <div className="flex flex-col items-center justify-center h-64 space-y-3">
                <Loader2 className="w-8 h-8 animate-spin text-indigo-400" />
                <p className="text-sm text-muted-foreground">
                  Aggregating contract ground truth, statutory disclosures & claim simulation...
                </p>
              </div>
            ) : error ? (
              <div className="p-4 rounded-xl border border-red-500/20 bg-red-500/10 text-red-300 text-sm space-y-2">
                <div className="flex items-center gap-2 font-bold">
                  <AlertTriangle className="w-4 h-4" />
                  <span>Failed to load Policy Intelligence Profile</span>
                </div>
                <p className="text-xs text-red-300/80">{error}</p>
              </div>
            ) : profile ? (
              <>
                {/* TAB 1: CONTRACT TERMS */}
                {activeTab === "contract" && (
                  <div className="space-y-6">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {/* Room Rent */}
                      <div className="p-4 rounded-xl border border-border/60 bg-card/60 space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                            Room Rent Ceiling
                          </span>
                          {profile.contractOverview.roomRentCapped ? (
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30">
                              CAPPED (1% SI)
                            </span>
                          ) : (
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                              NO ROOM RENT CAP
                            </span>
                          )}
                        </div>
                        <p className="text-sm text-foreground font-semibold">
                          {profile.contractOverview.roomRentLimitDescription}
                        </p>
                        {profile.contractOverview.proportionateDeductionApplies && (
                          <div className="text-xs text-amber-400/90 flex items-start gap-1.5 pt-1">
                            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                            <span>
                              Proportionate deduction penalty applies to doctor fees, OT charges, and nursing when room category exceeds ceiling.
                            </span>
                          </div>
                        )}
                      </div>

                      {/* Co-Payment */}
                      <div className="p-4 rounded-xl border border-border/60 bg-card/60 space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                            Co-Payment Terms
                          </span>
                          {profile.contractOverview.copayPercentage > 0 ? (
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-500/20 text-purple-300">
                              {profile.contractOverview.copayPercentage}% COPAY
                            </span>
                          ) : (
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-300">
                              ZERO COPAY
                            </span>
                          )}
                        </div>
                        <p className="text-sm text-foreground font-semibold">
                          {profile.contractOverview.copayDescription}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          Deductible: ₹{profile.contractOverview.deductibleAmount.toLocaleString("en-IN")}
                        </p>
                      </div>

                      {/* Waiting Periods */}
                      <div className="p-4 rounded-xl border border-border/60 bg-card/60 space-y-2">
                        <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                          Waiting Periods
                        </span>
                        <div className="grid grid-cols-3 gap-2 pt-1 text-center">
                          <div className="p-2 rounded-lg bg-white/5">
                            <span className="text-xs text-muted-foreground block">Initial</span>
                            <span className="text-sm font-bold text-foreground">
                              {profile.contractOverview.waitingPeriodInitialDays} Days
                            </span>
                          </div>
                          <div className="p-2 rounded-lg bg-white/5">
                            <span className="text-xs text-muted-foreground block">Specific</span>
                            <span className="text-sm font-bold text-foreground">
                              {profile.contractOverview.waitingPeriodSpecificIllnessMonths} Months
                            </span>
                          </div>
                          <div className="p-2 rounded-lg bg-white/5">
                            <span className="text-xs text-muted-foreground block">PED</span>
                            <span className="text-sm font-bold text-foreground">
                              {profile.contractOverview.waitingPeriodPedMonths} Months
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Benefits & Restoration */}
                      <div className="p-4 rounded-xl border border-border/60 bg-card/60 space-y-2">
                        <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                          Benefits & Restoration
                        </span>
                        <div className="space-y-1 text-xs text-muted-foreground">
                          <div className="flex items-center justify-between">
                            <span>Day-Care Treatments:</span>
                            <span className="font-semibold text-foreground">
                              {profile.contractOverview.dayCareTreatmentsCovered ? "Covered" : "Check list"}
                            </span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span>Sum Insured Restoration:</span>
                            <span className="font-semibold text-emerald-400">
                              {profile.contractOverview.restorationBenefit ? "100% Unlimited" : "Standard"}
                            </span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span>Cumulative Bonus:</span>
                            <span className="font-semibold text-foreground">
                              {profile.contractOverview.cumulativeBonusDescription}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Official Document Citation */}
                    <div className="p-4 rounded-xl border border-indigo-500/20 bg-indigo-500/5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs">
                      <div>
                        <span className="text-indigo-300 font-bold block">Official Policy Wording on File:</span>
                        <span className="text-muted-foreground font-mono">
                          {profile.contractOverview.policyWordingDocument || "Policy_Wordings.pdf"}
                        </span>
                        <div className="text-[10px] text-muted-foreground mt-0.5">
                          Source: {profile.contractOverview.regulatoryFilingSource}
                        </div>
                      </div>
                      {profile.contractOverview.officialDocumentUrl && (
                        <a
                          href={profile.contractOverview.officialDocumentUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="px-3 py-1.5 rounded-lg bg-indigo-600/20 hover:bg-indigo-600/30 text-indigo-300 border border-indigo-500/30 font-semibold flex items-center gap-1.5"
                        >
                          <ExternalLink className="w-3.5 h-3.5" />
                          <span>View Official Filing</span>
                        </a>
                      )}
                    </div>
                  </div>
                )}

                {/* TAB 2: INSURER PERFORMANCE */}
                {activeTab === "performance" && (
                  <div className="space-y-6">
                    {profile.insurerPerformance && profile.insurerPerformance.verifiedStatutory ? (
                      <div className="space-y-4">
                        <div className="p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/5 flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <CheckCircle className="w-5 h-5 text-emerald-400" />
                            <div>
                              <span className="text-sm font-bold text-foreground">
                                Verified Statutory Performance Metrics
                              </span>
                              <div className="text-xs text-muted-foreground">
                                Sourced from {profile.insurerPerformance.statutorySourceTitle} ({profile.insurerPerformance.reportingPeriod})
                              </div>
                            </div>
                          </div>
                          <span className="px-2.5 py-1 rounded-lg text-xs font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                            {profile.insurerPerformance.overallPerformanceRating}
                          </span>
                        </div>

                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                          <div className="p-3 rounded-xl border border-border bg-card/40 space-y-1">
                            <span className="text-[11px] text-muted-foreground block">Incurred Claim Ratio (ICR)</span>
                            <span className="text-lg font-bold text-foreground">
                              {profile.insurerPerformance.incurredClaimRatio || "N/A"}
                            </span>
                            <span className="text-[10px] text-emerald-400 block">Healthy: 65% - 85%</span>
                          </div>

                          <div className="p-3 rounded-xl border border-border bg-card/40 space-y-1">
                            <span className="text-[11px] text-muted-foreground block">Settled in &lt; 3 Months</span>
                            <span className="text-lg font-bold text-foreground">
                              {profile.insurerPerformance.settlementRatioLessThan3Months || "N/A"}
                            </span>
                            <span className="text-[10px] text-emerald-400 block">Statutory Disclosure</span>
                          </div>

                          <div className="p-3 rounded-xl border border-border bg-card/40 space-y-1">
                            <span className="text-[11px] text-muted-foreground block">Annual Settlement Ratio</span>
                            <span className="text-lg font-bold text-foreground">
                              {profile.insurerPerformance.annualSettlementRatio || "N/A"}
                            </span>
                            <span className="text-[10px] text-muted-foreground block">Full FY Settlement</span>
                          </div>

                          <div className="p-3 rounded-xl border border-border bg-card/40 space-y-1">
                            <span className="text-[11px] text-muted-foreground block">Repudiation Ratio</span>
                            <span className="text-lg font-bold text-foreground">
                              {profile.insurerPerformance.repudiationRatio || "N/A"}
                            </span>
                            <span className="text-[10px] text-amber-400 block">Rejected Claims</span>
                          </div>

                          <div className="p-3 rounded-xl border border-border bg-card/40 space-y-1">
                            <span className="text-[11px] text-muted-foreground block">Solvency Ratio</span>
                            <span className="text-lg font-bold text-foreground">
                              {profile.insurerPerformance.solvencyRatio || "N/A"}
                            </span>
                            <span className="text-[10px] text-emerald-400 block">Min Required: 1.50</span>
                          </div>

                          <div className="p-3 rounded-xl border border-border bg-card/40 space-y-1">
                            <span className="text-[11px] text-muted-foreground block">Claim Turnaround</span>
                            <span className="text-lg font-bold text-foreground">
                              {profile.insurerPerformance.claimTurnaroundDays || "N/A"}
                            </span>
                            <span className="text-[10px] text-muted-foreground block">Avg Settlement Days</span>
                          </div>
                        </div>

                        {profile.insurerPerformance.statutoryDocumentHash && (
                          <div className="p-3 rounded-xl border border-white/5 bg-white/5 text-[11px] font-mono text-muted-foreground break-all">
                            <span className="text-xs font-sans font-bold text-foreground block mb-0.5">
                              Cryptographic Statutory Filing SHA-256:
                            </span>
                            {profile.insurerPerformance.statutoryDocumentHash}
                          </div>
                        )}
                      </div>
                    ) : (
                      <div className="p-6 rounded-2xl border border-amber-500/20 bg-amber-500/5 text-center space-y-3">
                        <AlertTriangle className="w-8 h-8 text-amber-400 mx-auto" />
                        <h4 className="text-base font-bold text-foreground">Statutory Filing Under Verification</h4>
                        <p className="text-xs text-muted-foreground max-w-md mx-auto">
                          External public disclosure metrics (IRDAI Form NL-25 and Annual Report Tables) for this insurer are pending compliance review. Vidhura AI strictly avoids hallucinating numbers.
                        </p>
                        <span className="inline-block px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-300 border border-amber-500/20">
                          STATUS: UNVERIFIED_PENDING_DISCLOSURE
                        </span>
                      </div>
                    )}
                  </div>
                )}

                {/* TAB 3: HOSPITAL NETWORK */}
                {activeTab === "hospitals" && (
                  <div className="space-y-4">
                    <div className="p-4 rounded-xl border border-border bg-card/50 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                      <div>
                        <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                          Cashless Accessibility Score
                        </span>
                        <div className="text-2xl font-black text-indigo-400">
                          {profile.hospitalAccessibility.cashlessAccessibilityScore}/100
                        </div>
                        <div className="text-xs text-muted-foreground mt-0.5">
                          {profile.hospitalAccessibility.totalNetworkHospitalsInCity} verified hospitals in{" "}
                          <strong>{profile.hospitalAccessibility.city || "Selected Area"}</strong> (
                          {profile.hospitalAccessibility.preferredTier1Count} Preferred Tier 1)
                        </div>
                      </div>

                      <span className="px-3 py-1 rounded-full text-xs font-bold bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
                        {profile.hospitalAccessibility.verificationBadge}
                      </span>
                    </div>

                    {profile.hospitalAccessibility.hospitals && profile.hospitalAccessibility.hospitals.length > 0 ? (
                      <div className="space-y-2.5">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                          Verified Local Network Facilities (ROHINI Registry)
                        </h4>
                        <div className="space-y-2">
                          {profile.hospitalAccessibility.hospitals.map((hosp) => (
                            <div
                              key={hosp.hospitalId}
                              className="p-3 rounded-xl border border-border/60 bg-card/30 flex items-center justify-between text-xs"
                            >
                              <div className="space-y-0.5">
                                <div className="font-bold text-foreground">{hosp.hospitalName}</div>
                                <div className="text-muted-foreground text-[11px]">
                                  ROHINI ID: {hosp.registryRohiniCode} • {hosp.city} (PIN: {hosp.pinCode})
                                </div>
                              </div>
                              <div className="flex items-center gap-2">
                                <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                                  hosp.tier === "TIER_1_PREFERRED" || hosp.tier === "GIPSA_PPN"
                                    ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                                    : "bg-slate-500/20 text-slate-300"
                                }`}>
                                  {hosp.tier}
                                </span>
                                <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                                  hosp.cashlessActive
                                    ? "bg-emerald-500/20 text-emerald-300"
                                    : "bg-rose-500/20 text-rose-300"
                                }`}>
                                  {hosp.cashlessActive ? "CASHLESS ACTIVE" : "CASHLESS INACTIVE"}
                                </span>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : (
                      <div className="p-6 rounded-2xl border border-white/5 bg-white/5 text-center text-xs text-muted-foreground space-y-2">
                        <MapPin className="w-6 h-6 text-muted-foreground mx-auto" />
                        <p>No regional cashless agreements ingested for this exact city/pincode filter yet.</p>
                        <p className="text-[11px] text-muted-foreground/70">
                          Cashless Everywhere permits reimbursement across any registered medical provider.
                        </p>
                      </div>
                    )}
                  </div>
                )}

                {/* TAB 4: PREMIUM QUOTE */}
                {activeTab === "premium" && (
                  <div className="space-y-4">
                    <div className="p-6 rounded-2xl border border-indigo-500/30 bg-gradient-to-br from-indigo-950/40 to-slate-900 p-6 space-y-4">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold uppercase tracking-wider text-indigo-300">
                          Payable Annual Premium
                        </span>
                        {profile.premiumQuotation.isAuthentic ? (
                          <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center gap-1">
                            <CheckCircle className="w-3.5 h-3.5" />
                            VERIFIED AUTHENTIC QUOTE
                          </span>
                        ) : (
                          <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30 flex items-center gap-1">
                            <AlertTriangle className="w-3.5 h-3.5" />
                            ESTIMATED ACTUARIAL MODEL
                          </span>
                        )}
                      </div>

                      <div className="text-3xl sm:text-4xl font-black text-foreground tracking-tight">
                        {profile.premiumQuotation.formattedAnnualPremium}
                        <span className="text-sm font-normal text-muted-foreground ml-2">/ year</span>
                      </div>

                      {/* GST Split */}
                      <div className="grid grid-cols-2 gap-4 pt-3 border-t border-white/10 text-xs">
                        <div>
                          <span className="text-muted-foreground block">Net Base Premium:</span>
                          <span className="font-bold text-foreground">
                            ₹{profile.premiumQuotation.quotedBasePremium?.toLocaleString("en-IN") || "—"}
                          </span>
                        </div>
                        <div>
                          <span className="text-muted-foreground block">GST (18% Statutory Tax):</span>
                          <span className="font-bold text-foreground">
                            ₹{profile.premiumQuotation.quotedGst?.toLocaleString("en-IN") || "—"}
                          </span>
                        </div>
                      </div>

                      <div className="pt-2 text-[11px] text-muted-foreground space-y-1">
                        <div>Channel: <strong>{profile.premiumQuotation.quotingChannel}</strong></div>
                        {profile.premiumQuotation.quoteReferenceNumber && (
                          <div>Quotation Ref: <strong className="font-mono">{profile.premiumQuotation.quoteReferenceNumber}</strong></div>
                        )}
                        {profile.premiumQuotation.isStale && (
                          <div className="text-amber-400 font-semibold">
                            ⚠️ This authentic quote has expired. Displaying updated non-binding estimate.
                          </div>
                        )}
                      </div>
                    </div>

                    {!profile.premiumQuotation.isAuthentic && (
                      <div className="p-4 rounded-xl border border-amber-500/20 bg-amber-500/5 text-xs text-amber-300/90 leading-relaxed">
                        <strong>Actuarial Estimation Notice:</strong> The rate above is computed from calibrated age-band grids and IRDAI geographic zones. Actual final premiums are subject to medical tele-underwriting, pre-existing disease loading, tobacco usage, and underwriter sign-off.
                      </div>
                    )}
                  </div>
                )}

                {/* TAB 5: CLAIM SIMULATION */}
                {activeTab === "claim" && (
                  <div className="space-y-4">
                    <div className="p-4 rounded-xl border border-border bg-card/40 space-y-3">
                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                            Standard Statutory Hospitalization Benchmark
                          </span>
                          <h4 className="text-sm font-bold text-foreground">
                            {profile.benchmarkClaimSimulation.diagnosis}
                          </h4>
                        </div>
                        <span className="px-2.5 py-1 rounded-lg text-xs font-bold bg-white/5 border border-white/10 text-foreground font-mono">
                          Bill: ₹{profile.benchmarkClaimSimulation.totalSubmittedBill.toLocaleString("en-IN")}
                        </span>
                      </div>

                      {/* Payment vs Liability Waterfall */}
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
                        <div className="p-3.5 rounded-xl border border-emerald-500/30 bg-emerald-500/10 space-y-1">
                          <span className="text-xs text-emerald-400 font-bold block">Estimated Insurer Payment</span>
                          <span className="text-2xl font-black text-emerald-300">
                            ₹{profile.benchmarkClaimSimulation.estimatedInsurerPayment.toLocaleString("en-IN")}
                          </span>
                          <span className="text-[10px] text-muted-foreground block">Cashless approved amount</span>
                        </div>

                        <div className="p-3.5 rounded-xl border border-amber-500/30 bg-amber-500/10 space-y-1">
                          <span className="text-xs text-amber-400 font-bold block">Estimated Patient Liability</span>
                          <span className="text-2xl font-black text-amber-300">
                            ₹{profile.benchmarkClaimSimulation.estimatedPatientLiability.toLocaleString("en-IN")}
                          </span>
                          <span className="text-[10px] text-muted-foreground block">Out-of-pocket payable by you</span>
                        </div>
                      </div>

                      <div className="p-3 rounded-lg bg-black/30 border border-white/5 text-xs text-muted-foreground leading-relaxed">
                        {profile.benchmarkClaimSimulation.contractualExplanation}
                      </div>

                      {/* Line Item Breakdown */}
                      <div className="space-y-1.5 pt-2 text-xs">
                        <span className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground block">
                          Contractual Deductions Waterfall
                        </span>
                        <div className="space-y-1">
                          <div className="flex items-center justify-between p-2 rounded bg-white/5">
                            <span>Room Rent Excess Deduction:</span>
                            <span className="font-bold text-amber-400">
                              ₹{profile.benchmarkClaimSimulation.roomRentExcessDeduction.toLocaleString("en-IN")}
                            </span>
                          </div>
                          <div className="flex items-center justify-between p-2 rounded bg-white/5">
                            <span>Proportionate Deduction Penalty:</span>
                            <span className="font-bold text-amber-400">
                              ₹{profile.benchmarkClaimSimulation.proportionateDeduction.toLocaleString("en-IN")}
                            </span>
                          </div>
                          <div className="flex items-center justify-between p-2 rounded bg-white/5">
                            <span>Non-Medical Consumables (IRDAI List 1):</span>
                            <span className="font-bold text-amber-400">
                              ₹{profile.benchmarkClaimSimulation.nonMedicalConsumablesDeduction.toLocaleString("en-IN")}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* TAB 6: EVIDENCE QUALITY SCORECARD */}
                {activeTab === "scorecard" && (
                  <div className="space-y-5">
                    <div className="p-4 rounded-xl border border-border bg-card/60 flex items-center justify-between">
                      <div className="space-y-1">
                        <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                          Composite Evidence Audit Score
                        </span>
                        <div className="text-2xl font-black text-emerald-400">
                          {Math.round(profile.evidenceQualityScorecard.overallQualityScore)} / 100
                        </div>
                        <div className="text-xs text-muted-foreground">
                          Audited against IRDAI official product registers & regulatory disclosures.
                        </div>
                      </div>
                      <span className="px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/10 text-emerald-300 border border-emerald-500/20">
                        {profile.evidenceQualityScorecard.contractualGroundTruthStatus}
                      </span>
                    </div>

                    {/* Verified Evidence List */}
                    <div className="space-y-2">
                      <h4 className="text-xs font-bold uppercase tracking-wider text-emerald-400 flex items-center gap-1.5">
                        <CheckCircle className="w-3.5 h-3.5" />
                        <span>Verified Ground-Truth Evidence</span>
                      </h4>
                      <div className="space-y-1.5">
                        {profile.evidenceQualityScorecard.verifiedDisclosures.map((v, i) => (
                          <div key={i} className="p-2.5 rounded-lg border border-emerald-500/20 bg-emerald-500/5 text-xs text-emerald-300/90 flex items-start gap-2">
                            <CheckCircle className="w-3.5 h-3.5 text-emerald-400 mt-0.5 flex-shrink-0" />
                            <span>{v}</span>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Explicit Missing Disclosures (Zero Hallucination) */}
                    {profile.evidenceQualityScorecard.pendingOrMissingDisclosures.length > 0 && (
                      <div className="space-y-2">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-amber-400 flex items-center gap-1.5">
                          <AlertTriangle className="w-3.5 h-3.5" />
                          <span>Explicit Limitations & Pending Disclosures</span>
                        </h4>
                        <div className="space-y-1.5">
                          {profile.evidenceQualityScorecard.pendingOrMissingDisclosures.map((p, i) => (
                            <div key={i} className="p-2.5 rounded-lg border border-amber-500/20 bg-amber-500/5 text-xs text-amber-300/90 flex items-start gap-2">
                              <AlertTriangle className="w-3.5 h-3.5 text-amber-400 mt-0.5 flex-shrink-0" />
                              <span>{p}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </>
            ) : null}
          </div>

          {/* Footer */}
          <div className="p-4 border-t border-border/60 bg-slate-900/60 flex items-center justify-between text-xs text-muted-foreground flex-shrink-0">
            <span>Verified Ground Truth • Vidhura AI Policy Engine</span>
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-xl bg-card border border-border hover:bg-card/80 text-foreground font-semibold transition-colors"
            >
              Done
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
