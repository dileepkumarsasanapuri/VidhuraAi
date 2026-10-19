"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  HeartPulse,
  Shield,
  AlertTriangle,
  CheckCircle,
  Star,
  ArrowRight,
  ArrowLeft,
  RefreshCw,
  Info,
  HelpCircle,
  FileText,
  Check,
  X,
  Sparkles,
  Building2,
  User,
  Users,
  ChevronRight,
  ChevronDown,
  Filter,
  Sliders,
  ExternalLink,
  Download,
  Clock,
  DollarSign,
  Activity,
  Percent,
  BookOpen,
  Search,
  Zap,
  Layers,
  BarChart2,
  History,
  Award,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import ScoreRing from "@/components/ScoreRing";
import {
  api,
  RecommendationRequest,
  RecommendationResponse,
  RankedPolicyCard,
  MemberInput,
  GuidanceResponse,
  RecommendationHistoryItem,
} from "@/lib/api";

// Sample Quick Start Presets for Instant 1-Click Evaluation
const PRESETS = [
  {
    name: "Young Professional (Age 28)",
    desc: "Single individual in metro city seeking high coverage with zero room-rent capping.",
    data: {
      primaryAge: 28,
      insuranceType: "INDIVIDUAL",
      members: [],
      cityOrPincode: "Bengaluru",
      annualBudget: 15000,
      budgetType: "PREFERRED" as const,
      desiredSumInsured: 1000000,
      helpMeDecideSumInsured: false,
      primaryPriority: "BALANCED" as const,
      preExistingConditions: [],
      maternityBenefitNeeded: false,
      opdBenefitNeeded: false,
      copayTolerance: "NONE",
      hasExistingInsurance: false,
    },
  },
  {
    name: "Family with Child (Ages 34, 32, 4)",
    desc: "2 adults + 1 toddler in Mumbai needing robust hospitalization protection & instant restoration.",
    data: {
      primaryAge: 34,
      insuranceType: "FAMILY_WITH_CHILDREN",
      members: [
        { relation: "SPOUSE", displayName: "Spouse", age: 32, preExistingConditions: [] },
        { relation: "CHILD", displayName: "Child", age: 4, preExistingConditions: [] },
      ],
      cityOrPincode: "Mumbai",
      annualBudget: 32000,
      budgetType: "PREFERRED" as const,
      desiredSumInsured: 2500000,
      helpMeDecideSumInsured: false,
      primaryPriority: "BROADER_COVERAGE" as const,
      preExistingConditions: [],
      maternityBenefitNeeded: false,
      opdBenefitNeeded: false,
      copayTolerance: "NONE",
      hasExistingInsurance: true,
      existingSumInsured: 500000,
      portingPolicy: true,
    },
  },
  {
    name: "Senior Parents (Ages 62, 58)",
    desc: "Parents in Delhi NCR with declared hypertension & diabetes, looking for low co-pay.",
    data: {
      primaryAge: 62,
      insuranceType: "PARENTS",
      members: [
        { relation: "SPOUSE", displayName: "Mother", age: 58, preExistingConditions: ["HYPERTENSION"] },
      ],
      cityOrPincode: "Delhi",
      annualBudget: 60000,
      budgetType: "PREFERRED" as const,
      desiredSumInsured: 1000000,
      helpMeDecideSumInsured: false,
      primaryPriority: "LOWER_OUT_OF_POCKET" as const,
      preExistingConditions: ["HYPERTENSION", "DIABETES"],
      maternityBenefitNeeded: false,
      opdBenefitNeeded: false,
      copayTolerance: "LOW",
      hasExistingInsurance: false,
    },
  },
];

const COMMON_CONDITIONS = [
  "Diabetes (Type 2)",
  "Hypertension (High BP)",
  "Asthma / Respiratory",
  "Thyroid Disorder",
  "Cardiovascular / Heart Disease",
  "Kidney Disease",
  "None / Healthy",
];

export default function RecommendPage() {
  // Navigation / Mode state: "questionnaire" | "loading" | "results"
  const [viewState, setViewState] = useState<"questionnaire" | "loading" | "results">("questionnaire");
  const [currentStep, setCurrentStep] = useState<number>(1); // 1: Who, 2: Coverage/Budget, 3: Priorities & Health, 4: Porting

  // Form State
  const [primaryAge, setPrimaryAge] = useState<number>(32);
  const [insuranceType, setInsuranceType] = useState<string>("INDIVIDUAL");
  const [members, setMembers] = useState<MemberInput[]>([]);
  const [cityOrPincode, setCityOrPincode] = useState<string>("Mumbai");
  const [annualBudget, setAnnualBudget] = useState<number>(25000);
  const [budgetType, setBudgetType] = useState<"STRICT" | "PREFERRED">("PREFERRED");
  const [desiredSumInsured, setDesiredSumInsured] = useState<number>(1000000);
  const [helpMeDecideSumInsured, setHelpMeDecideSumInsured] = useState<boolean>(false);
  const [primaryPriority, setPrimaryPriority] = useState<"AFFORDABLE_PREMIUMS" | "BROADER_COVERAGE" | "LOWER_OUT_OF_POCKET" | "BALANCED">("BALANCED");

  // Step B fields
  const [preExistingConditions, setPreExistingConditions] = useState<string[]>([]);
  const [maternityBenefitNeeded, setMaternityBenefitNeeded] = useState<boolean>(false);
  const [opdBenefitNeeded, setOpdBenefitNeeded] = useState<boolean>(false);
  const [copayTolerance, setCopayTolerance] = useState<string>("NONE");
  const [hasExistingInsurance, setHasExistingInsurance] = useState<boolean>(false);
  const [existingSumInsured, setExistingSumInsured] = useState<number>(500000);
  const [portingPolicy, setPortingPolicy] = useState<boolean>(false);

  // Dynamic AI guidance state
  const [guidance, setGuidance] = useState<GuidanceResponse | null>(null);
  const [isGuidanceLoading, setIsGuidanceLoading] = useState<boolean>(false);

  // Recommendation output state
  const [recommendationResponse, setRecommendationResponse] = useState<RecommendationResponse | null>(null);
  const [selectedPolicyDetail, setSelectedPolicyDetail] = useState<RankedPolicyCard | null>(null);
  const [activeResultsTab, setActiveResultsTab] = useState<"q1_policies" | "q2_limitations" | "q3_evidence" | "metrics">("q1_policies");

  // Provenance & Audit modal state
  const [auditingPolicy, setAuditingPolicy] = useState<RankedPolicyCard | null>(null);
  const [showProvenanceModal, setShowProvenanceModal] = useState<boolean>(false);

  // Comparison drawer state
  const [comparingPolicyIds, setComparingPolicyIds] = useState<string[]>([]);
  const [showComparisonModal, setShowComparisonModal] = useState<boolean>(false);

  // History state
  const [historyItems, setHistoryItems] = useState<RecommendationHistoryItem[]>([]);
  const [showHistoryModal, setShowHistoryModal] = useState<boolean>(false);
  const [isLoadingHistory, setIsLoadingHistory] = useState<boolean>(false);

  // Filter/Sort state in Results
  const [sortBy, setSortBy] = useState<"match" | "premium_asc" | "oop_safety" | "settlement">("match");
  const [evidenceFilter, setEvidenceFilter] = useState<string>("ALL");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Handle preset application
  const applyPreset = (preset: typeof PRESETS[0]) => {
    setPrimaryAge(preset.data.primaryAge);
    setInsuranceType(preset.data.insuranceType);
    setMembers(preset.data.members);
    setCityOrPincode(preset.data.cityOrPincode);
    setAnnualBudget(preset.data.annualBudget);
    setBudgetType(preset.data.budgetType);
    setDesiredSumInsured(preset.data.desiredSumInsured);
    setHelpMeDecideSumInsured(preset.data.helpMeDecideSumInsured);
    setPrimaryPriority(preset.data.primaryPriority);
    setPreExistingConditions(preset.data.preExistingConditions);
    setMaternityBenefitNeeded(preset.data.maternityBenefitNeeded);
    setOpdBenefitNeeded(preset.data.opdBenefitNeeded);
    setCopayTolerance(preset.data.copayTolerance);
    setHasExistingInsurance(preset.data.hasExistingInsurance);
    if (preset.data.existingSumInsured) setExistingSumInsured(preset.data.existingSumInsured);
    if (preset.data.portingPolicy) setPortingPolicy(preset.data.portingPolicy);
  };

  // Fetch guidance whenever basic parameters change
  useEffect(() => {
    let isCancelled = false;

    async function fetchAdvisorGuidance() {
      try {
        setIsGuidanceLoading(true);
        const res = await api.getRecommendationGuidance({
          primaryAge,
          insuranceType,
          memberCount: 1 + members.length,
          cityOrPincode,
          annualBudget,
        });
        if (!isCancelled && res.data) {
          setGuidance(res.data);
          if (helpMeDecideSumInsured && res.data.recommendedSumInsured) {
            setDesiredSumInsured(res.data.recommendedSumInsured);
          }
        }
      } catch (e) {
        // Soft fallback
      } finally {
        if (!isCancelled) {
          setIsGuidanceLoading(false);
        }
      }
    }

    if (cityOrPincode && cityOrPincode.trim().length >= 3) {
      fetchAdvisorGuidance();
    }

    return () => {
      isCancelled = true;
    };
  }, [primaryAge, insuranceType, members.length, cityOrPincode, annualBudget, helpMeDecideSumInsured]);

  // Adjust member list template when insuranceType changes
  const handleInsuranceTypeChange = (type: string) => {
    setInsuranceType(type);
    if (type === "INDIVIDUAL") {
      setMembers([]);
    } else if (type === "SELF_AND_SPOUSE") {
      setMembers([{ relation: "SPOUSE", displayName: "Spouse", age: Math.max(18, primaryAge - 2), preExistingConditions: [] }]);
    } else if (type === "FAMILY_WITH_CHILDREN") {
      setMembers([
        { relation: "SPOUSE", displayName: "Spouse", age: Math.max(18, primaryAge - 2), preExistingConditions: [] },
        { relation: "CHILD", displayName: "Child 1", age: 5, preExistingConditions: [] },
      ]);
    } else if (type === "PARENTS") {
      setMembers([
        { relation: "SPOUSE", displayName: "Parent 2 / Mother", age: 58, preExistingConditions: [] },
      ]);
      if (primaryAge < 50) setPrimaryAge(60);
    }
  };

  const addMember = (relation: string) => {
    setMembers([
      ...members,
      { relation, displayName: relation === "CHILD" ? `Child ${members.filter((m) => m.relation === "CHILD").length + 1}` : relation, age: relation === "CHILD" ? 6 : 40, preExistingConditions: [] },
    ]);
  };

  const removeMember = (index: number) => {
    setMembers(members.filter((_, i) => i !== index));
  };

  const updateMemberAge = (index: number, age: number) => {
    const updated = [...members];
    updated[index].age = age;
    setMembers(updated);
  };

  const toggleCondition = (condition: string) => {
    if (condition === "None / Healthy") {
      setPreExistingConditions([]);
      return;
    }
    const cleanName = condition.split(" ")[0].toUpperCase();
    if (preExistingConditions.includes(cleanName)) {
      setPreExistingConditions(preExistingConditions.filter((c) => c !== cleanName));
    } else {
      setPreExistingConditions([...preExistingConditions.filter((c) => c !== "NONE"), cleanName]);
    }
  };

  // Run Recommendation Request
  const handleSubmitQuestionnaire = async () => {
    setErrorMsg(null);
    setViewState("loading");

    const payload: RecommendationRequest = {
      primaryAge: Number(primaryAge),
      insuranceType,
      members,
      cityOrPincode,
      annualBudget: Number(annualBudget),
      budgetType,
      desiredSumInsured: helpMeDecideSumInsured ? null : desiredSumInsured,
      helpMeDecideSumInsured,
      primaryPriority,
      preExistingConditions,
      maternityBenefitNeeded,
      opdBenefitNeeded,
      copayTolerance,
      hasExistingInsurance,
      existingSumInsured: hasExistingInsurance ? existingSumInsured : null,
      portingPolicy: hasExistingInsurance ? portingPolicy : false,
    };

    try {
      const response = await api.generateRecommendations(payload);
      if (response && response.data) {
        setRecommendationResponse(response.data);
        if (response.data.rankedPolicies && response.data.rankedPolicies.length > 0) {
          setSelectedPolicyDetail(response.data.rankedPolicies[0]);
          // Default compare top 2
          setComparingPolicyIds(response.data.rankedPolicies.slice(0, 2).map((p) => p.policyId));
        }
        setViewState("results");
      } else {
        setErrorMsg("Failed to generate recommendations. Please check your inputs.");
        setViewState("questionnaire");
      }
    } catch (err: any) {
      console.error("Recommendation error:", err);
      setErrorMsg(err.message || "An unexpected error occurred while scoring policies.");
      setViewState("questionnaire");
    }
  };

  // Load Past History
  const openHistory = async () => {
    setShowHistoryModal(true);
    setIsLoadingHistory(true);
    try {
      const res = await api.getRecommendationHistory();
      if (res && res.data) {
        setHistoryItems(res.data);
      }
    } catch (e) {
      console.error("Could not fetch history:", e);
    } finally {
      setIsLoadingHistory(false);
    }
  };

  const loadHistoricalResult = (item: RecommendationHistoryItem) => {
    if (item.rankedPolicies && item.rankedPolicies.length > 0) {
      const topPolicy = item.rankedPolicies[0];
      const inputSnap = topPolicy?.reproducibilitySnapshot?.inputRequirementsSnapshot;
      const req = item.userRequirements;

      const age = req?.primaryAge ?? inputSnap?.primaryAge ?? 30;
      const insType = req?.insuranceType ?? inputSnap?.insuranceType ?? "INDIVIDUAL";
      const totalMembers = req?.members ? (1 + req.members.length) : (inputSnap?.totalInsuredMembers ?? 1);
      const city = req?.cityOrPincode ?? "Saved Location";
      const zone = req?.detectedZone ?? inputSnap?.pricingZone ?? item.guidance?.zoneClassification ?? "Zone 1";
      const budget = req?.annualBudget ?? inputSnap?.annualBudget ?? 25000;
      const budgetType = req?.budgetType ?? inputSnap?.budgetType ?? "PREFERRED";
      const priority = req?.primaryPriority ?? topPolicy?.reproducibilitySnapshot?.scoringBreakdown?.primaryPriority ?? "BALANCED";
      const conditions = req?.preExistingConditions ?? [];
      const sumInsured = topPolicy?.sumInsured ?? item.guidance?.recommendedSumInsured ?? 1000000;
      const formattedSI = topPolicy?.formattedSumInsured ?? item.guidance?.formattedRecommendedSumInsured ?? "₹10 Lakh";

      setRecommendationResponse({
        recommendationId: item.id,
        profileId: item.profileId,
        generatedAt: item.createdAt,
        profileSummary: {
          primaryAge: age,
          insuranceType: insType,
          totalInsuredMembers: totalMembers,
          cityOrPincode: city,
          pricingZone: zone,
          annualBudget: budget,
          formattedBudget: `₹${budget.toLocaleString("en-IN")}/yr (${budgetType})`,
          budgetType: budgetType,
          selectedSumInsured: sumInsured,
          formattedSumInsured: formattedSI,
          primaryPriorityLabel: priority,
          declaredConditions: conditions,
        },
        guidance: item.guidance,
        rankedPolicies: item.rankedPolicies,
        totalPoliciesEvaluated: item.rankedPolicies.length,
        eligiblePoliciesCount: item.rankedPolicies.length,
      });
      setSelectedPolicyDetail(item.rankedPolicies[0]);
      setShowHistoryModal(false);
      setViewState("results");
    }
  };

  // Sorting ranked policies
  const sortedPolicies = recommendationResponse?.rankedPolicies
    ? [...recommendationResponse.rankedPolicies].sort((a, b) => {
        if (sortBy === "match") return b.overallMatchScore - a.overallMatchScore;
        if (sortBy === "premium_asc") return a.calculatedAnnualPremium - b.calculatedAnnualPremium;
        if (sortBy === "oop_safety") return b.outOfPocketProtectionScore - a.outOfPocketProtectionScore;
        if (sortBy === "settlement") {
          return (b.insurerMetrics?.claimSettlementRatio || 0) - (a.insurerMetrics?.claimSettlementRatio || 0);
        }
        return 0;
      })
    : [];

  // Toggle compare selection
  const toggleCompare = (policyId: string) => {
    if (comparingPolicyIds.includes(policyId)) {
      setComparingPolicyIds(comparingPolicyIds.filter((id) => id !== policyId));
    } else {
      if (comparingPolicyIds.length >= 3) {
        setComparingPolicyIds([...comparingPolicyIds.slice(1), policyId]);
      } else {
        setComparingPolicyIds([...comparingPolicyIds, policyId]);
      }
    }
  };

  return (
    <div className="flex h-screen bg-background overflow-hidden text-foreground">
      <Sidebar activeTab="recommend" />

      <main className="flex-1 overflow-y-auto min-h-screen pb-16">
        {/* Top Header */}
        <header className="sticky top-0 z-30 border-b border-border bg-background/80 backdrop-blur-md px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-500/20 to-teal-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
              <HeartPulse className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold tracking-tight">Health Insurance Match & Audit</h1>
                <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  Version 2 Engine
                </span>
              </div>
              <p className="text-xs text-muted-foreground">
                3-Question Intelligence: Best Policies • Limitation & Out-of-Pocket Traps • Official Contract Evidence
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={openHistory}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-border bg-card/50 hover:bg-card text-xs font-medium transition-colors"
            >
              <History className="w-4 h-4 text-muted-foreground" />
              <span>Past Runs</span>
            </button>

            {viewState === "results" && (
              <button
                onClick={() => setViewState("questionnaire")}
                className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-primary/10 hover:bg-primary/20 text-primary border border-primary/20 text-xs font-semibold transition-colors"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Tweak Requirements</span>
              </button>
            )}
          </div>
        </header>

        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
          {errorMsg && (
            <div className="mb-6 p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-sm flex items-center gap-3">
              <AlertTriangle className="w-5 h-5 flex-shrink-0" />
              <p>{errorMsg}</p>
            </div>
          )}

          {/* ========================================================================= */}
          {/* VIEW 1: PROGRESSIVE QUESTIONNAIRE WIZARD */}
          {/* ========================================================================= */}
          {viewState === "questionnaire" && (
            <div className="space-y-8 animate-in fade-in duration-300">
              {/* Hero Banner with Quick Presets */}
              <div className="glass rounded-2xl p-6 border border-border relative overflow-hidden bg-gradient-to-r from-card via-card/80 to-emerald-950/20">
                <div className="max-w-2xl">
                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 mb-3">
                    <Sparkles className="w-3 h-3" />
                    Zero-Sales Guarantee • Objective Contract Analysis
                  </span>
                  <h2 className="text-2xl font-bold mb-2">Find Your Ideal Health Policy Without Sales Bias</h2>
                  <p className="text-sm text-muted-foreground leading-relaxed">
                    Most insurance platforms push plans with the highest broker commissions. Vidhura AI uses deterministic IRDAI contract clause scoring, room-rent proportionate deduction detection, and official policy wordings to protect your family from out-of-pocket hospital surprises.
                  </p>
                </div>

                {/* Instant 1-Click Presets */}
                <div className="mt-5 pt-4 border-t border-border/60">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                      ⚡ Quick Start with Verified Profiles:
                    </span>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                    {PRESETS.map((preset, idx) => (
                      <button
                        key={idx}
                        onClick={() => applyPreset(preset)}
                        className="text-left p-3 rounded-xl border border-border bg-background/50 hover:border-emerald-500/40 hover:bg-emerald-500/5 transition-all group"
                      >
                        <div className="font-semibold text-xs text-foreground group-hover:text-emerald-400 transition-colors">
                          {preset.name}
                        </div>
                        <div className="text-[11px] text-muted-foreground mt-0.5 line-clamp-2">
                          {preset.desc}
                        </div>
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Step Navigation Bar */}
              <div className="flex items-center justify-between border-b border-border pb-4">
                {[
                  { step: 1, label: "Insured Members", sub: "Who are you covering?" },
                  { step: 2, label: "Coverage & Budget", sub: "Sum insured & limit" },
                  { step: 3, label: "Priorities & Health", sub: "PED & Co-pay tolerance" },
                  { step: 4, label: "Porting & Network", sub: "Existing policies & hospital" },
                ].map((item) => (
                  <button
                    key={item.step}
                    onClick={() => setCurrentStep(item.step)}
                    className={`flex items-center gap-3 text-left transition-all ${
                      currentStep === item.step
                        ? "text-primary opacity-100"
                        : currentStep > item.step
                        ? "text-emerald-400 opacity-90"
                        : "text-muted-foreground opacity-50"
                    }`}
                  >
                    <div
                      className={`w-8 h-8 rounded-full flex items-center justify-center font-bold text-xs border ${
                        currentStep === item.step
                          ? "bg-primary text-primary-foreground border-primary shadow-lg shadow-primary/20"
                          : currentStep > item.step
                          ? "bg-emerald-500/20 border-emerald-500 text-emerald-400"
                          : "bg-muted border-border"
                      }`}
                    >
                      {currentStep > item.step ? <Check className="w-4 h-4" /> : item.step}
                    </div>
                    <div className="hidden sm:block">
                      <div className="text-xs font-semibold">{item.label}</div>
                      <div className="text-[10px] text-muted-foreground">{item.sub}</div>
                    </div>
                  </button>
                ))}
              </div>

              {/* Form Content Cards */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 space-y-6">
                  {/* STEP 1: Who Are You Covering */}
                  {currentStep === 1 && (
                    <motion.div
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      className="glass rounded-2xl p-6 border border-border space-y-6"
                    >
                      <div>
                        <h3 className="text-base font-bold flex items-center gap-2">
                          <Users className="w-4 h-4 text-primary" />
                          Step 1: Choose Insurance Structure & Primary Insured
                        </h3>
                        <p className="text-xs text-muted-foreground mt-1">
                          Health insurance premiums in India depend heavily on the age of the eldest member and location zone.
                        </p>
                      </div>

                      {/* Insurance Structure Selector */}
                      <div>
                        <label className="text-xs font-semibold text-muted-foreground block mb-2">
                          Coverage Type
                        </label>
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                          {[
                            { id: "INDIVIDUAL", label: "Individual", icon: User },
                            { id: "SELF_AND_SPOUSE", label: "Self & Spouse", icon: Users },
                            { id: "FAMILY_WITH_CHILDREN", label: "Family + Kids", icon: Users },
                            { id: "PARENTS", label: "Parents", icon: HeartPulse },
                          ].map((t) => {
                            const Icon = t.icon;
                            const isSelected = insuranceType === t.id;
                            return (
                              <button
                                key={t.id}
                                type="button"
                                onClick={() => handleInsuranceTypeChange(t.id)}
                                className={`p-3.5 rounded-xl border text-center transition-all flex flex-col items-center gap-2 ${
                                  isSelected
                                    ? "bg-primary/10 border-primary text-primary shadow-sm"
                                    : "bg-card border-border hover:border-border/80 text-muted-foreground hover:text-foreground"
                                }`}
                              >
                                <Icon className={`w-5 h-5 ${isSelected ? "text-primary" : "text-muted-foreground"}`} />
                                <span className="text-xs font-semibold">{t.label}</span>
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      {/* Primary Insured Age & Location */}
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="text-xs font-semibold text-muted-foreground block mb-1.5">
                            Primary Member Age (Years) *
                          </label>
                          <div className="relative">
                            <input
                              type="number"
                              min={18}
                              max={99}
                              value={primaryAge}
                              onChange={(e) => setPrimaryAge(Number(e.target.value))}
                              className="w-full px-3.5 py-2.5 rounded-xl bg-card border border-border focus:border-primary focus:outline-none text-sm font-medium"
                            />
                            <span className="absolute right-3.5 top-2.5 text-xs text-muted-foreground">Yrs</span>
                          </div>
                        </div>

                        <div>
                          <label className="text-xs font-semibold text-muted-foreground block mb-1.5">
                            City or PIN Code (For Zone Pricing) *
                          </label>
                          <div className="relative">
                            <input
                              type="text"
                              value={cityOrPincode}
                              placeholder="e.g. Mumbai, Bengaluru, 110001"
                              onChange={(e) => setCityOrPincode(e.target.value)}
                              className="w-full px-3.5 py-2.5 rounded-xl bg-card border border-border focus:border-primary focus:outline-none text-sm font-medium"
                            />
                            <Building2 className="w-4 h-4 text-muted-foreground absolute right-3.5 top-3" />
                          </div>
                        </div>
                      </div>

                      {/* Additional Members List */}
                      {insuranceType !== "INDIVIDUAL" && (
                        <div className="pt-4 border-t border-border space-y-3">
                          <div className="flex items-center justify-between">
                            <label className="text-xs font-semibold text-muted-foreground">
                              Covered Family Members ({members.length})
                            </label>
                            {insuranceType === "FAMILY_WITH_CHILDREN" && members.filter((m) => m.relation === "CHILD").length < 4 && (
                              <button
                                type="button"
                                onClick={() => addMember("CHILD")}
                                className="text-xs text-primary font-semibold hover:underline"
                              >
                                + Add Another Child
                              </button>
                            )}
                          </div>

                          <div className="space-y-2">
                            {members.map((member, index) => (
                              <div
                                key={index}
                                className="p-3 rounded-xl border border-border bg-card/40 flex items-center justify-between gap-3"
                              >
                                <div className="flex items-center gap-2">
                                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-muted text-muted-foreground">
                                    {member.relation}
                                  </span>
                                  <span className="text-xs font-medium">{member.displayName}</span>
                                </div>
                                <div className="flex items-center gap-2">
                                  <span className="text-xs text-muted-foreground">Age:</span>
                                  <input
                                    type="number"
                                    min={0}
                                    max={99}
                                    value={member.age}
                                    onChange={(e) => updateMemberAge(index, Number(e.target.value))}
                                    className="w-16 px-2 py-1 rounded-lg bg-card border border-border text-xs text-center font-medium focus:border-primary focus:outline-none"
                                  />
                                  {members.length > 1 && member.relation === "CHILD" && (
                                    <button
                                      type="button"
                                      onClick={() => removeMember(index)}
                                      className="text-rose-400 hover:text-rose-300 p-1"
                                    >
                                      <X className="w-3.5 h-3.5" />
                                    </button>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      <div className="pt-4 flex justify-end">
                        <button
                          type="button"
                          onClick={() => setCurrentStep(2)}
                          className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-primary hover:bg-primary/90 text-primary-foreground font-semibold text-xs shadow-lg shadow-primary/20 transition-all"
                        >
                          Next: Coverage & Budget
                          <ArrowRight className="w-4 h-4" />
                        </button>
                      </div>
                    </motion.div>
                  )}

                  {/* STEP 2: Coverage & Budget */}
                  {currentStep === 2 && (
                    <motion.div
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      className="glass rounded-2xl p-6 border border-border space-y-6"
                    >
                      <div>
                        <h3 className="text-base font-bold flex items-center gap-2">
                          <DollarSign className="w-4 h-4 text-emerald-400" />
                          Step 2: Desired Sum Insured & Annual Premium Budget
                        </h3>
                        <p className="text-xs text-muted-foreground mt-1">
                          Select the target coverage sum and your annual spend target. Enable &quot;Help Me Decide&quot; if you want the algorithm to calculate based on medical inflation in {cityOrPincode}.
                        </p>
                      </div>

                      {/* Desired Sum Insured Selector */}
                      <div className="space-y-3">
                        <div className="flex items-center justify-between">
                          <label className="text-xs font-semibold text-muted-foreground">
                            Target Sum Insured (Hospitalization Cover)
                          </label>
                          <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-emerald-400">
                            <input
                              type="checkbox"
                              checked={helpMeDecideSumInsured}
                              onChange={(e) => setHelpMeDecideSumInsured(e.target.checked)}
                              className="rounded border-border accent-emerald-500 w-3.5 h-3.5"
                            />
                            <span>Help Me Decide (AI Guided)</span>
                          </label>
                        </div>

                        {!helpMeDecideSumInsured ? (
                          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                            {[
                              { value: 500000, label: "₹5 Lakh" },
                              { value: 1000000, label: "₹10 Lakh (Standard)" },
                              { value: 2500000, label: "₹25 Lakh (Recommended)" },
                              { value: 5000000, label: "₹50 Lakh (High Net)" },
                            ].map((tier) => (
                              <button
                                key={tier.value}
                                type="button"
                                onClick={() => setDesiredSumInsured(tier.value)}
                                className={`py-3 px-2 rounded-xl border text-center transition-all ${
                                  desiredSumInsured === tier.value
                                    ? "bg-emerald-500/10 border-emerald-500 text-emerald-400 font-bold shadow-sm"
                                    : "bg-card border-border hover:border-border/80 text-muted-foreground hover:text-foreground"
                                }`}
                              >
                                <div className="text-xs">{tier.label}</div>
                              </button>
                            ))}
                          </div>
                        ) : (
                          <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs flex items-start gap-3">
                            <Sparkles className="w-4 h-4 flex-shrink-0 mt-0.5 text-emerald-400" />
                            <div>
                              <p className="font-semibold">AI Calculated Optimal Sum Insured: ₹{guidance ? (guidance.recommendedSumInsured / 100000) : 10} Lakh</p>
                              <p className="mt-1 text-emerald-200/80 leading-relaxed">
                                {guidance?.rationale || "Calculated based on average tertiary hospital ICU charges in your city and medical inflation rate."}
                              </p>
                            </div>
                          </div>
                        )}
                      </div>

                      {/* Annual Budget Slider & Type */}
                      <div className="space-y-3 pt-4 border-t border-border">
                        <div className="flex items-center justify-between">
                          <label className="text-xs font-semibold text-muted-foreground">
                            Target Annual Premium Budget
                          </label>
                          <span className="text-sm font-bold text-foreground">
                            ₹{annualBudget.toLocaleString("en-IN")} / year
                          </span>
                        </div>

                        <input
                          type="range"
                          min={8000}
                          max={90000}
                          step={1000}
                          value={annualBudget}
                          onChange={(e) => setAnnualBudget(Number(e.target.value))}
                          className="w-full accent-primary h-2 bg-card rounded-lg cursor-pointer"
                        />

                        <div className="flex items-center justify-between text-[11px] text-muted-foreground">
                          <span>₹8,000 / yr</span>
                          <span>₹45,000 / yr</span>
                          <span>₹90,000 / yr</span>
                        </div>

                        {/* Strict vs Preferred */}
                        <div className="pt-2">
                          <span className="text-xs font-semibold text-muted-foreground block mb-2">Budget Flexibility</span>
                          <div className="grid grid-cols-2 gap-3">
                            <button
                              type="button"
                              onClick={() => setBudgetType("PREFERRED")}
                              className={`p-3 rounded-xl border text-left transition-all ${
                                budgetType === "PREFERRED"
                                  ? "bg-primary/10 border-primary text-primary"
                                  : "bg-card border-border text-muted-foreground"
                              }`}
                            >
                              <div className="text-xs font-semibold">Preferred Budget (±20% flexibility)</div>
                              <div className="text-[11px] text-muted-foreground mt-0.5">Show policies with superior room rent & co-pay safety if slightly higher</div>
                            </button>

                            <button
                              type="button"
                              onClick={() => setBudgetType("STRICT")}
                              className={`p-3 rounded-xl border text-left transition-all ${
                                budgetType === "STRICT"
                                  ? "bg-primary/10 border-primary text-primary"
                                  : "bg-card border-border text-muted-foreground"
                              }`}
                            >
                              <div className="text-xs font-semibold">Strict Budget Priority</div>
                              <div className="text-[11px] text-muted-foreground mt-0.5">Applies heavy cost penalties to any policy exceeding your annual budget</div>
                            </button>
                          </div>
                        </div>
                      </div>

                      <div className="pt-4 flex justify-between">
                        <button
                          type="button"
                          onClick={() => setCurrentStep(1)}
                          className="flex items-center gap-2 px-4 py-2 rounded-xl border border-border hover:bg-card text-xs font-medium transition-colors"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" /> Back
                        </button>
                        <button
                          type="button"
                          onClick={() => setCurrentStep(3)}
                          className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-primary hover:bg-primary/90 text-primary-foreground font-semibold text-xs shadow-lg shadow-primary/20 transition-all"
                        >
                          Next: Priorities & Health
                          <ArrowRight className="w-4 h-4" />
                        </button>
                      </div>
                    </motion.div>
                  )}

                  {/* STEP 3: Priorities & Health */}
                  {currentStep === 3 && (
                    <motion.div
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      className="glass rounded-2xl p-6 border border-border space-y-6"
                    >
                      <div>
                        <h3 className="text-base font-bold flex items-center gap-2">
                          <Activity className="w-4 h-4 text-amber-400" />
                          Step 3: Primary Priority & Declared Health Conditions
                        </h3>
                        <p className="text-xs text-muted-foreground mt-1">
                          Tells the engine how to weigh the 4 scoring dimensions (cost, coverage, out-of-pocket, insurer reliability).
                        </p>
                      </div>

                      {/* Primary Priority Selector */}
                      <div>
                        <label className="text-xs font-semibold text-muted-foreground block mb-2">
                          What matters most to you? *
                        </label>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                          {[
                            {
                              id: "BALANCED",
                              title: "Balanced Value",
                              desc: "Optimal equilibrium across price, zero room-rent trap, and high claim settlement.",
                            },
                            {
                              id: "LOWER_OUT_OF_POCKET",
                              title: "Lowest Out-of-Pocket Risk",
                              desc: "Prioritizes zero co-pay, zero room rent capping, and minimal disease sub-limits.",
                            },
                            {
                              id: "BROADER_COVERAGE",
                              title: "Broadest Coverage",
                              desc: "Demands unlimited restoration, worldwide cover, and shortest waiting periods.",
                            },
                            {
                              id: "AFFORDABLE_PREMIUMS",
                              title: "Lowest Annual Premium",
                              desc: "Highest weight given to keeping recurring annual outlay within minimum budget.",
                            },
                          ].map((p) => {
                            const isSelected = primaryPriority === p.id;
                            return (
                              <button
                                key={p.id}
                                type="button"
                                onClick={() => setPrimaryPriority(p.id as any)}
                                className={`p-3.5 rounded-xl border text-left transition-all ${
                                  isSelected
                                    ? "bg-amber-500/10 border-amber-500/80 text-amber-300 shadow-sm"
                                    : "bg-card border-border hover:border-border/80 text-muted-foreground hover:text-foreground"
                                }`}
                              >
                                <div className="text-xs font-bold">{p.title}</div>
                                <div className="text-[11px] text-muted-foreground mt-1 leading-normal">{p.desc}</div>
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      {/* Pre-Existing Conditions */}
                      <div className="pt-4 border-t border-border">
                        <label className="text-xs font-semibold text-muted-foreground block mb-1.5">
                          Declared Pre-Existing Medical History (PED)
                        </label>
                        <p className="text-[11px] text-muted-foreground mb-3">
                          Policies with waiting period reduction or lower PED waiting times will be favored for these conditions.
                        </p>

                        <div className="flex flex-wrap gap-2">
                          {COMMON_CONDITIONS.map((cond) => {
                            const cleanName = cond.split(" ")[0].toUpperCase();
                            const isSelected =
                              cond === "None / Healthy"
                                ? preExistingConditions.length === 0
                                : preExistingConditions.includes(cleanName);
                            return (
                              <button
                                key={cond}
                                type="button"
                                onClick={() => toggleCondition(cond)}
                                className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                                  isSelected
                                    ? "bg-primary text-primary-foreground border-primary shadow-sm"
                                    : "bg-card border-border text-muted-foreground hover:text-foreground"
                                }`}
                              >
                                {cond}
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      {/* Additional Special Benefits */}
                      <div className="pt-4 border-t border-border grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <label className="flex items-start gap-3 p-3 rounded-xl border border-border bg-card/40 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={maternityBenefitNeeded}
                            onChange={(e) => setMaternityBenefitNeeded(e.target.checked)}
                            className="rounded border-border accent-primary mt-0.5 w-4 h-4"
                          />
                          <div>
                            <div className="text-xs font-semibold">Maternity & Newborn Cover</div>
                            <div className="text-[11px] text-muted-foreground">Checks maternity waiting periods (24 - 36 months)</div>
                          </div>
                        </label>

                        <label className="flex items-start gap-3 p-3 rounded-xl border border-border bg-card/40 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={opdBenefitNeeded}
                            onChange={(e) => setOpdBenefitNeeded(e.target.checked)}
                            className="rounded border-border accent-primary mt-0.5 w-4 h-4"
                          />
                          <div>
                            <div className="text-xs font-semibold">OPD & Daycare Consultations</div>
                            <div className="text-[11px] text-muted-foreground">Scrutinizes outpatient consultation allowances</div>
                          </div>
                        </label>
                      </div>

                      <div className="pt-4 flex justify-between">
                        <button
                          type="button"
                          onClick={() => setCurrentStep(2)}
                          className="flex items-center gap-2 px-4 py-2 rounded-xl border border-border hover:bg-card text-xs font-medium transition-colors"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" /> Back
                        </button>
                        <button
                          type="button"
                          onClick={() => setCurrentStep(4)}
                          className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-primary hover:bg-primary/90 text-primary-foreground font-semibold text-xs shadow-lg shadow-primary/20 transition-all"
                        >
                          Next: Porting & Verification
                          <ArrowRight className="w-4 h-4" />
                        </button>
                      </div>
                    </motion.div>
                  )}

                  {/* STEP 4: Porting & Verification */}
                  {currentStep === 4 && (
                    <motion.div
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      className="glass rounded-2xl p-6 border border-border space-y-6"
                    >
                      <div>
                        <h3 className="text-base font-bold flex items-center gap-2">
                          <Shield className="w-4 h-4 text-emerald-400" />
                          Step 4: Existing Policy Porting & Verification
                        </h3>
                        <p className="text-xs text-muted-foreground mt-1">
                          Under IRDAI porting regulations, your accumulated waiting period credits transfer seamlessly to your new policy.
                        </p>
                      </div>

                      <div className="space-y-4">
                        <label className="flex items-start gap-3 p-4 rounded-xl border border-border bg-card/40 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={hasExistingInsurance}
                            onChange={(e) => setHasExistingInsurance(e.target.checked)}
                            className="rounded border-border accent-primary mt-0.5 w-4 h-4"
                          />
                          <div>
                            <div className="text-xs font-bold text-foreground">I currently have an active health insurance policy</div>
                            <div className="text-[11px] text-muted-foreground mt-0.5">
                              Enables portability credit matching for pre-existing disease waiting periods.
                            </div>
                          </div>
                        </label>

                        {hasExistingInsurance && (
                          <div className="pl-6 border-l-2 border-primary/30 space-y-3">
                            <div>
                              <label className="text-xs font-semibold text-muted-foreground block mb-1">
                                Current Sum Insured
                              </label>
                              <div className="grid grid-cols-3 gap-2">
                                {[300000, 500000, 1000000].map((amt) => (
                                  <button
                                    key={amt}
                                    type="button"
                                    onClick={() => setExistingSumInsured(amt)}
                                    className={`py-2 px-2 rounded-lg border text-xs font-semibold ${
                                      existingSumInsured === amt
                                        ? "bg-primary/10 border-primary text-primary"
                                        : "bg-card border-border text-muted-foreground"
                                    }`}
                                  >
                                    ₹{amt / 100000} Lakh
                                  </button>
                                ))}
                              </div>
                            </div>

                            <label className="flex items-center gap-2 text-xs font-medium cursor-pointer">
                              <input
                                type="checkbox"
                                checked={portingPolicy}
                                onChange={(e) => setPortingPolicy(e.target.checked)}
                                className="rounded border-border accent-primary"
                              />
                              <span>I plan to port this policy at next renewal to keep continuity benefits</span>
                            </label>
                          </div>
                        )}
                      </div>

                      {/* Ready to generate banner */}
                      <div className="p-4 rounded-xl bg-gradient-to-r from-emerald-500/10 via-teal-500/10 to-primary/10 border border-emerald-500/20 text-xs space-y-2">
                        <div className="flex items-center gap-2 font-bold text-emerald-400">
                          <CheckCircle className="w-4 h-4" />
                          Ready to Run Deterministic Scoring
                        </div>
                        <p className="text-muted-foreground leading-relaxed">
                          The recommendation engine will filter the policy catalog against hard product eligibility rules, compute zone pricing, score all 4 dimensions, and extract the exact contract wording evidence for you.
                        </p>
                      </div>

                      <div className="pt-4 flex justify-between">
                        <button
                          type="button"
                          onClick={() => setCurrentStep(3)}
                          className="flex items-center gap-2 px-4 py-2 rounded-xl border border-border hover:bg-card text-xs font-medium transition-colors"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" /> Back
                        </button>
                        <button
                          type="button"
                          onClick={handleSubmitQuestionnaire}
                          className="flex items-center gap-2 px-6 py-3 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-600 hover:to-teal-700 text-white font-bold text-xs shadow-lg shadow-emerald-500/20 transition-all scale-100 hover:scale-[1.02]"
                        >
                          <Sparkles className="w-4 h-4" />
                          Generate Verified Recommendations
                        </button>
                      </div>
                    </motion.div>
                  )}
                </div>

                {/* Right Side: Dynamic AI Advisor & Live Rule Guidance */}
                <div className="space-y-6">
                  {/* Realtime Advisor Card */}
                  <div className="glass rounded-2xl p-5 border border-border/80 space-y-4 sticky top-24">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 text-xs font-bold text-emerald-400">
                        <Sparkles className="w-4 h-4" />
                        AI Actuarial Advisor
                      </div>
                      <span className="text-[10px] uppercase font-semibold tracking-wider text-muted-foreground px-2 py-0.5 rounded bg-muted">
                        Live Preview
                      </span>
                    </div>

                    <div className="space-y-3 text-xs">
                      <div className="p-3 rounded-xl bg-card border border-border space-y-1">
                        <span className="text-muted-foreground text-[11px] block">Hospitalization Zone:</span>
                        <div className="font-semibold text-foreground flex items-center justify-between">
                          <span>{cityOrPincode || "Specify Location"}</span>
                          <span className="text-[10px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 font-bold">
                            {guidance?.zone || "Zone 1"}
                          </span>
                        </div>
                      </div>

                      <div className="p-3 rounded-xl bg-card border border-border space-y-1">
                        <span className="text-muted-foreground text-[11px] block">Recommended Sum Insured:</span>
                        <div className="font-bold text-base text-emerald-400">
                          {guidance?.formattedRecommendedSumInsured || "₹10 - 25 Lakh"}
                        </div>
                        <p className="text-[11px] text-muted-foreground leading-normal mt-1">
                          {guidance?.rationale || "Calculated based on average tertiary hospital ICU charges in your city and medical inflation rate."}
                        </p>
                      </div>

                      <div className="p-3 rounded-xl bg-card border border-border space-y-1">
                        <span className="text-muted-foreground text-[11px] block">Estimated Premium Range:</span>
                        <div className="font-semibold text-foreground">
                          ₹{guidance?.estimatedMinBudget?.toLocaleString("en-IN") || "14,000"} — ₹{guidance?.estimatedMaxBudget?.toLocaleString("en-IN") || "32,000"} / yr
                        </div>
                        <div className="text-[11px] text-muted-foreground mt-1">
                          {guidance?.budgetAdequacyNote || "Your allocated budget provides healthy coverage options across top Indian insurers."}
                        </div>
                      </div>

                      {guidance?.cityHealthcareInsights && guidance.cityHealthcareInsights.length > 0 && (
                        <div className="p-3 rounded-xl bg-emerald-500/5 border border-emerald-500/20 space-y-1.5">
                          <span className="text-[11px] font-bold text-emerald-400 flex items-center gap-1.5">
                            <Building2 className="w-3.5 h-3.5" />
                            Local City Healthcare Dynamics:
                          </span>
                          <ul className="space-y-1 text-[11px] text-muted-foreground">
                            {guidance.cityHealthcareInsights.map((insight, idx) => (
                              <li key={idx} className="flex items-start gap-1.5">
                                <span className="text-emerald-400 mt-0.5">•</span>
                                <span>{insight}</span>
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}

                      {/* Anti-Trap Rule Highlights */}
                      <div className="pt-2 border-t border-border/80">
                        <span className="text-[11px] font-bold text-muted-foreground block mb-2">
                          🛡️ Traps Automatically Scrutinized:
                        </span>
                        <ul className="space-y-1.5 text-[11px] text-muted-foreground">
                          <li className="flex items-center gap-1.5">
                            <CheckCircle className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
                            <span>Room rent proportionate deduction traps</span>
                          </li>
                          <li className="flex items-center gap-1.5">
                            <CheckCircle className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
                            <span>Hidden senior citizen mandatory co-payments</span>
                          </li>
                          <li className="flex items-center gap-1.5">
                            <CheckCircle className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
                            <span>Cataract & knee replacement sub-limit caps</span>
                          </li>
                          <li className="flex items-center gap-1.5">
                            <CheckCircle className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
                            <span>Cashless network hospital availability</span>
                          </li>
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ========================================================================= */}
          {/* VIEW 2: LOADING STATE WITH ANIMATED STAGES */}
          {/* ========================================================================= */}
          {viewState === "loading" && (
            <div className="flex flex-col items-center justify-center min-h-[60vh] space-y-6">
              <div className="relative">
                <div className="w-20 h-20 rounded-full border-4 border-emerald-500/20 border-t-emerald-500 animate-spin" />
                <HeartPulse className="w-8 h-8 text-emerald-400 absolute inset-0 m-auto" />
              </div>

              <div className="text-center max-w-md space-y-2">
                <h3 className="text-xl font-bold">Auditing Policy Catalog & Contracts</h3>
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Deterministic engine is checking eligibility rules, calculating zone premiums, and cross-referencing policy wordings...
                </p>
              </div>

              <div className="w-full max-w-sm glass rounded-xl p-4 border border-border space-y-2 text-xs">
                <div className="flex items-center gap-2 text-emerald-400 font-medium">
                  <CheckCircle className="w-4 h-4" />
                  <span>Filtering out restrictive room rent policies</span>
                </div>
                <div className="flex items-center gap-2 text-emerald-400 font-medium">
                  <CheckCircle className="w-4 h-4" />
                  <span>Computing 4-pillar dimensional match score</span>
                </div>
                <div className="flex items-center gap-2 text-emerald-400 font-medium animate-pulse">
                  <Sparkles className="w-4 h-4 text-primary" />
                  <span>Linking official IRDAI clause evidence citations</span>
                </div>
              </div>
            </div>
          )}

          {/* ========================================================================= */}
          {/* VIEW 3: THREE-QUESTION ANSWER DASHBOARD */}
          {/* ========================================================================= */}
          {viewState === "results" && recommendationResponse && (
            <div className="space-y-8 animate-in fade-in duration-300">
              {/* Evaluated Profile Ribbon */}
              <div className="glass rounded-2xl p-5 border border-border flex flex-wrap items-center justify-between gap-4 bg-gradient-to-r from-card to-emerald-950/20">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 rounded-xl bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-400 font-bold text-lg">
                    {recommendationResponse.eligiblePoliciesCount}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h2 className="text-lg font-bold">Evaluated Recommendation Summary</h2>
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                        {recommendationResponse.totalPoliciesEvaluated} Policies Audited
                      </span>
                    </div>
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground mt-1">
                      <span>{recommendationResponse.profileSummary?.insuranceType}</span>
                      <span>•</span>
                      <span>{recommendationResponse.profileSummary?.totalInsuredMembers} Insured</span>
                      <span>•</span>
                      <span>Zone: {recommendationResponse.profileSummary?.pricingZone}</span>
                      <span>•</span>
                      <span>Budget: {recommendationResponse.profileSummary?.formattedBudget}</span>
                      <span>•</span>
                      <span>Sum Insured: {recommendationResponse.profileSummary?.formattedSumInsured}</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  {comparingPolicyIds.length >= 2 && (
                    <button
                      onClick={() => setShowComparisonModal(true)}
                      className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary hover:bg-primary/90 text-primary-foreground font-semibold text-xs shadow-lg shadow-primary/20 transition-all"
                    >
                      <Layers className="w-4 h-4" />
                      <span>Compare ({comparingPolicyIds.length})</span>
                    </button>
                  )}
                  <button
                    onClick={() => window.print()}
                    className="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-border hover:bg-card text-xs font-medium transition-colors"
                  >
                    <Download className="w-4 h-4 text-muted-foreground" />
                    <span>Export</span>
                  </button>
                </div>
              </div>

              {/* Data Integrity & Classification Banner */}
              <div className="p-4 rounded-xl bg-card/60 border border-border flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 text-xs">
                <div className="flex items-start gap-3">
                  <div className="w-8 h-8 rounded-lg bg-primary/10 border border-primary/20 flex items-center justify-center text-primary flex-shrink-0 mt-0.5">
                    <Info className="w-4 h-4" />
                  </div>
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 font-bold text-foreground">
                      <span>IRDAI Regulatory Audit & Data Classification Framework</span>
                      <span className="px-1.5 py-0.5 rounded text-[10px] bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                        Strict Provenance Active
                      </span>
                    </div>
                    <p className="text-muted-foreground leading-relaxed text-[11px]">
                      Recommendations strictly separate <strong className="text-emerald-400">Verified Contractual Facts</strong> (canonical UINs, clause-level wordings, waiting periods) from <strong className="text-teal-400">Dated Regulatory Statistics</strong> (IRDAI Annual Report FY 2022-23 Table 4.5) and <strong className="text-amber-400">Modeled Actuarial Estimates</strong> (indicative premiums & simulated bills). Quotes are non-binding pending insurer medical tele-underwriting.
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                  <span className="text-[10px] font-mono px-2.5 py-1 rounded-lg bg-card border border-border text-muted-foreground">
                    IRDAI Baseline: FY 2022-23 Table 4.5
                  </span>
                </div>
              </div>

              {/* Core 3-Question Navigation Tabs */}
              <div className="border-b border-border flex flex-wrap gap-2 sm:gap-6">
                {[
                  {
                    id: "q1_policies",
                    label: "1. Best Matching Policies",
                    desc: "Ranked by 4-pillar match",
                    icon: Award,
                  },
                  {
                    id: "q2_limitations",
                    label: "2. Limitations & Out-of-Pocket Traps",
                    desc: "Room rent, copay & scenario OOP",
                    icon: AlertTriangle,
                  },
                  {
                    id: "q3_evidence",
                    label: "3. Official Policy Evidence",
                    desc: "Exact verified contract clauses",
                    icon: BookOpen,
                  },
                  {
                    id: "metrics",
                    label: "Insurer Scorecards",
                    desc: "IRDAI CSR, network & speed",
                    icon: BarChart2,
                  },
                ].map((tab) => {
                  const Icon = tab.icon;
                  const isActive = activeResultsTab === tab.id;
                  return (
                    <button
                      key={tab.id}
                      onClick={() => setActiveResultsTab(tab.id as any)}
                      className={`pb-3 pt-1 px-1 flex items-center gap-2.5 border-b-2 text-left transition-all ${
                        isActive
                          ? "border-emerald-500 text-emerald-400 font-bold"
                          : "border-transparent text-muted-foreground hover:text-foreground"
                      }`}
                    >
                      <Icon className={`w-4 h-4 ${isActive ? "text-emerald-400" : "text-muted-foreground"}`} />
                      <div>
                        <div className="text-xs">{tab.label}</div>
                        <div className="text-[10px] text-muted-foreground font-normal">{tab.desc}</div>
                      </div>
                    </button>
                  );
                })}
              </div>

              {/* ================================================================= */}
              {/* TAB 1: QUESTION 1 — BEST MATCHING POLICIES */}
              {/* ================================================================= */}
              {activeResultsTab === "q1_policies" && (
                <div className="space-y-6">
                  {/* Sorting & Filter controls */}
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-semibold text-muted-foreground">Sort By:</span>
                      <div className="flex flex-wrap gap-1.5">
                        {[
                          { id: "match", label: "Overall Match Score" },
                          { id: "premium_asc", label: "Lowest Annual Premium" },
                          { id: "oop_safety", label: "Out-of-Pocket Safety" },
                          { id: "settlement", label: "Claim Settlement %" },
                        ].map((s) => (
                          <button
                            key={s.id}
                            onClick={() => setSortBy(s.id as any)}
                            className={`px-3 py-1 rounded-lg text-xs font-medium border transition-colors ${
                              sortBy === s.id
                                ? "bg-emerald-500/20 border-emerald-500 text-emerald-300 font-semibold"
                                : "bg-card border-border text-muted-foreground hover:text-foreground"
                            }`}
                          >
                            {s.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    <span className="text-xs text-muted-foreground">
                      Showing <strong>{sortedPolicies.length}</strong> eligible policies
                    </span>
                  </div>

                  {/* Policy Cards Grid */}
                  <div className="space-y-5">
                    {sortedPolicies.map((policy) => {
                      const isTopRank = policy.rank === 1;
                      const isSelectedForDetail = selectedPolicyDetail?.policyId === policy.policyId;
                      const isComparing = comparingPolicyIds.includes(policy.policyId);

                      return (
                        <div
                          key={policy.policyId}
                          className={`glass rounded-2xl p-6 border transition-all relative overflow-hidden ${
                            isTopRank
                              ? "border-emerald-500/60 shadow-lg shadow-emerald-500/10 bg-gradient-to-r from-card via-card to-emerald-950/20"
                              : "border-border hover:border-border/80"
                          }`}
                        >
                          {/* Rank Badge */}
                          {isTopRank && (
                            <div className="absolute top-0 right-0 bg-gradient-to-l from-emerald-500 to-teal-600 text-white text-[11px] font-bold px-4 py-1 rounded-bl-xl shadow-md flex items-center gap-1.5">
                              <Star className="w-3.5 h-3.5 fill-current" />
                              Rank #1 • Best Overall Recommendation
                            </div>
                          )}

                          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-center">
                            {/* Left Col: Match Dial & Rank */}
                            <div className="lg:col-span-3 flex sm:flex-col items-center sm:items-start gap-4">
                              <div className="flex items-center gap-3">
                                <div className="w-10 h-10 rounded-xl bg-card border border-border flex items-center justify-center font-bold text-sm text-foreground">
                                  #{policy.rank}
                                </div>
                                <div>
                                  <div className="text-sm font-bold text-foreground">{policy.policyName}</div>
                                  <div className="text-xs text-muted-foreground">{policy.provider}</div>
                                  <div className="flex flex-wrap items-center gap-1.5 mt-1">
                                    <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-muted/80 border border-border text-foreground">
                                      UIN: {policy.irdaUin}
                                    </span>
                                    {policy.uinRevision && (
                                      <span className="text-[10px] font-mono text-muted-foreground">
                                        ({policy.uinRevision})
                                      </span>
                                    )}
                                  </div>
                                  <div className="mt-1">
                                    <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 uppercase tracking-tight">
                                      {policy.verificationStatus || "VERIFIED_OFFICIAL_CATALOG"}
                                    </span>
                                  </div>
                                </div>
                              </div>

                              <div className="flex items-center gap-3 pt-2">
                                <ScoreRing score={Math.round(policy.overallMatchScore)} label="Match Score" size={72} strokeWidth={6} />
                                <div className="text-xs space-y-0.5">
                                  <div className="text-[10px] uppercase font-bold tracking-wider text-muted-foreground">Scoring Grade</div>
                                  <div className="font-bold text-emerald-400 text-sm">
                                    {policy.overallMatchScore >= 90 ? "Excellent" : policy.overallMatchScore >= 80 ? "Very Good" : "Good"} Match
                                  </div>
                                  <div className="text-[10px] text-muted-foreground">
                                    {policy.matchBadges?.[0] || "Verified Product"}
                                  </div>
                                </div>
                              </div>
                            </div>

                            {/* Middle Col: 4-Pillar Score Bars & Key Highlights */}
                            <div className="lg:col-span-5 space-y-3">
                              {/* 4 Pillars */}
                              <div className="grid grid-cols-2 gap-2 text-xs">
                                <div className="p-2 rounded-lg bg-card/60 border border-border/80">
                                  <div className="flex justify-between text-[11px] mb-1">
                                    <span className="text-muted-foreground">Cost & Budget</span>
                                    <span className="font-bold text-foreground">{Math.round(policy.costScore)}/100</span>
                                  </div>
                                  <div className="w-full bg-muted h-1.5 rounded-full overflow-hidden">
                                    <div className="bg-emerald-400 h-full rounded-full" style={{ width: `${policy.costScore}%` }} />
                                  </div>
                                </div>

                                <div className="p-2 rounded-lg bg-card/60 border border-border/80">
                                  <div className="flex justify-between text-[11px] mb-1">
                                    <span className="text-muted-foreground">Coverage Scope</span>
                                    <span className="font-bold text-foreground">{Math.round(policy.coverageScore)}/100</span>
                                  </div>
                                  <div className="w-full bg-muted h-1.5 rounded-full overflow-hidden">
                                    <div className="bg-primary h-full rounded-full" style={{ width: `${policy.coverageScore}%` }} />
                                  </div>
                                </div>

                                <div className="p-2 rounded-lg bg-card/60 border border-border/80">
                                  <div className="flex justify-between text-[11px] mb-1">
                                    <span className="text-muted-foreground">Out-of-Pocket Safety</span>
                                    <span className="font-bold text-foreground">{Math.round(policy.outOfPocketProtectionScore)}/100</span>
                                  </div>
                                  <div className="w-full bg-muted h-1.5 rounded-full overflow-hidden">
                                    <div className="bg-amber-400 h-full rounded-full" style={{ width: `${policy.outOfPocketProtectionScore}%` }} />
                                  </div>
                                </div>

                                <div className="p-2 rounded-lg bg-card/60 border border-border/80">
                                  <div className="flex justify-between text-[11px] mb-1">
                                    <span className="text-muted-foreground">Insurer Reliability</span>
                                    <span className="font-bold text-foreground">{Math.round(policy.insurerReliabilityScore)}/100</span>
                                  </div>
                                  <div className="w-full bg-muted h-1.5 rounded-full overflow-hidden">
                                    <div className="bg-teal-400 h-full rounded-full" style={{ width: `${policy.insurerReliabilityScore}%` }} />
                                  </div>
                                </div>
                              </div>

                              {/* Key Match Badges */}
                              <div className="flex flex-wrap gap-1.5">
                                {policy.matchBadges?.map((badge, bIdx) => (
                                  <span
                                    key={bIdx}
                                    className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-emerald-500/10 text-emerald-300 border border-emerald-500/20"
                                  >
                                    ✓ {badge}
                                  </span>
                                ))}
                              </div>
                            </div>

                            {/* Right Col: Pricing, Budget & Action */}
                            <div className="lg:col-span-4 flex flex-col justify-between h-full p-4 rounded-xl bg-card/50 border border-border space-y-3">
                              <div>
                                <div className="flex items-center justify-between gap-1">
                                  <span className="text-[11px] text-muted-foreground">Estimated Annual Premium</span>
                                  <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-amber-500/10 text-amber-400 border border-amber-500/20 uppercase">
                                    Modeled Estimate
                                  </span>
                                </div>
                                <div className="text-2xl font-black text-foreground mt-0.5">
                                  {policy.formattedAnnualPremium}
                                  <span className="text-xs font-normal text-muted-foreground"> / year</span>
                                </div>
                                <div className="text-[10px] text-muted-foreground/80 mt-0.5">
                                  Non-binding quote • Tele-underwriting required
                                </div>
                                <div className={`text-[11px] font-semibold mt-1 flex items-center gap-1 ${
                                  policy.withinBudget ? "text-emerald-400" : "text-amber-400"
                                }`}>
                                  {policy.withinBudget ? <CheckCircle className="w-3.5 h-3.5" /> : <AlertTriangle className="w-3.5 h-3.5" />}
                                  {policy.budgetComparisonText}
                                </div>
                              </div>

                              <div className="pt-2 border-t border-border flex items-center gap-2">
                                <button
                                  onClick={() => {
                                    setSelectedPolicyDetail(policy);
                                    setActiveResultsTab("q2_limitations");
                                  }}
                                  className="flex-1 py-2 px-3 rounded-lg bg-primary/10 hover:bg-primary/20 text-primary border border-primary/20 font-semibold text-xs text-center transition-colors"
                                >
                                  Inspect Limitations
                                </button>
                                <button
                                  onClick={() => {
                                    setAuditingPolicy(policy);
                                    setShowProvenanceModal(true);
                                  }}
                                  className="py-2 px-3 rounded-lg bg-card border border-border hover:bg-card/80 text-muted-foreground hover:text-foreground font-semibold text-xs flex items-center gap-1.5 transition-colors"
                                  title="Audit official filing, UIN & data provenance"
                                >
                                  <Shield className="w-3.5 h-3.5 text-emerald-400" />
                                  <span>Audit</span>
                                </button>
                                <button
                                  onClick={() => toggleCompare(policy.policyId)}
                                  className={`p-2 rounded-lg border text-xs transition-colors ${
                                    isComparing
                                      ? "bg-emerald-500 text-white border-emerald-500"
                                      : "border-border hover:bg-card text-muted-foreground"
                                  }`}
                                  title="Add to side-by-side comparison"
                                >
                                  <Layers className="w-4 h-4" />
                                </button>
                              </div>
                            </div>
                          </div>

                          {/* 3 Core Decision Questions Breakdown */}
                          <div className="mt-5 pt-4 border-t border-border/80">
                            <div className="flex items-center justify-between mb-3">
                              <div className="flex items-center gap-2">
                                <Sparkles className="w-4 h-4 text-emerald-400" />
                                <span className="text-xs font-bold uppercase tracking-wider text-foreground">
                                  Recommendation Decision Breakdown: 3 Core Questions
                                </span>
                              </div>
                              <button
                                onClick={() => {
                                  setAuditingPolicy(policy);
                                  setShowProvenanceModal(true);
                                }}
                                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/20 transition-colors"
                              >
                                <FileText className="w-3 h-3" />
                                <span>Inspect Provenance & Audit</span>
                              </button>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                              {/* Question 1: Why Recommended */}
                              <div className="p-3.5 rounded-xl bg-card/60 border border-border/80 flex flex-col justify-between">
                                <div>
                                  <div className="flex items-center gap-1.5 text-emerald-400 text-xs font-bold mb-1.5">
                                    <Award className="w-3.5 h-3.5 flex-shrink-0" />
                                    <span>1. Why Recommended?</span>
                                  </div>
                                  <p className="text-xs text-muted-foreground leading-relaxed">
                                    {policy.threeQuestionAnswers?.whyRecommended}
                                  </p>
                                </div>
                                <div className="mt-3 text-[10px] text-emerald-400/90 font-medium">
                                  Algorithmic 4-Pillar Score: {Math.round(policy.overallMatchScore)}/100
                                </div>
                              </div>

                              {/* Question 2: Verified Facts Supporting */}
                              <div className="p-3.5 rounded-xl bg-emerald-500/5 border border-emerald-500/20 flex flex-col justify-between">
                                <div>
                                  <div className="flex items-center gap-1.5 text-emerald-300 text-xs font-bold mb-1.5">
                                    <Shield className="w-3.5 h-3.5 flex-shrink-0" />
                                    <span>2. Verified Contractual Facts</span>
                                  </div>
                                  <ul className="space-y-1.5 text-xs text-foreground/90">
                                    {policy.threeQuestionAnswers?.verifiedFactsSupporting?.map((fact, fIdx) => (
                                      <li key={fIdx} className="flex items-start gap-1.5 text-[11px]">
                                        <Check className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0 mt-0.5" />
                                        <span>{fact}</span>
                                      </li>
                                    ))}
                                  </ul>
                                </div>
                                <div className="mt-3 text-[10px] text-emerald-400/80 font-mono">
                                  Canonical UIN: {policy.irdaUin} {policy.uinRevision ? `(${policy.uinRevision})` : ""}
                                </div>
                              </div>

                              {/* Question 3: Estimates & Assumptions */}
                              <div className="p-3.5 rounded-xl bg-amber-500/5 border border-amber-500/20 flex flex-col justify-between">
                                <div>
                                  <div className="flex items-center gap-1.5 text-amber-300 text-xs font-bold mb-1.5">
                                    <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0" />
                                    <span>3. Modeled Estimates (Not Guarantees)</span>
                                  </div>
                                  <ul className="space-y-1.5 text-xs text-muted-foreground">
                                    {policy.threeQuestionAnswers?.estimatesAndAssumptions?.map((est, eIdx) => (
                                      <li key={eIdx} className="flex items-start gap-1.5 text-[11px]">
                                        <span className="text-amber-400 flex-shrink-0 mt-0.5 font-bold">•</span>
                                        <span>{est}</span>
                                      </li>
                                    ))}
                                  </ul>
                                </div>
                                <div className="mt-3 text-[10px] text-amber-400/80 font-medium">
                                  Non-binding quote • Tele-underwriting required
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* ================================================================= */}
              {/* TAB 2: QUESTION 2 — LIMITATIONS & OUT-OF-POCKET RISKS */}
              {/* ================================================================= */}
              {activeResultsTab === "q2_limitations" && (
                <div className="space-y-6">
                  {/* Policy Switcher */}
                  <div className="flex items-center gap-3 overflow-x-auto pb-2">
                    <span className="text-xs font-semibold text-muted-foreground flex-shrink-0">Auditing Policy:</span>
                    {sortedPolicies.map((p) => {
                      const isSelected = selectedPolicyDetail?.policyId === p.policyId;
                      return (
                        <button
                          key={p.policyId}
                          onClick={() => setSelectedPolicyDetail(p)}
                          className={`px-3 py-1.5 rounded-xl text-xs font-semibold border flex-shrink-0 transition-all ${
                            isSelected
                              ? "bg-emerald-500/20 border-emerald-500 text-emerald-300 shadow-sm"
                              : "bg-card border-border text-muted-foreground hover:text-foreground"
                          }`}
                        >
                          #{p.rank} {p.policyName}
                        </button>
                      );
                    })}
                  </div>

                  {selectedPolicyDetail && (
                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                      {/* Left 2 Cols: The 4 Key Limitation Checks */}
                      <div className="lg:col-span-2 space-y-5">
                        {/* 1. Room Rent & Proportionate Deduction Alert */}
                        <div className={`glass rounded-2xl p-5 border ${
                          selectedPolicyDetail.limitations?.roomRentCapped
                            ? "border-rose-500/50 bg-rose-500/5"
                            : "border-emerald-500/40 bg-emerald-500/5"
                        }`}>
                          <div className="flex items-start justify-between">
                            <div className="flex items-center gap-2">
                              {selectedPolicyDetail.limitations?.roomRentCapped ? (
                                <AlertTriangle className="w-5 h-5 text-rose-400" />
                              ) : (
                                <CheckCircle className="w-5 h-5 text-emerald-400" />
                              )}
                              <h3 className="font-bold text-sm">Room Rent Limit & Proportionate Deductions</h3>
                            </div>
                            <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                              selectedPolicyDetail.limitations?.roomRentCapped
                                ? "bg-rose-500/20 text-rose-400"
                                : "bg-emerald-500/20 text-emerald-400"
                            }`}>
                              {selectedPolicyDetail.limitations?.roomRentCapped ? "Capped Trap" : "Zero Limit (Safe)"}
                            </span>
                          </div>

                          <div className="mt-3 text-xs space-y-2">
                            <div className="font-semibold text-foreground">
                              Contract Condition: {selectedPolicyDetail.limitations?.roomRentCondition}
                            </div>
                            <p className="text-muted-foreground leading-relaxed">
                              {selectedPolicyDetail.limitations?.roomRentWarning}
                            </p>
                          </div>
                        </div>

                        {/* 2. Co-Payment Audit */}
                        <div className="glass rounded-2xl p-5 border border-border space-y-3">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2">
                              <Percent className="w-4 h-4 text-primary" />
                              <h3 className="font-bold text-sm">Co-Payment Requirement</h3>
                            </div>
                            <span className="font-bold text-xs text-foreground">
                              {selectedPolicyDetail.limitations?.copayPercentage}% Co-pay
                            </span>
                          </div>

                          <p className="text-xs text-muted-foreground leading-relaxed">
                            {selectedPolicyDetail.limitations?.copayNote}
                          </p>
                        </div>

                        {/* 3. Disease Specific Sub-Limits */}
                        <div className="glass rounded-2xl p-5 border border-border space-y-3">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2">
                              <Shield className="w-4 h-4 text-amber-400" />
                              <h3 className="font-bold text-sm">Disease Sub-Limits Scrutiny</h3>
                            </div>
                            <span className="text-[11px] text-muted-foreground">
                              {selectedPolicyDetail.limitations?.sublimits?.length || 0} Conditions Listed
                            </span>
                          </div>

                          <div className="space-y-2">
                            {selectedPolicyDetail.limitations?.sublimits?.map((sub, sIdx) => (
                              <div
                                key={sIdx}
                                className="p-2.5 rounded-xl border border-border bg-card/40 flex items-center justify-between text-xs"
                              >
                                <div>
                                  <span className="font-semibold text-foreground">{sub.diseaseName}</span>
                                  <p className="text-[10px] text-muted-foreground">{sub.note}</p>
                                </div>
                                <span className={`font-bold px-2 py-0.5 rounded text-[11px] ${
                                  sub.sublimitAmount === -1
                                    ? "bg-emerald-500/10 text-emerald-400"
                                    : "bg-amber-500/10 text-amber-300"
                                }`}>
                                  {sub.formattedLimit}
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>

                        {/* 4. Waiting Periods Clock */}
                        <div className="glass rounded-2xl p-5 border border-border space-y-3">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2">
                              <Clock className="w-4 h-4 text-teal-400" />
                              <h3 className="font-bold text-sm">Waiting Period Audit</h3>
                            </div>
                            <span className="text-xs font-semibold text-teal-400">
                              {selectedPolicyDetail.limitations?.pedStatusForUser}
                            </span>
                          </div>

                          <div className="grid grid-cols-3 gap-3 text-center text-xs">
                            <div className="p-3 rounded-xl bg-card border border-border">
                              <div className="text-[10px] text-muted-foreground uppercase">Initial Illness</div>
                              <div className="text-base font-bold text-foreground mt-1">
                                {selectedPolicyDetail.limitations?.initialWaitingDays} Days
                              </div>
                            </div>

                            <div className="p-3 rounded-xl bg-card border border-border">
                              <div className="text-[10px] text-muted-foreground uppercase">Specific Ailments</div>
                              <div className="text-base font-bold text-foreground mt-1">
                                {selectedPolicyDetail.limitations?.specificIllnessWaitingMonths} Months
                              </div>
                            </div>

                            <div className="p-3 rounded-xl bg-card border border-border">
                              <div className="text-[10px] text-muted-foreground uppercase">Pre-Existing (PED)</div>
                              <div className="text-base font-bold text-foreground mt-1">
                                {selectedPolicyDetail.limitations?.pedWaitingMonths} Months
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Right Col: Interactive Out-of-Pocket Hospitalization Simulator */}
                      <div className="space-y-5">
                        <div className="glass rounded-2xl p-5 border border-border space-y-4 sticky top-24">
                          <div className="flex items-center justify-between">
                            <div>
                              <div className="flex items-center gap-2">
                                <Activity className="w-4 h-4 text-emerald-400" />
                                <h3 className="font-bold text-sm text-foreground">Out-of-Pocket Hospital Bill Simulation</h3>
                              </div>
                              <span className="inline-block mt-1 px-1.5 py-0.5 rounded text-[9px] font-bold bg-purple-500/10 text-purple-400 border border-purple-500/20 uppercase tracking-tight">
                                Hypothetical Scenario Estimate
                              </span>
                            </div>
                            <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                              selectedPolicyDetail.outOfPocketRisk?.riskLevel === "LOW"
                                ? "bg-emerald-500/20 text-emerald-400"
                                : selectedPolicyDetail.outOfPocketRisk?.riskLevel === "MODERATE"
                                ? "bg-amber-500/20 text-amber-300"
                                : "bg-rose-500/20 text-rose-400"
                            }`}>
                              {selectedPolicyDetail.outOfPocketRisk?.riskLevel} Risk
                            </span>
                          </div>

                          <p className="text-xs text-muted-foreground leading-relaxed">
                            {selectedPolicyDetail.outOfPocketRisk?.summary}
                          </p>

                          {/* Simulation Card */}
                          <div className="p-4 rounded-xl bg-card border border-border space-y-3">
                            <div className="flex justify-between text-xs">
                              <span className="text-muted-foreground">Sample Hospital Bill:</span>
                              <span className="font-bold text-foreground">
                                ₹{selectedPolicyDetail.outOfPocketRisk?.sampleScenarioBill?.toLocaleString("en-IN")}
                              </span>
                            </div>

                            <div className="space-y-1.5 pt-2 border-t border-border/80">
                              <div className="flex justify-between text-xs text-emerald-400">
                                <span>Estimated Insurer Payout:</span>
                                <span className="font-bold">
                                  ₹{selectedPolicyDetail.outOfPocketRisk?.estimatedCoveredAmount?.toLocaleString("en-IN")}
                                </span>
                              </div>

                              <div className="flex justify-between text-xs text-rose-400">
                                <span>Your Out-of-Pocket Cost:</span>
                                <span className="font-bold">
                                  ₹{selectedPolicyDetail.outOfPocketRisk?.estimatedOutofPocketCost?.toLocaleString("en-IN")}
                                </span>
                              </div>
                            </div>

                            {/* Split bar */}
                            <div className="w-full bg-rose-500/30 h-3 rounded-full overflow-hidden flex">
                              <div
                                className="bg-emerald-500 h-full"
                                style={{
                                  width: `${100 - (selectedPolicyDetail.outOfPocketRisk?.estimatedExposurePercentage ?? 5)}%`,
                                }}
                              />
                            </div>
                            <div className="flex justify-between text-[10px] text-muted-foreground">
                              <span>Policy Pays: {100 - (selectedPolicyDetail.outOfPocketRisk?.estimatedExposurePercentage ?? 5)}%</span>
                              <span>You Pay: {selectedPolicyDetail.outOfPocketRisk?.estimatedExposurePercentage ?? 5}%</span>
                            </div>

                            <div className="pt-2 text-[10px] text-muted-foreground/80 leading-normal border-t border-border/60">
                              ℹ️ Mathematical scenario applying policy contract clauses (room-rent limits, copays, sublimits). Actual deductions depend on hospital billing tariffs and non-medical consumables.
                            </div>
                          </div>

                          {/* Risk Exposure Factors */}
                          <div className="space-y-2 pt-2">
                            <span className="text-xs font-semibold text-muted-foreground block">
                              Potential Out-of-Pocket Triggers:
                            </span>
                            <ul className="space-y-1 text-xs text-muted-foreground">
                              {selectedPolicyDetail.outOfPocketRisk?.exposureFactors?.map((factor, fIdx) => (
                                <li key={fIdx} className="flex items-start gap-2">
                                  <span className="text-amber-400 mt-0.5">•</span>
                                  <span>{factor}</span>
                                </li>
                              ))}
                            </ul>
                          </div>

                          {/* Key Exclusions List */}
                          <div className="pt-3 border-t border-border space-y-2">
                            <span className="text-xs font-semibold text-muted-foreground block">
                              Standard Exclusions to Remember:
                            </span>
                            <div className="flex flex-wrap gap-1.5">
                              {selectedPolicyDetail.keyExclusions?.map((ex, exIdx) => (
                                <span
                                  key={exIdx}
                                  className="px-2 py-0.5 rounded text-[10px] bg-muted text-muted-foreground"
                                >
                                  {ex}
                                </span>
                              ))}
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* ================================================================= */}
              {/* TAB 3: QUESTION 3 — OFFICIAL POLICY DOCUMENT EVIDENCE */}
              {/* ================================================================= */}
              {activeResultsTab === "q3_evidence" && (
                <div className="space-y-6">
                  {/* Policy Switcher & Tag Filters */}
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div className="flex items-center gap-2 overflow-x-auto pb-1">
                      <span className="text-xs font-semibold text-muted-foreground flex-shrink-0">Document:</span>
                      {sortedPolicies.map((p) => {
                        const isSelected = selectedPolicyDetail?.policyId === p.policyId;
                        return (
                          <button
                            key={p.policyId}
                            onClick={() => setSelectedPolicyDetail(p)}
                            className={`px-3 py-1.5 rounded-xl text-xs font-semibold border flex-shrink-0 transition-all ${
                              isSelected
                                ? "bg-emerald-500/20 border-emerald-500 text-emerald-300"
                                : "bg-card border-border text-muted-foreground"
                            }`}
                          >
                            {p.policyName}
                          </button>
                        );
                      })}
                    </div>

                    <div className="flex items-center gap-1.5">
                      {["ALL", "ROOM_RENT", "PED", "RESTORATION", "COPAY", "SUBLIMIT"].map((tag) => (
                        <button
                          key={tag}
                          onClick={() => setEvidenceFilter(tag)}
                          className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition-colors ${
                            evidenceFilter === tag
                              ? "bg-primary text-primary-foreground border-primary"
                              : "bg-card border-border text-muted-foreground"
                          }`}
                        >
                          {tag}
                        </button>
                      ))}
                    </div>
                  </div>

                  {selectedPolicyDetail && (
                    <div className="space-y-4">
                      <div className="p-4 rounded-xl bg-card border border-border flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs">
                        <div className="flex items-center gap-3">
                          <FileText className="w-5 h-5 text-primary flex-shrink-0" />
                          <div>
                            <div className="font-bold text-foreground text-sm">
                              {selectedPolicyDetail.policyWordingDocument}
                            </div>
                            <div className="text-muted-foreground text-[11px] mt-0.5">
                              Official Approved Document • Canonical IRDAI UIN: <span className="font-mono text-foreground font-semibold">{selectedPolicyDetail.irdaUin}</span> {selectedPolicyDetail.uinRevision ? `(${selectedPolicyDetail.uinRevision})` : ""}
                            </div>
                            <div className="text-[10px] text-muted-foreground/80 mt-0.5">
                              Filing Source: {selectedPolicyDetail.regulatoryFilingSource || "IRDAI Public Disclosure Portal"} • Audited: {selectedPolicyDetail.lastAuditedDate || "2024-03-31"}
                            </div>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 flex-shrink-0">
                          <button
                            onClick={() => {
                              setAuditingPolicy(selectedPolicyDetail);
                              setShowProvenanceModal(true);
                            }}
                            className="px-3 py-1.5 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/20 text-xs font-semibold flex items-center gap-1.5 transition-colors"
                          >
                            <Shield className="w-3.5 h-3.5" />
                            <span>Audit Provenance</span>
                          </button>
                          <span className="px-3 py-1.5 rounded-lg text-xs font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center gap-1">
                            <CheckCircle className="w-3.5 h-3.5" />
                            Verified Contract
                          </span>
                        </div>
                      </div>

                      {/* Evidence Item Cards */}
                      <div className="grid grid-cols-1 gap-4">
                        {selectedPolicyDetail.supportingEvidence
                          ?.filter((item) => evidenceFilter === "ALL" || item.tag === evidenceFilter)
                          .map((item, eIdx) => (
                            <div
                              key={eIdx}
                              className="glass rounded-2xl p-5 border border-border space-y-3 relative overflow-hidden"
                            >
                              <div className="flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-muted text-muted-foreground uppercase">
                                    {item.tag}
                                  </span>
                                  <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 uppercase">
                                    {item.evidenceConfidence || "VERIFIED_CONTRACTUAL_SPECIFICATION"}
                                  </span>
                                  <h4 className="font-bold text-sm text-foreground">{item.clauseName}</h4>
                                </div>
                                <div className="text-right">
                                  <span className="font-mono text-xs text-primary font-semibold block">
                                    {item.sectionReference}
                                  </span>
                                  {item.sourceDocumentVersion && (
                                    <span className="text-[10px] text-muted-foreground font-mono">
                                      {item.sourceDocumentVersion}
                                    </span>
                                  )}
                                </div>
                              </div>

                              {/* Verbatim quote */}
                              <div className="p-3.5 rounded-xl bg-slate-950/60 border border-border/80 text-xs font-mono text-slate-300 leading-relaxed border-l-4 border-l-emerald-500">
                                &quot;{item.verbatimText}&quot;
                              </div>

                              {/* Plain Language Legal Fact */}
                              <div className="flex items-start gap-2 text-xs">
                                <CheckCircle className="w-4 h-4 text-emerald-400 flex-shrink-0 mt-0.5" />
                                <div>
                                  <span className="font-bold text-foreground">Plain-Language Verdict: </span>
                                  <span className="text-muted-foreground">{item.verifiedFact}</span>
                                </div>
                              </div>
                            </div>
                          ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* ================================================================= */}
              {/* TAB 4: INSURER SCORECARDS & IRDAI METRICS */}
              {/* ================================================================= */}
              {activeResultsTab === "metrics" && (
                <div className="space-y-6">
                  {/* Dated Evidence Notice */}
                  <div className="p-4 rounded-xl bg-teal-500/5 border border-teal-500/20 text-xs flex items-start gap-3">
                    <Info className="w-4 h-4 text-teal-400 flex-shrink-0 mt-0.5" />
                    <div>
                      <span className="font-bold text-teal-300">Dated Regulatory Disclosures Notice:</span>
                      <p className="text-muted-foreground mt-0.5 leading-relaxed text-[11px]">
                        Claim Settlement Ratios (CSR) and Incurred Claims Ratios (ICR) are derived from the official <strong>IRDAI Annual Report FY 2022-23 (Table 4.5)</strong> using uniform statutory definitions (Claims Settled / Total Claims Payable within reporting period). Cashless network figures reflect published provider directories as of FY 2023-24 Q4. They are historical evidence, not timeless guarantees.
                      </p>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                    {sortedPolicies.map((p) => {
                      const m = p.insurerMetrics;
                      return (
                        <div key={p.policyId} className="glass rounded-2xl p-5 border border-border space-y-4">
                          <div className="flex items-center justify-between">
                            <div>
                              <div className="font-bold text-sm text-foreground">{p.provider}</div>
                              <div className="text-xs text-muted-foreground">{p.policyName}</div>
                              <span className="inline-block mt-1 px-1.5 py-0.5 rounded text-[9px] font-bold bg-teal-500/10 text-teal-300 border border-teal-500/20 uppercase tracking-tight">
                                Dated Regulatory Disclosure
                              </span>
                            </div>
                            <div className="text-right">
                              <span className="text-xs font-bold text-emerald-400 block">Rank #{p.rank}</span>
                              <button
                                onClick={() => {
                                  setAuditingPolicy(p);
                                  setShowProvenanceModal(true);
                                }}
                                className="mt-1 text-[11px] font-semibold text-primary hover:underline inline-flex items-center gap-1"
                              >
                                <Shield className="w-3 h-3" />
                                <span>Audit</span>
                              </button>
                            </div>
                          </div>

                          <div className="space-y-2 pt-2 border-t border-border text-xs">
                            <div className="py-1 border-b border-border/60">
                              <div className="flex justify-between">
                                <span className="text-muted-foreground">IRDAI Settlement (CSR):</span>
                                <span className="font-bold text-foreground">{m?.claimSettlementRatio}%</span>
                              </div>
                              <div className="text-[10px] text-muted-foreground/80 mt-0.5 font-mono">
                                Period: {m?.csrReportingPeriod || "IRDAI FY 2022-23 (Table 4.5)"}
                              </div>
                            </div>

                            <div className="py-1 border-b border-border/60">
                              <div className="flex justify-between">
                                <span className="text-muted-foreground">Incurred Claims (ICR):</span>
                                <span className="font-bold text-foreground">{m?.incurredClaimsRatio}%</span>
                              </div>
                              <div className="text-[10px] text-muted-foreground/80 mt-0.5 font-mono">
                                IRDAI FY 2022-23 Statutory Metric
                              </div>
                            </div>

                            <div className="py-1 border-b border-border/60">
                              <div className="flex justify-between">
                                <span className="text-muted-foreground">Cashless Hospitals:</span>
                                <span className="font-bold text-foreground">{m?.cashlessHospitalsCount?.toLocaleString("en-IN")}+</span>
                              </div>
                              <div className="text-[10px] text-muted-foreground/80 mt-0.5 font-mono">
                                As of: {m?.networkAsOfDate || "FY 2023-24 Q4"} • {m?.networkSource || "Directory"}
                              </div>
                            </div>

                            <div className="flex justify-between py-1 border-b border-border/60">
                              <span className="text-muted-foreground">Grievance Resolution:</span>
                              <span className="font-bold text-foreground">{m?.avgGrievanceResolutionDays} Days avg</span>
                            </div>

                            <div className="flex justify-between py-1">
                              <span className="text-muted-foreground">Approval Speed:</span>
                              <span className="font-bold text-emerald-400">{m?.claimPaymentSpeed}</span>
                            </div>
                          </div>

                          <div className="pt-2">
                            <span className="text-[10px] uppercase font-bold text-muted-foreground block mb-1">
                              Top Partner Networks:
                            </span>
                            <div className="flex flex-wrap gap-1">
                              {m?.topHospitalNetworks?.map((h, hIdx) => (
                                <span key={hIdx} className="px-2 py-0.5 rounded text-[10px] bg-card border border-border text-muted-foreground">
                                  {h}
                                </span>
                              ))}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* ========================================================================= */}
        {/* MODAL: SIDE-BY-SIDE POLICY COMPARISON */}
        {/* ========================================================================= */}
        {showComparisonModal && (
          <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
            <div className="glass rounded-2xl max-w-5xl w-full border border-border p-6 space-y-6 max-h-[90vh] overflow-y-auto">
              <div className="flex items-center justify-between border-b border-border pb-4">
                <div className="flex items-center gap-2">
                  <Layers className="w-5 h-5 text-emerald-400" />
                  <h3 className="text-lg font-bold">Side-by-Side Policy Comparison</h3>
                </div>
                <button
                  onClick={() => setShowComparisonModal(false)}
                  className="p-1 rounded-lg hover:bg-card text-muted-foreground hover:text-foreground"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Compare Columns */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {sortedPolicies
                  .filter((p) => comparingPolicyIds.includes(p.policyId))
                  .map((policy) => (
                    <div key={policy.policyId} className="p-4 rounded-xl bg-card border border-border space-y-4 text-xs">
                      <div className="border-b border-border pb-3">
                        <div className="font-bold text-sm text-foreground">{policy.policyName}</div>
                        <div className="text-muted-foreground">{policy.provider}</div>
                        <div className="flex flex-wrap items-center gap-1.5 mt-1">
                          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-muted text-foreground">
                            UIN: {policy.irdaUin}
                          </span>
                          {policy.uinRevision && (
                            <span className="text-[10px] font-mono text-muted-foreground">
                              ({policy.uinRevision})
                            </span>
                          )}
                        </div>
                        <div className="mt-1">
                          <span className="px-1.5 py-0.2 rounded text-[9px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 uppercase tracking-tight">
                            {policy.verificationStatus || "VERIFIED_OFFICIAL_CATALOG"}
                          </span>
                        </div>
                        <div className="text-xl font-black text-emerald-400 mt-2">
                          {policy.formattedAnnualPremium}
                        </div>
                        <div className="text-[10px] text-amber-400/90 font-medium">
                          Modeled Actuarial Estimate • Non-Binding
                        </div>
                        <div className="text-[11px] text-muted-foreground mt-0.5">{policy.budgetComparisonText}</div>
                      </div>

                      <div className="space-y-2">
                        <div>
                          <span className="text-muted-foreground block text-[10px] uppercase font-bold">Match Score</span>
                          <span className="font-bold text-foreground text-sm">{Math.round(policy.overallMatchScore)} / 100</span>
                        </div>

                        <div>
                          <span className="text-muted-foreground block text-[10px] uppercase font-bold">Room Rent Limit (Contract Fact)</span>
                          <span className={`font-semibold ${policy.limitations?.roomRentCapped ? "text-rose-400" : "text-emerald-400"}`}>
                            {policy.limitations?.roomRentCondition}
                          </span>
                        </div>

                        <div>
                          <span className="text-muted-foreground block text-[10px] uppercase font-bold">Co-Payment (Contract Fact)</span>
                          <span className="font-semibold text-foreground">{policy.limitations?.copayPercentage}%</span>
                        </div>

                        <div>
                          <span className="text-muted-foreground block text-[10px] uppercase font-bold">Waiting Period (PED) (Contract Fact)</span>
                          <span className="font-semibold text-foreground">{policy.limitations?.pedWaitingMonths} Months</span>
                        </div>

                        <div>
                          <span className="text-muted-foreground block text-[10px] uppercase font-bold">Out-of-Pocket Risk (Scenario)</span>
                          <span className="font-semibold text-emerald-400">{policy.outOfPocketRisk?.riskLevel}</span>
                        </div>

                        <div>
                          <span className="text-muted-foreground block text-[10px] uppercase font-bold">IRDAI Settlement CSR (Dated Disclosure)</span>
                          <span className="font-semibold text-foreground">{policy.insurerMetrics?.claimSettlementRatio}%</span>
                          <span className="block text-[10px] text-muted-foreground font-mono mt-0.5">
                            {policy.insurerMetrics?.csrReportingPeriod || "IRDAI FY 2022-23"}
                          </span>
                        </div>
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* MODAL: PAST RECOMMENDATION HISTORY */}
        {/* ========================================================================= */}
        {showHistoryModal && (
          <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="glass rounded-2xl max-w-xl w-full border border-border p-6 space-y-5 max-h-[85vh] overflow-y-auto">
              <div className="flex items-center justify-between border-b border-border pb-3">
                <div className="flex items-center gap-2">
                  <History className="w-5 h-5 text-primary" />
                  <h3 className="text-base font-bold">Past Recommendation Runs</h3>
                </div>
                <button
                  onClick={() => setShowHistoryModal(false)}
                  className="p-1 rounded-lg hover:bg-card text-muted-foreground hover:text-foreground"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {isLoadingHistory ? (
                <div className="py-8 text-center text-xs text-muted-foreground">Loading history...</div>
              ) : historyItems.length === 0 ? (
                <div className="py-8 text-center text-xs text-muted-foreground">
                  No past recommendation runs found yet. Complete a questionnaire to save results.
                </div>
              ) : (
                <div className="space-y-3">
                  {historyItems.map((item) => (
                    <div
                      key={item.id}
                      className="p-4 rounded-xl border border-border bg-card/50 hover:bg-card transition-all flex items-center justify-between"
                    >
                      <div className="text-xs space-y-1">
                        <div className="font-bold text-foreground">
                          {item.rankedPolicies?.length || 0} Policies Evaluated
                        </div>
                        <div className="text-[11px] text-muted-foreground">
                          Top Match: {item.rankedPolicies?.[0]?.policyName || "N/A"}
                        </div>
                        <div className="text-[10px] text-muted-foreground/70">
                          {new Date(item.createdAt).toLocaleString()}
                        </div>
                      </div>

                      <button
                        onClick={() => loadHistoricalResult(item)}
                        className="px-3 py-1.5 rounded-lg bg-primary/10 hover:bg-primary/20 text-primary border border-primary/20 text-xs font-semibold"
                      >
                        Load Run
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* MODAL: DATA PROVENANCE & REGULATORY FILING AUDIT */}
        {/* ========================================================================= */}
        {showProvenanceModal && auditingPolicy && (
          <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
            <div className="glass rounded-2xl max-w-3xl w-full border border-border p-6 space-y-6 max-h-[90vh] overflow-y-auto">
              <div className="flex items-center justify-between border-b border-border pb-4">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
                    <Shield className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-base font-bold text-foreground">
                        Data Provenance & Regulatory Filing Audit
                      </h3>
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                        {auditingPolicy.verificationStatus || "VERIFIED_OFFICIAL_CATALOG"}
                      </span>
                    </div>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {auditingPolicy.policyName} • {auditingPolicy.provider}
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => setShowProvenanceModal(false)}
                  className="p-1 rounded-lg hover:bg-card text-muted-foreground hover:text-foreground"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Product Identity Box */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 p-4 rounded-xl bg-card border border-border text-xs">
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-bold">Canonical IRDAI UIN</span>
                  <span className="font-mono font-bold text-foreground text-sm">{auditingPolicy.irdaUin}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-bold">Product Revision Version</span>
                  <span className="font-mono font-bold text-primary text-sm">{auditingPolicy.uinRevision || "Latest Approved Filing"}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-bold">Official Filing Source</span>
                  <span className="font-semibold text-foreground">{auditingPolicy.regulatoryFilingSource || "IRDAI Public Disclosure Portal / Product Registry"}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-bold">Independent Audit Date</span>
                  <span className="font-semibold text-emerald-400">{auditingPolicy.lastAuditedDate || "2024-03-31"}</span>
                </div>
                <div className="md:col-span-2 pt-2 border-t border-border flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
                  <div className="text-muted-foreground">
                    Official Document on File: <strong className="text-foreground">{auditingPolicy.policyWordingDocument}</strong>
                  </div>
                  {auditingPolicy.officialDocumentUrl && (
                    <a
                      href={auditingPolicy.officialDocumentUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/20 font-semibold text-xs"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                      <span>IRDAI Portal</span>
                    </a>
                  )}
                </div>
              </div>

              {/* 4-Tier Data Classification Breakdown */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                  Data Classification & Governance Framework
                </h4>

                <div className="space-y-3 text-xs">
                  {/* Tier 1: Verified Contractual Facts */}
                  <div className="p-3.5 rounded-xl border border-emerald-500/30 bg-emerald-500/5 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 font-bold text-emerald-400">
                        <CheckCircle className="w-4 h-4" />
                        <span>1. Verified Contractual Specifications (Binding)</span>
                      </div>
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-300">
                        VERIFIED_CONTRACTUAL_SPECIFICATION
                      </span>
                    </div>
                    <p className="text-muted-foreground leading-relaxed text-[11px]">
                      Room rent capping terms, proportionate deduction rules, pre-existing disease (PED) waiting periods ({auditingPolicy.limitations?.pedWaitingMonths} months), and restoration benefits are extracted verbatim from approved policy wordings filed under UIN {auditingPolicy.irdaUin}. Legally binding on the insurer.
                    </p>
                  </div>

                  {/* Tier 2: Dated Regulatory Disclosures */}
                  <div className="p-3.5 rounded-xl border border-teal-500/30 bg-teal-500/5 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 font-bold text-teal-300">
                        <BarChart2 className="w-4 h-4" />
                        <span>2. Dated Regulatory Disclosures (Non-Timeless Evidence)</span>
                      </div>
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-teal-500/20 text-teal-300">
                        DATED_REGULATORY_DISCLOSURE
                      </span>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-[11px] text-muted-foreground">
                      <div>
                        <strong>Claim Settlement (CSR):</strong> {auditingPolicy.insurerMetrics?.claimSettlementRatio}%
                        <div className="text-[10px] text-teal-400/90">{auditingPolicy.insurerMetrics?.csrReportingPeriod || "IRDAI Annual Report FY 2022-23 (Table 4.5)"}</div>
                      </div>
                      <div>
                        <strong>Calculation Method:</strong>
                        <div className="text-[10px] text-teal-400/90">{auditingPolicy.insurerMetrics?.csrCalculationMethod || "Claims Settled / Total Claims Payable"}</div>
                      </div>
                      <div>
                        <strong>Hospital Network:</strong> {auditingPolicy.insurerMetrics?.cashlessHospitalsCount?.toLocaleString("en-IN")}+ Hospitals
                        <div className="text-[10px] text-teal-400/90">As of {auditingPolicy.insurerMetrics?.networkAsOfDate || "FY 2023-24 Q4"} • {auditingPolicy.insurerMetrics?.networkSource || "Provider Directory"}</div>
                      </div>
                      <div>
                        <strong>Incurred Claims (ICR):</strong> {auditingPolicy.insurerMetrics?.incurredClaimsRatio}%
                        <div className="text-[10px] text-teal-400/90">IRDAI FY 2022-23 Statutory Disclosure</div>
                      </div>
                    </div>
                  </div>

                  {/* Tier 3: Modeled Actuarial Estimates */}
                  <div className="p-3.5 rounded-xl border border-amber-500/30 bg-amber-500/5 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 font-bold text-amber-300">
                        <AlertTriangle className="w-4 h-4" />
                        <span>3. Modeled Actuarial Estimates (Non-Binding Quote)</span>
                      </div>
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300">
                        MODELED_ACTUARIAL_ESTIMATE
                      </span>
                    </div>
                    <p className="text-muted-foreground leading-relaxed text-[11px]">
                      The displayed premium ({auditingPolicy.formattedAnnualPremium}/year) is an indicative actuarial estimate computed across age bands, family member compositions, and IRDAI geographic pricing zones.
                      <strong> This is not a binding insurance quote.</strong> The insurer underwriting API is not live; final premiums depend on medical tele-underwriting, medical examination reports (MER), tobacco/BMI loading, and insurer underwriter approval.
                    </p>
                  </div>

                  {/* Tier 4: Hypothetical Hospitalization Simulation */}
                  <div className="p-3.5 rounded-xl border border-purple-500/30 bg-purple-500/5 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 font-bold text-purple-300">
                        <Activity className="w-4 h-4" />
                        <span>4. Hypothetical Hospitalization Simulation</span>
                      </div>
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-500/20 text-purple-300">
                        HYPOTHETICAL_SCENARIO_ESTIMATE
                      </span>
                    </div>
                    <p className="text-muted-foreground leading-relaxed text-[11px]">
                      The ₹3,00,000 scenario test mathematically applies this policy&apos;s contractual room-rent ceilings, copay rules, and sublimits to highlight potential proportionate deduction traps. Actual hospital claim settlement depends on individual hospital tariffs, itemized non-medical consumables, and claim assessor evaluation.
                    </p>
                  </div>
                </div>
              </div>

              {/* Three Questions Summary in Modal */}
              <div className="p-4 rounded-xl bg-card border border-border space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-2">
                  <Sparkles className="w-4 h-4 text-emerald-400" />
                  <span>Recommendation Rationale & Verification Audit</span>
                </h4>
                <div className="text-xs space-y-2.5">
                  <div>
                    <span className="font-bold text-emerald-400">1. Why was this policy recommended?</span>
                    <p className="text-muted-foreground mt-0.5">{auditingPolicy.threeQuestionAnswers?.whyRecommended}</p>
                  </div>
                  <div>
                    <span className="font-bold text-emerald-300">2. Which verified facts support that decision?</span>
                    <ul className="list-disc list-inside text-muted-foreground mt-0.5 space-y-0.5">
                      {auditingPolicy.threeQuestionAnswers?.verifiedFactsSupporting?.map((fact, idx) => (
                        <li key={idx}>{fact}</li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <span className="font-bold text-amber-300">3. Which parts are estimates rather than guarantees?</span>
                    <ul className="list-disc list-inside text-muted-foreground mt-0.5 space-y-0.5">
                      {auditingPolicy.threeQuestionAnswers?.estimatesAndAssumptions?.map((est, idx) => (
                        <li key={idx}>{est}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>

              <div className="flex justify-end pt-2">
                <button
                  onClick={() => setShowProvenanceModal(false)}
                  className="px-4 py-2 rounded-xl bg-card border border-border hover:bg-card/80 text-xs font-semibold text-foreground transition-colors"
                >
                  Close Audit View
                </button>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
