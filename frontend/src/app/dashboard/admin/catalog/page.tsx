"use client";

import React, { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  ShieldCheck,
  FileSearch,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Database,
  ExternalLink,
  Layers,
  Sparkles,
  Search,
  Filter,
  Eye,
  Check,
  X,
  FileText,
  Clock,
  Hash,
  Globe,
  UploadCloud,
  ChevronRight,
  Info,
  RotateCcw,
  TrendingDown,
  ShieldAlert,
  Scale,
  Award,
  Calculator,
  FileCheck,
  Shield,
  AlertCircle,
  Edit3,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import {
  api,
  PolicySource,
  DiscoveredDocument,
  CrawlSummary,
  DomainDiagnostics,
  CatalogStats,
  PolicyChangeImpactReport,
  FactDiff,
  RecommendationQualityReport,
  ScenarioEvaluationResult,
  PolicyDiscoveryRun,
  PolicyDiscoveryCandidate,
  PolicyDocumentRevision,
  ConflictQueueItem,
} from "@/lib/api";
import { useToast } from "@/context/ToastContext";

export default function AdminCatalogPage() {
  const toast = useToast();
  const [activeTab, setActiveTab] = useState<"drafts" | "sources" | "live" | "documents" | "benchmark" | "discovery" | "conflicts">("drafts");

  // State
  const [drafts, setDrafts] = useState<any[]>([]);
  const [sources, setSources] = useState<PolicySource[]>([]);
  const [conflicts, setConflicts] = useState<ConflictQueueItem[]>([]);
  const [selectedConflict, setSelectedConflict] = useState<ConflictQueueItem | null>(null);
  const [showCorrectionModal, setShowCorrectionModal] = useState(false);
  const [isAdjudicating, setIsAdjudicating] = useState(false);
  const [correctionForm, setCorrectionForm] = useState({
    correctedValue: "",
    correctedNumeric: 0,
    correctedUnit: "PERCENTAGE",
    correctedEvidenceRole: "OPERATIVE_CLAUSE",
    notes: "",
    approveAfterCorrection: true,
  });
  const [livePolicies, setLivePolicies] = useState<any[]>([]);
  const [documents, setDocuments] = useState<DiscoveredDocument[]>([]);
  const [stats, setStats] = useState<CatalogStats | null>(null);

  // Phase 5.6 Discovery State
  const [discoveryRuns, setDiscoveryRuns] = useState<PolicyDiscoveryRun[]>([]);
  const [discoveryCandidates, setDiscoveryCandidates] = useState<PolicyDiscoveryCandidate[]>([]);
  const [isTriggeringDiscovery, setIsTriggeringDiscovery] = useState(false);
  const [operatingCandidateId, setOperatingCandidateId] = useState<string | null>(null);
  const [selectedCandidate, setSelectedCandidate] = useState<PolicyDiscoveryCandidate | null>(null);
  const [candidateNotes, setCandidateNotes] = useState("");
  const [isPublishingCandidate, setIsPublishingCandidate] = useState(false);

  const [isLoading, setIsLoading] = useState(true);
  const [isCrawling, setIsCrawling] = useState(false);
  const [isRefreshingCache, setIsRefreshingCache] = useState(false);
  const [qualityReport, setQualityReport] = useState<RecommendationQualityReport | null>(null);
  const [isRunningBenchmark, setIsRunningBenchmark] = useState(false);
  const [latestCrawlSummary, setLatestCrawlSummary] = useState<any | null>(null);

  // Review Modal State
  const [selectedDraft, setSelectedDraft] = useState<any | null>(null);
  const [reviewerNotes, setReviewerNotes] = useState("");
  const [isSubmittingReview, setIsSubmittingReview] = useState(false);

  // New Source Modal State
  const [showAddSourceModal, setShowAddSourceModal] = useState(false);
  const [newSource, setNewSource] = useState<Partial<PolicySource>>({
    sourceName: "",
    provider: "",
    canonicalProductName: "",
    irdaUin: "",
    sourceUrl: "",
    documentType: "POLICY_WORDING",
    sourceAuthority: "OFFICIAL_INSURER_WEBSITE",
    pollFrequencyHours: 24,
  });

  // Manually Curated Official PDF Modal State
  const [showCuratedModal, setShowCuratedModal] = useState(false);
  const [isSubmittingCurated, setIsSubmittingCurated] = useState(false);
  const [authenticityFilter, setAuthenticityFilter] = useState<"ALL" | "VERIFIED" | "SYNTHETIC">("ALL");
  const [isRunningSmokeTest, setIsRunningSmokeTest] = useState(false);
  const [smokeTestResult, setSmokeTestResult] = useState<any | null>(null);
  const [curatedForm, setCuratedForm] = useState({
    sourceUrl: "",
    sourceName: "",
    provider: "",
    canonicalProductName: "",
    expectedUin: "",
    expectedRevision: "",
    curationNotes: "",
  });

  const loadData = async () => {
    setIsLoading(true);
    try {
      const [draftsRes, sourcesRes, liveRes, docsRes, statsRes, runsRes, candidatesRes, conflictsRes] = await Promise.all([
        api.getPolicyDrafts().catch(() => ({ data: [] })),
        api.getPolicySources().catch(() => ({ data: [] })),
        api.getCatalogPolicies("ALL").catch(() => ({ data: [] })),
        api.getDiscoveredDocuments().catch(() => ({ data: [] })),
        api.getCatalogStats().catch(() => ({ data: null })),
        api.getDiscoveryRuns().catch(() => ({ data: [] })),
        api.getDiscoveryCandidates().catch(() => ({ data: [] })),
        api.getConflictQueue().catch(() => ({ data: [] })),
      ]);

      setDrafts(draftsRes.data || []);
      setSources(sourcesRes.data || []);
      setLivePolicies(liveRes.data || []);
      setDocuments(docsRes.data || []);
      setDiscoveryRuns(runsRes.data || []);
      setDiscoveryCandidates(candidatesRes.data || []);
      setConflicts(conflictsRes.data || []);
      if (statsRes && statsRes.data) {
        setStats(statsRes.data);
      }
    } catch (err: any) {
      console.error("Failed to load catalog governance data:", err);
      toast.error("Could not load catalog governance records.", "Network Error");
    } finally {
      setIsLoading(false);
    }
  };

  const handleApproveConflict = async (item: ConflictQueueItem) => {
    setIsAdjudicating(true);
    try {
      await api.reviewExtraction(item.extractionId, {
        action: "APPROVE",
        notes: "Approved by reviewer via conflict resolution console",
      });
      toast.success(`Candidate value "${item.candidateValue}" approved for ${item.fieldName}.`, "Conflict Resolved");
      setConflicts(prev => prev.filter(c => c.extractionId !== item.extractionId));
    } catch (e: any) {
      toast.error(e.message || "Failed to approve extraction.", "Adjudication Error");
    } finally {
      setIsAdjudicating(false);
    }
  };

  const handleRejectConflict = async (item: ConflictQueueItem) => {
    setIsAdjudicating(true);
    try {
      await api.reviewExtraction(item.extractionId, {
        action: "REJECT",
        notes: "Rejected as unreliable/unverifiable by reviewer",
      });
      toast.success(`Extraction ${item.extractionId} marked as REJECTED.`, "Conflict Rejected");
      setConflicts(prev => prev.filter(c => c.extractionId !== item.extractionId));
    } catch (e: any) {
      toast.error(e.message || "Failed to reject extraction.", "Adjudication Error");
    } finally {
      setIsAdjudicating(false);
    }
  };

  const handleOpenCorrection = (item: ConflictQueueItem) => {
    setSelectedConflict(item);
    setCorrectionForm({
      correctedValue: item.candidateValue || "",
      correctedNumeric: item.numericValue || 0,
      correctedUnit: item.unit || "PERCENTAGE",
      correctedEvidenceRole: item.evidenceRole || "OPERATIVE_CLAUSE",
      notes: "Corrected based on statutory policy wording inspection",
      approveAfterCorrection: true,
    });
    setShowCorrectionModal(true);
  };

  const handleSubmitCorrection = async () => {
    if (!selectedConflict) return;
    setIsAdjudicating(true);
    try {
      await api.correctExtraction(selectedConflict.extractionId, {
        correctedValue: correctionForm.correctedValue,
        correctedNumeric: Number(correctionForm.correctedNumeric),
        correctedUnit: correctionForm.correctedUnit,
        correctedEvidenceRole: correctionForm.correctedEvidenceRole,
        notes: correctionForm.notes,
        approveAfterCorrection: correctionForm.approveAfterCorrection,
      });
      toast.success(`Attribute ${selectedConflict.fieldName} corrected & resolved.`, "Correction Applied");
      setConflicts(prev => prev.filter(c => c.extractionId !== selectedConflict.extractionId));
      setShowCorrectionModal(false);
      setSelectedConflict(null);
    } catch (e: any) {
      toast.error(e.message || "Failed to submit correction.", "Correction Error");
    } finally {
      setIsAdjudicating(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleTriggerDiscovery = async () => {
    setIsTriggeringDiscovery(true);
    try {
      const res = await api.triggerPolicyDiscovery();
      toast.success(
        `Discovery Run complete. Scanned: ${res.data?.sourcesScanned}, Found: ${res.data?.candidatesDiscovered}, New: ${res.data?.candidatesNew}, Acquired: ${res.data?.candidatesAcquired}`,
        "Discovery Completed"
      );
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to trigger automated discovery", "Discovery Error");
    } finally {
      setIsTriggeringDiscovery(false);
    }
  };

  const handleAcquireCandidate = async (id: string) => {
    setOperatingCandidateId(id);
    try {
      const res = await api.acquireDiscoveryCandidate(id);
      if (res.data?.lifecycleStage === "ACQUIRED") {
        toast.success(`Document acquired. SHA-256: ${res.data?.verifiedSha256?.substring(0, 16)}...`, "Document Acquired");
      } else {
        toast.error(res.data?.lastErrorMessage || "Acquisition unsuccessful", "Acquisition Incomplete");
      }
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to acquire candidate document", "Acquisition Error");
    } finally {
      setOperatingCandidateId(null);
    }
  };

  const handleExtractCandidate = async (id: string) => {
    setOperatingCandidateId(id);
    try {
      const res = await api.extractDiscoveryCandidate(id);
      toast.success(`Contract terms extracted. Status: ${res.data?.lifecycleStage}`, "Extraction Succeeded");
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to extract candidate", "Extraction Error");
    } finally {
      setOperatingCandidateId(null);
    }
  };

  const handlePublishCandidate = async () => {
    if (!selectedCandidate) return;
    setIsPublishingCandidate(true);
    try {
      const res = await api.publishDiscoveryCandidate(
        selectedCandidate.id,
        candidateNotes || "Verified and published via operator audit."
      );
      toast.success(
        `Policy ${res.data?.productName} published to live catalogue! Now active in recommendations & comparison.`,
        "Policy Published"
      );
      setSelectedCandidate(null);
      setCandidateNotes("");
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to publish candidate", "Publication Gated");
    } finally {
      setIsPublishingCandidate(false);
    }
  };

  const handleCrawlAll = async () => {
    setIsCrawling(true);
    try {
      const res = await api.crawlAllPolicySources(false, true);
      if (res.data) {
        const d = res.data;
        setLatestCrawlSummary(d);
        const modeBadge = d.discoveryMode === "LIVE_DISCOVERY"
          ? "🟢 [Live Discovery]"
          : d.discoveryMode === "LIVE_DISCOVERY_EMPTY"
          ? "🔵 [Live Scan Empty]"
          : "⚠️ [Catalog Fallback]";
        const discMsg = d.candidateSourcesRegistered && d.candidateSourcesRegistered > 0
          ? `${modeBadge} Discovered & registered ${d.candidateSourcesRegistered} new sources! `
          : `${modeBadge} `;
        toast.success(
          `${discMsg}Crawled ${d.sourcesScanned || d.checkedSources} sources: ${d.newDocumentsExtracted} extracted/staged, ${d.unchangedDocumentsSkipped} unchanged (SHA-256 deduplicated), ${d.failedSources} failed.`,
          "Discovery Crawl Finished"
        );
      }
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to trigger discovery crawl", "Crawl Failed");
    } finally {
      setIsCrawling(false);
    }
  };

  const handleCrawlSingleSource = async (sourceId: string) => {
    try {
      const res = await api.crawlPolicySource(sourceId, false);
      toast.success(`Result: ${res.data?.outcome}`, "Source Crawled");
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to crawl source", "Crawl Error");
    }
  };

  const handleRefreshCache = async () => {
    setIsRefreshingCache(true);
    try {
      const res = await api.refreshPolicyCache();
      toast.success(
        `Active verified catalog atomically refreshed (${res.data?.activeVerifiedPoliciesCount} live policies).`,
        "Cache Invalidation Complete"
      );
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to refresh policy cache", "Cache Refresh Failed");
    } finally {
      setIsRefreshingCache(false);
    }
  };

  const handleRunBenchmark = async () => {
    setIsRunningBenchmark(true);
    try {
      const res = await api.getQualityBenchmarkReport();
      if (res.data) {
        setQualityReport(res.data);
        toast.success(
          `Evaluated ${res.data.totalBenchmarkScenarios} gold-standard archetypes with ${res.data.overallQualityScore}% overall quality score!`,
          "Benchmark Evaluation Complete"
        );
      }
    } catch (err: any) {
      toast.error(err.message || "Failed to execute quality evaluation benchmark suite", "Benchmark Error");
    } finally {
      setIsRunningBenchmark(false);
    }
  };

  const handleApproveDraft = async (draftId: string) => {
    setIsSubmittingReview(true);
    try {
      await api.verifyPolicy(draftId, {
        decision: "APPROVE",
        reviewerNotes: reviewerNotes || "Approved after automated discovery & contract verification audit.",
      });
      toast.success("Draft policy verified, published, and activated in live recommendation catalog!", "Policy Published");
      setSelectedDraft(null);
      setReviewerNotes("");
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Could not publish draft policy", "Publication Blocked");
    } finally {
      setIsSubmittingReview(false);
    }
  };

  const handleRejectDraft = async (draftId: string) => {
    setIsSubmittingReview(true);
    try {
      await api.verifyPolicy(draftId, {
        decision: "REJECT",
        reviewerNotes: reviewerNotes || "Rejected due to unverified clauses or contractual ambiguities.",
      });
      toast.success("Draft policy rejected and flagged for revision.", "Policy Rejected");
      setSelectedDraft(null);
      setReviewerNotes("");
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Could not reject draft policy", "Rejection Error");
    } finally {
      setIsSubmittingReview(false);
    }
  };

  const handleRollbackPolicy = async (policyId: string, policyName: string) => {
    const reason = window.prompt(
      `Enter reason for rolling back '${policyName}' to its previous active revision:`,
      "Defect or contractual inaccuracy identified in current revision."
    );
    if (!reason) return;

    try {
      const res = await api.rollbackPolicy(policyId, reason);
      toast.success(
        `Rollback successful! Restored previous revision '${res.data?.policyCode || res.data?.policyName}'.`,
        "Revision Restored"
      );
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to rollback revision", "Rollback Failed");
    }
  };

  const handleWithdrawPolicy = async (policyCode: string, policyName: string) => {
    const reason = window.prompt(
      `Enter reason for withdrawing '${policyName}' (${policyCode}) from recommendation eligibility:`,
      "Regulatory circular withdrawal / discontinued by insurer."
    );
    if (!reason) return;

    try {
      await api.withdrawPolicy(policyCode, reason);
      toast.success(`Policy '${policyName}' withdrawn and excluded from active recommendations.`, "Policy Withdrawn");
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to withdraw policy", "Withdrawal Error");
    }
  };

  const handleRejectDiscoveryCandidate = async (candidateId: string, candidateName: string) => {
    const reason = window.prompt(
      `Enter reason for rejecting candidate '${candidateName}':`,
      "Unverified document source / marketing brochure / identity mismatch."
    );
    if (!reason) return;

    try {
      await api.rejectDiscoveryCandidate(candidateId, reason);
      toast.success(`Candidate '${candidateName}' rejected and transitioned to REJECTED.`, "Candidate Rejected");
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Failed to reject candidate", "Rejection Error");
    }
  };

  const handleRunSmokeTest = async () => {
    setIsRunningSmokeTest(true);
    try {
      const res = await api.runDiscoverySmokeTest();
      setSmokeTestResult(res.data);
      toast.success(`Smoke test complete: ${res.data?.outcome} (${res.data?.latencyMs}ms)`, "Smoke Test Executed");
    } catch (err: any) {
      toast.error(err.message || "Smoke test failed to connect", "Smoke Test Error");
    } finally {
      setIsRunningSmokeTest(false);
    }
  };

  const handleCreateSource = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSource.sourceName || !newSource.sourceUrl || !newSource.provider) {
      toast.error("Source name, provider, and official document URL are required.", "Validation Error");
      return;
    }
    try {
      await api.registerPolicySource(newSource);
      toast.success(`Registered official source '${newSource.sourceName}'`, "Source Added");
      setShowAddSourceModal(false);
      setNewSource({
        sourceName: "",
        provider: "",
        canonicalProductName: "",
        irdaUin: "",
        sourceUrl: "",
        documentType: "POLICY_WORDING",
        sourceAuthority: "OFFICIAL_INSURER_WEBSITE",
        pollFrequencyHours: 24,
      });
      await loadData();
    } catch (err: any) {
      toast.error(err.message || "Could not register policy source", "Registration Error");
    }
  };

  const handleRegisterCuratedSource = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!curatedForm.sourceUrl) {
      toast.error("Official document URL is required.", "Validation Error");
      return;
    }
    setIsSubmittingCurated(true);
    try {
      const res = await api.registerCuratedOfficialSource(curatedForm);
      if (res.data?.success) {
        toast.success(
          `Curated official PDF registered! Parsed UIN: ${res.data.irdaUin || "Extracted"} (SHA-256: ${res.data.sha256Checksum ? res.data.sha256Checksum.substring(0, 8) + "..." : "Computed"}). Staged as ${res.data.verificationStatus}`,
          "Curated Document Staged"
        );
        setShowCuratedModal(false);
        setCuratedForm({
          sourceUrl: "",
          sourceName: "",
          provider: "",
          canonicalProductName: "",
          expectedUin: "",
          expectedRevision: "",
          curationNotes: "",
        });
        await loadData();
        setActiveTab("drafts");
      } else {
        toast.error(res.data?.message || "Failed to register curated source", "Registration Blocked");
      }
    } catch (err: any) {
      toast.error(err.message || "Could not process curated source URL", "Curated Source Error");
    } finally {
      setIsSubmittingCurated(false);
    }
  };

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar activeTab="/dashboard/admin/catalog" />

      <main className="flex-1 overflow-y-auto">
        {/* Header */}
        <header className="sticky top-0 z-30 glass border-b border-border px-6 py-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="ml-10 lg:ml-0">
            <div className="flex items-center gap-2">
              <span className="p-1.5 rounded-lg bg-primary/20 text-primary-light">
                <ShieldCheck className="w-5 h-5" />
              </span>
              <h1 className="text-xl font-bold tracking-tight">Policy Catalog Governance & Ingestion</h1>
            </div>
            <p className="text-xs text-muted mt-0.5">
              Automated IRDAI discovery, SHA-256 deduplication, and authorized human verification gate
            </p>
          </div>

          <div className="flex items-center gap-2.5">
            <button
              onClick={handleRunSmokeTest}
              disabled={isRunningSmokeTest}
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-semibold glass border border-sky-500/30 text-sky-400 hover:bg-sky-500/10 transition-all"
            >
              <Globe className={`w-3.5 h-3.5 ${isRunningSmokeTest ? "animate-spin" : ""}`} />
              {isRunningSmokeTest ? "Pinging..." : "Source Smoke Test"}
            </button>
            <button
              onClick={handleRefreshCache}
              disabled={isRefreshingCache}
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-semibold glass border border-white/10 hover:border-white/20 transition-all text-foreground"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshingCache ? "animate-spin" : ""}`} />
              Sync Engine Cache
            </button>
            <button
              onClick={handleCrawlAll}
              disabled={isCrawling}
              className="flex items-center gap-1.5 px-4 py-1.5 rounded-xl text-xs font-semibold bg-gradient-to-r from-primary to-accent text-white shadow-lg shadow-primary/25 hover:opacity-90 transition-all"
            >
              <UploadCloud className={`w-3.5 h-3.5 ${isCrawling ? "animate-pulse" : ""}`} />
              {isCrawling ? "Crawling Sources..." : "Trigger Discovery Crawl"}
            </button>
          </div>
        </header>

        <div className="p-6 max-w-7xl mx-auto space-y-6">
          {/* Governance Metric Cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="glass p-4 rounded-2xl border border-white/5 relative overflow-hidden">
              <div className="flex items-center justify-between">
                <span className="text-xs text-muted font-medium">Live Verified Catalog</span>
                <span className="p-2 rounded-xl bg-emerald-500/10 text-emerald-400">
                  <CheckCircle2 className="w-4 h-4" />
                </span>
              </div>
              <p className="text-2xl font-bold mt-2 text-foreground">
                {stats?.verifiedPublishedPolicies ?? livePolicies.length}
              </p>
              <p className="text-[11px] text-muted mt-0.5">Eligible for recommendations</p>
            </div>

            <div className="glass p-4 rounded-2xl border border-white/5 relative overflow-hidden">
              <div className="flex items-center justify-between">
                <span className="text-xs text-muted font-medium">Staged Drafts (Gated)</span>
                <span className="p-2 rounded-xl bg-amber-500/10 text-amber-400">
                  <Clock className="w-4 h-4" />
                </span>
              </div>
              <p className="text-2xl font-bold mt-2 text-foreground">
                {stats?.pendingReviewPolicies ?? drafts.length}
              </p>
              <p className="text-[11px] text-muted mt-0.5">Awaiting reviewer approval</p>
            </div>

            <div className="glass p-4 rounded-2xl border border-white/5 relative overflow-hidden">
              <div className="flex items-center justify-between">
                <span className="text-xs text-muted font-medium">Monitored Sources</span>
                <span className="p-2 rounded-xl bg-sky-500/10 text-sky-400">
                  <Globe className="w-4 h-4" />
                </span>
              </div>
              <p className="text-2xl font-bold mt-2 text-foreground">{sources.length}</p>
              <p className="text-[11px] text-muted mt-0.5">Official insurer & IRDAI portals</p>
            </div>

            <div className="glass p-4 rounded-2xl border border-white/5 relative overflow-hidden">
              <div className="flex items-center justify-between">
                <span className="text-xs text-muted font-medium">Archived Checksums</span>
                <span className="p-2 rounded-xl bg-purple-500/10 text-purple-400">
                  <Hash className="w-4 h-4" />
                </span>
              </div>
              <p className="text-2xl font-bold mt-2 text-foreground">{documents.length}</p>
              <p className="text-[11px] text-muted mt-0.5">Cryptographically tracked</p>
            </div>
          </div>

          {/* Navigation Tabs */}
          <div className="flex items-center gap-2 border-b border-white/10 pb-2">
            <button
              onClick={() => setActiveTab("drafts")}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === "drafts"
                  ? "bg-primary text-white shadow-lg shadow-primary/25"
                  : "text-muted hover:text-foreground hover:bg-white/5"
              }`}
            >
              <FileSearch className="w-3.5 h-3.5" />
              Staged Drafts ({drafts.length})
            </button>
            <button
              onClick={() => setActiveTab("sources")}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === "sources"
                  ? "bg-primary text-white shadow-lg shadow-primary/25"
                  : "text-muted hover:text-foreground hover:bg-white/5"
              }`}
            >
              <Globe className="w-3.5 h-3.5" />
              Monitored Sources ({sources.length})
            </button>
            <button
              onClick={() => setActiveTab("live")}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === "live"
                  ? "bg-primary text-white shadow-lg shadow-primary/25"
                  : "text-muted hover:text-foreground hover:bg-white/5"
              }`}
            >
              <Database className="w-3.5 h-3.5" />
              Live Active Catalog ({livePolicies.length})
            </button>
            <button
              onClick={() => setActiveTab("documents")}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === "documents"
                  ? "bg-primary text-white shadow-lg shadow-primary/25"
                  : "text-muted hover:text-foreground hover:bg-white/5"
              }`}
            >
              <FileText className="w-3.5 h-3.5" />
              Document Archive Logs ({documents.length})
            </button>
            <button
              onClick={() => {
                setActiveTab("benchmark");
                if (!qualityReport) {
                  handleRunBenchmark();
                }
              }}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === "benchmark"
                  ? "bg-gradient-to-r from-primary to-accent text-white shadow-lg shadow-primary/25"
                  : "text-muted hover:text-foreground hover:bg-white/5"
              }`}
            >
              <Scale className="w-3.5 h-3.5" />
              Quality Benchmark {qualityReport ? `(${qualityReport.overallQualityScore}%)` : ""}
            </button>
            <button
              onClick={() => setActiveTab("discovery")}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === "discovery"
                  ? "bg-gradient-to-r from-primary to-accent text-white shadow-lg shadow-primary/25"
                  : "text-muted hover:text-foreground hover:bg-white/5"
              }`}
            >
              <Sparkles className="w-3.5 h-3.5" />
              Discovery Pipeline ({discoveryCandidates.length})
            </button>
            <button
              onClick={() => setActiveTab("conflicts")}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === "conflicts"
                  ? "bg-amber-500 text-white shadow-lg shadow-amber-500/25"
                  : "text-muted hover:text-foreground hover:bg-white/5"
              }`}
            >
              <AlertTriangle className="w-3.5 h-3.5 text-amber-300" />
              Reviewer Conflicts ({conflicts.length})
            </button>
          </div>

          {/* TAB 1: STAGED DRAFTS PENDING REVIEW */}
          {activeTab === "drafts" && (
            <div className="space-y-4">
              {drafts.length === 0 ? (
                <div className="glass p-12 rounded-2xl border border-white/5 text-center">
                  <CheckCircle2 className="w-12 h-12 text-emerald-400 mx-auto mb-3 opacity-80" />
                  <h3 className="text-base font-bold text-foreground">No pending drafts awaiting review</h3>
                  <p className="text-xs text-muted max-w-md mx-auto mt-1">
                    All discovered policy filings have been processed or published. New drafts will appear here when
                    the background monitor detects an updated official wording PDF.
                  </p>
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-4">
                  {drafts.map((draft) => (
                    <div
                      key={draft.id}
                      className="glass p-5 rounded-2xl border border-amber-500/20 hover:border-amber-500/40 transition-all flex flex-col md:flex-row md:items-center justify-between gap-4"
                    >
                      <div className="space-y-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/10 text-amber-400 border border-amber-500/20">
                            {draft.verificationStatus || "EXTRACTION_PENDING_REVIEW"}
                          </span>
                          <span className="px-2 py-0.5 rounded-md text-[10px] font-mono bg-white/5 text-muted border border-white/10">
                            UIN: {draft.irdaUin || "Pending Extract"}
                          </span>
                          {draft.uinRevision && (
                            <span className="px-2 py-0.5 rounded-md text-[10px] font-mono bg-white/5 text-muted">
                              Rev: {draft.uinRevision}
                            </span>
                          )}
                          <span className="px-2 py-0.5 rounded-md text-[10px] font-medium bg-red-500/10 text-red-400">
                            active: false (Gated)
                          </span>
                        </div>

                        <div>
                          <h4 className="text-base font-bold text-foreground">{draft.policyName}</h4>
                          <p className="text-xs text-muted">{draft.provider}</p>
                        </div>

                        {/* Confidence & Uncertain Fields */}
                        <div className="flex flex-wrap items-center gap-3 text-xs">
                          <div className="flex items-center gap-1.5">
                            <span className="text-muted">Contract Confidence:</span>
                            <span className="font-semibold text-emerald-400">
                              {draft.contractConfidenceScore ?? 100}%
                            </span>
                          </div>
                          <span className="text-white/20">•</span>
                          <div className="flex items-center gap-1.5">
                            <span className="text-muted">Pricing Model:</span>
                            <span className="font-semibold text-sky-400">
                              {draft.pricingStatus || "CALIBRATED_ACTUARIAL_TABLE"}
                            </span>
                          </div>
                        </div>

                        {draft.contractUncertainFields && draft.contractUncertainFields.length > 0 && (
                          <div className="flex items-start gap-1.5 p-2 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-300 text-[11px]">
                            <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                            <span>Contradictions / Audit Flags: {draft.contractUncertainFields.join(", ")}</span>
                          </div>
                        )}
                      </div>

                      <div className="flex items-center gap-2.5 shrink-0">
                        <button
                          onClick={() => setSelectedDraft(draft)}
                          className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-semibold bg-primary text-white hover:opacity-90 shadow-lg shadow-primary/25 transition-all"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          Review & Publish
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 2: MONITORED POLICY SOURCES */}
          {activeTab === "sources" && (
            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <div>
                  <h3 className="text-sm font-bold text-foreground">Registered Official Sources</h3>
                  <p className="text-xs text-muted">Authoritative URLs checked for changes using HTTP ETags & SHA-256 hashes</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setShowCuratedModal(true)}
                    className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-gradient-to-r from-emerald-500/20 to-teal-500/20 hover:from-emerald-500/30 hover:to-teal-500/30 text-emerald-300 border border-emerald-500/30 transition-all shadow-sm"
                  >
                    <ShieldCheck className="w-3.5 h-3.5" />
                    Curate & Stage Official PDF
                  </button>
                  <button
                    onClick={() => setShowAddSourceModal(true)}
                    className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-white/10 hover:bg-white/15 text-foreground transition-all"
                  >
                    <Globe className="w-3.5 h-3.5" />
                    Register Source
                  </button>
                </div>
              </div>

              {/* Live Discovery vs Fallback Observability Banner */}
              {latestCrawlSummary && (
                <div
                  className={`p-4 rounded-2xl border text-xs space-y-2 transition-all ${
                    latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY"
                      ? "bg-emerald-500/10 border-emerald-500/20 text-emerald-200"
                      : latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY_EMPTY"
                      ? "bg-blue-500/10 border-blue-500/20 text-blue-200"
                      : "bg-amber-500/10 border-amber-500/20 text-amber-200"
                  }`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span
                        className={`w-2.5 h-2.5 rounded-full animate-pulse ${
                          latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY"
                            ? "bg-emerald-400"
                            : latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY_EMPTY"
                            ? "bg-blue-400"
                            : "bg-amber-400"
                        }`}
                      />
                      <span className="font-bold text-sm text-foreground">
                        {latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY"
                          ? "Dynamic Live Portal Discovery Active"
                          : latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY_EMPTY"
                          ? "Live Portal Scan Completed (0 New Eligible Candidates)"
                          : "Curated Official Catalog Fallback Engaged"}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold uppercase tracking-wider ${
                          latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY"
                            ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                            : latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY_EMPTY"
                            ? "bg-blue-500/20 text-blue-300 border border-blue-500/30"
                            : "bg-amber-500/20 text-amber-300 border border-amber-500/30"
                        }`}
                      >
                        {latestCrawlSummary.discoveryMode}
                      </span>
                    </div>
                    {latestCrawlSummary.lastLiveScanTimestamp && (
                      <span className="text-[11px] text-muted font-mono">
                        Last Live Scan: {new Date(latestCrawlSummary.lastLiveScanTimestamp).toLocaleTimeString()}
                      </span>
                    )}
                  </div>

                  <p className="text-muted leading-relaxed">
                    {latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY"
                      ? `Successfully scanned live official insurer portals with socket-pinned connections and redirect domain boundary checks. Discovered ${latestCrawlSummary.liveCandidatesDiscovered || 0} candidate links across official directories with 0 DNS rebinding exposure.`
                      : latestCrawlSummary.discoveryMode === "LIVE_DISCOVERY_EMPTY"
                      ? `Live official insurer portals were successfully reached and scanned, but yielded 0 new eligible candidates (all were duplicate or ineligible). Catalog fallback was not required.`
                      : `Live insurer root portals were offline, rate-limited, or blocked by bot protection. Fallback catalog was safely engaged (${latestCrawlSummary.fallbackCatalogCandidates || 5} official filings evaluated) with strict SHA-256 deduplication.`}
                  </p>

                  {latestCrawlSummary.candidateRejectionReasons &&
                    Object.keys(latestCrawlSummary.candidateRejectionReasons).length > 0 && (
                      <div className="pt-2 border-t border-white/10 flex flex-wrap gap-2 text-[11px]">
                        <span className="text-muted font-semibold">Rejected Candidates:</span>
                        {Object.entries(latestCrawlSummary.candidateRejectionReasons).map(([reason, count]) => (
                          <span
                            key={reason}
                            className="px-2 py-0.5 rounded bg-white/5 border border-white/10 font-mono text-muted text-[10px]"
                          >
                            {reason}: <strong className="text-foreground">{count as any}</strong>
                          </span>
                        ))}
                      </div>
                    )}

                  {/* Structured Per-Domain Diagnostics */}
                  {latestCrawlSummary.domainDiagnostics && latestCrawlSummary.domainDiagnostics.length > 0 && (
                    <div className="pt-3 border-t border-white/10 space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-foreground text-xs flex items-center gap-1.5">
                          <Globe className="w-3.5 h-3.5 text-primary-light" />
                          Insurer Domain Diagnostics ({latestCrawlSummary.domainDiagnostics.length} endpoints inspected)
                        </span>
                        <span className="text-[10px] text-muted">Socket-pinned TLS &bull; RFC 6125 SAN check &bull; Host header verification</span>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2 pt-1">
                        {latestCrawlSummary.domainDiagnostics.map((diag: DomainDiagnostics, idx: number) => {
                          const isSuccess = diag.errorCategory === "SUCCESS_200" || diag.httpStatusCode === 200;
                          const isRedirect = diag.errorCategory === "REDIRECT_FOLLOWED" || (diag.httpStatusCode >= 300 && diag.httpStatusCode < 400);
                          const isBot = diag.errorCategory === "BOT_PROTECTION_403" || diag.httpStatusCode === 403;
                          const isObsolete = diag.errorCategory === "OBSOLETE_ENDPOINT_404" || diag.httpStatusCode === 404;
                          const isTimeout = diag.errorCategory === "TIMEOUT_ERROR";
                          const isMisdirected = diag.errorCategory === "MISDIRECTED_OR_BAD_421_400" || diag.httpStatusCode === 421 || diag.httpStatusCode === 400;

                          const badgeColor = isSuccess
                            ? "bg-emerald-500/10 text-emerald-300 border-emerald-500/20"
                            : isRedirect
                            ? "bg-purple-500/10 text-purple-300 border-purple-500/20"
                            : isBot
                            ? "bg-rose-500/10 text-rose-300 border-rose-500/20"
                            : isObsolete
                            ? "bg-amber-500/10 text-amber-300 border-amber-500/20"
                            : isTimeout
                            ? "bg-orange-500/10 text-orange-300 border-orange-500/20"
                            : isMisdirected
                            ? "bg-fuchsia-500/10 text-fuchsia-300 border-fuchsia-500/20"
                            : "bg-red-500/10 text-red-300 border-red-500/20";

                          return (
                            <div
                              key={idx}
                              className="p-3 rounded-xl bg-black/40 border border-white/5 space-y-1.5 text-[11px]"
                            >
                              <div className="flex items-center justify-between gap-1">
                                <span className="font-bold text-foreground truncate max-w-[150px]" title={diag.domain}>
                                  {diag.domain}
                                </span>
                                <span className={`px-1.5 py-0.5 rounded text-[9px] font-mono font-bold border ${badgeColor}`}>
                                  HTTP {diag.httpStatusCode || "N/A"}
                                </span>
                              </div>

                              <div className="flex items-center justify-between text-[10px] text-muted">
                                <span className="font-mono">{diag.resolvedIp || "DNS pending"}</span>
                                <span className="uppercase font-semibold tracking-wider text-[9px]">{diag.errorCategory}</span>
                              </div>

                              {diag.redirectChain && diag.redirectChain.length > 0 && (
                                <div className="text-[10px] text-purple-300/80 font-mono truncate" title={diag.redirectChain.join(" → ")}>
                                  ↳ {diag.redirectChain.join(" → ")}
                                </div>
                              )}

                              <div className="flex items-center justify-between text-[10px] pt-1 border-t border-white/5 text-muted">
                                <span>PDF Links: <strong className="text-foreground">{diag.linksExtracted}</strong></span>
                                {diag.contentType && <span className="font-mono truncate max-w-[120px]">{diag.contentType.split(";")[0]}</span>}
                              </div>

                              {diag.sanitizedExcerpt && (
                                <div className="p-1.5 rounded bg-white/5 font-mono text-[9px] text-muted/80 truncate" title={diag.sanitizedExcerpt}>
                                  {diag.sanitizedExcerpt}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              )}

              <div className="grid grid-cols-1 gap-3">
                {sources.map((src) => (
                  <div
                    key={src.id}
                    className="glass p-4 rounded-2xl border border-white/5 flex flex-col md:flex-row md:items-center justify-between gap-3"
                  >
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2">
                        <span
                          className={`w-2 h-2 rounded-full ${
                            src.status === "SYNCED" ? "bg-emerald-400" : src.status === "FAILED" ? "bg-rose-400" : "bg-amber-400"
                          }`}
                        />
                        <h4 className="text-sm font-bold text-foreground">{src.sourceName}</h4>
                        <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-white/5 text-muted">
                          {src.status}
                        </span>
                      </div>

                      <p className="text-xs text-muted">{src.provider} • UIN: {src.irdaUin || "Unspecified"}</p>

                      <div className="flex items-center gap-2 text-[11px] text-muted">
                        <span className="truncate max-w-md font-mono">{src.sourceUrl}</span>
                        <a
                          href={src.sourceUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-primary-light hover:underline inline-flex items-center gap-0.5"
                        >
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      </div>

                      {src.lastSha256Checksum && (
                        <p className="text-[10px] text-muted/70 font-mono">
                          SHA-256: {src.lastSha256Checksum.substring(0, 16)}... (ETag: {src.lastEtag || "None"})
                        </p>
                      )}
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        onClick={() => handleCrawlSingleSource(src.id)}
                        className="px-3 py-1.5 rounded-xl text-xs font-medium glass border border-white/10 hover:border-white/20 text-foreground transition-all flex items-center gap-1"
                      >
                        <RefreshCw className="w-3 h-3" /> Check Now
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 3: LIVE ACTIVE CATALOG */}
          {activeTab === "live" && (
            <div className="space-y-4">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
                <div>
                  <h3 className="text-sm font-bold text-foreground">Live Active Catalog & Authenticity Verification</h3>
                  <p className="text-xs text-muted">Audited policies driving recommendations vs segregated synthetic test fixtures</p>
                </div>
                <div className="flex items-center gap-1.5 p-1 bg-white/5 rounded-xl border border-white/10 text-xs">
                  <button
                    onClick={() => setAuthenticityFilter("ALL")}
                    className={`px-2.5 py-1 rounded-lg font-semibold transition-all ${
                      authenticityFilter === "ALL" ? "bg-primary text-white" : "text-muted hover:text-foreground"
                    }`}
                  >
                    All ({livePolicies.length})
                  </button>
                  <button
                    onClick={() => setAuthenticityFilter("VERIFIED")}
                    className={`px-2.5 py-1 rounded-lg font-semibold transition-all ${
                      authenticityFilter === "VERIFIED" ? "bg-emerald-500 text-white" : "text-muted hover:text-foreground"
                    }`}
                  >
                    Official Verified ({livePolicies.filter((p) => !p.syntheticFixture && p.documentAuthenticityStatus === "VERIFIED_OFFICIAL_SOURCE").length})
                  </button>
                  <button
                    onClick={() => setAuthenticityFilter("SYNTHETIC")}
                    className={`px-2.5 py-1 rounded-lg font-semibold transition-all ${
                      authenticityFilter === "SYNTHETIC" ? "bg-amber-500 text-white" : "text-muted hover:text-foreground"
                    }`}
                  >
                    Synthetic Fixtures ({livePolicies.filter((p) => p.syntheticFixture || p.documentAuthenticityStatus === "TEST_FIXTURE_OR_SYNTHETIC").length})
                  </button>
                </div>
              </div>

              {smokeTestResult && (
                <div className="p-3 rounded-xl bg-sky-500/10 border border-sky-500/20 text-xs flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Globe className="w-4 h-4 text-sky-400" />
                    <span>
                      Live Smoke Test: <strong>{smokeTestResult.targetUrl}</strong> • Status: <strong>{smokeTestResult.outcome}</strong> ({smokeTestResult.latencyMs}ms, {smokeTestResult.bytesAcquired} bytes)
                    </span>
                  </div>
                  <span className="font-mono text-[10px] text-sky-300">
                    SHA: {smokeTestResult.sha256Digest?.substring(0, 16)}...
                  </span>
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {livePolicies
                  .filter((p) => {
                    if (authenticityFilter === "VERIFIED") {
                      return !p.syntheticFixture && p.documentAuthenticityStatus === "VERIFIED_OFFICIAL_SOURCE";
                    }
                    if (authenticityFilter === "SYNTHETIC") {
                      return p.syntheticFixture || p.documentAuthenticityStatus === "TEST_FIXTURE_OR_SYNTHETIC";
                    }
                    return true;
                  })
                  .map((p) => {
                    const isSynthetic = p.syntheticFixture || p.documentAuthenticityStatus === "TEST_FIXTURE_OR_SYNTHETIC";
                    return (
                      <div
                        key={p.id}
                        className={`glass p-5 rounded-2xl border space-y-3 ${
                          isSynthetic ? "border-amber-500/30 bg-amber-500/[0.02]" : "border-emerald-500/20"
                        }`}
                      >
                        <div className="flex items-start justify-between">
                          <div>
                            <div className="flex flex-wrap items-center gap-1.5">
                              <span
                                className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${
                                  isSynthetic
                                    ? "bg-amber-500/10 text-amber-400 border-amber-500/20"
                                    : "bg-emerald-500/10 text-emerald-400 border-emerald-500/20"
                                }`}
                              >
                                {isSynthetic ? "TEST_FIXTURE_OR_SYNTHETIC" : (p.documentAuthenticityStatus || p.verificationStatus)}
                              </span>
                              {isSynthetic && (
                                <span className="text-[10px] text-amber-400 font-semibold">
                                  (Excluded from Recommendations)
                                </span>
                              )}
                            </div>
                            <h4 className="text-base font-bold text-foreground mt-1.5">{p.policyName}</h4>
                            <p className="text-xs text-muted">{p.provider}</p>
                          </div>
                          <span className="text-[11px] font-mono text-muted bg-white/5 px-2 py-0.5 rounded border border-white/10">
                            {p.irdaUin}
                          </span>
                        </div>

                        <div className="grid grid-cols-2 gap-2 text-xs pt-2 border-t border-white/5">
                          <div>
                            <span className="text-muted block text-[10px]">Room Rent:</span>
                            <span className="font-semibold text-foreground">
                              {p.contractTerms?.roomRentCapped ? "Capped" : "No Room Rent Limit"}
                            </span>
                          </div>
                          <div>
                            <span className="text-muted block text-[10px]">Co-Payment:</span>
                            <span className="font-semibold text-foreground">
                              {p.contractTerms?.copayPercentage ?? 0}%
                            </span>
                          </div>
                          <div>
                            <span className="text-muted block text-[10px]">PED Waiting:</span>
                            <span className="font-semibold text-foreground">
                              {p.contractTerms?.waitingPeriodPedMonths ?? 36} months
                            </span>
                          </div>
                          <div>
                            <span className="text-muted block text-[10px]">Authenticity Trust:</span>
                            <span className="font-semibold text-foreground">
                              {isSynthetic ? "Synthetic Demonstration" : "Chain of Custody Verified"}
                            </span>
                          </div>
                        </div>

                        <div className="flex items-center justify-between pt-2 border-t border-white/5">
                          <span className="text-[10px] text-muted font-mono">
                            {p.policyCode || p.id}
                          </span>
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => handleWithdrawPolicy(p.policyCode || p.id, p.policyName)}
                              title="Withdraw policy from recommendation eligibility"
                              className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 border border-rose-500/20 transition-all flex items-center gap-1"
                            >
                              <XCircle className="w-3 h-3" /> Withdraw
                            </button>
                            <button
                              onClick={() => handleRollbackPolicy(p.id, p.policyName)}
                              title="Rollback to previous active revision if issues are found"
                              className="px-2.5 py-1 rounded-lg text-[11px] font-semibold glass hover:bg-white/10 border border-white/10 text-foreground transition-all flex items-center gap-1"
                            >
                              <RotateCcw className="w-3 h-3" /> Rollback
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })}
              </div>
            </div>
          )}

          {/* TAB 4: DISCOVERED DOCUMENTS ARCHIVE */}
          {activeTab === "documents" && (
            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <div>
                  <h3 className="text-sm font-bold text-foreground">Cryptographic Document History</h3>
                  <p className="text-xs text-muted">Audited record of all retrieved PDFs, hashes, and parsing states</p>
                </div>
              </div>

              <div className="glass rounded-2xl border border-white/5 overflow-hidden">
                <table className="w-full text-left text-xs">
                  <thead className="bg-white/5 border-b border-white/10 text-muted">
                    <tr>
                      <th className="p-3 font-semibold">Document Name</th>
                      <th className="p-3 font-semibold">SHA-256 Checksum</th>
                      <th className="p-3 font-semibold">File Size</th>
                      <th className="p-3 font-semibold">Status</th>
                      <th className="p-3 font-semibold">Fetched At</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {documents.map((doc) => (
                      <tr key={doc.id} className="hover:bg-white/[0.02]">
                        <td className="p-3 font-medium text-foreground">{doc.documentName}</td>
                        <td className="p-3 font-mono text-[11px] text-muted">
                          {doc.sha256Checksum ? `${doc.sha256Checksum.substring(0, 16)}...` : "None"}
                        </td>
                        <td className="p-3 text-muted">
                          {doc.fileSizeBytes ? `${(doc.fileSizeBytes / 1024).toFixed(1)} KB` : "-"}
                        </td>
                        <td className="p-3">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                              doc.extractionStatus === "EXTRACTED_STAGED"
                                ? "bg-emerald-500/10 text-emerald-400"
                                : "bg-amber-500/10 text-amber-400"
                            }`}
                          >
                            {doc.extractionStatus}
                          </span>
                        </td>
                        <td className="p-3 text-muted">
                          {doc.fetchedAt ? new Date(doc.fetchedAt).toLocaleString() : "-"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* TAB 5: RECOMMENDATION QUALITY EVALUATION BENCHMARK SUITE */}
          {activeTab === "benchmark" && (
            <div className="space-y-6">
              {/* Benchmark Header & Actions */}
              <div className="glass p-6 rounded-3xl border border-white/10 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="p-2 rounded-xl bg-primary/20 text-primary-light">
                      <Scale className="w-5 h-5" />
                    </span>
                    <div>
                      <h3 className="text-base font-bold text-foreground">
                        Recommendation Quality Evaluation Suite
                      </h3>
                      <p className="text-xs text-muted mt-0.5">
                        Domain-verified benchmark dataset assessing constraint enforcement, ranking accuracy, and claim arithmetic
                      </p>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  {qualityReport && (
                    <span className="text-xs text-muted">
                      Evaluated: {new Date(qualityReport.evaluatedAt).toLocaleTimeString()}
                    </span>
                  )}
                    <button
                    onClick={handleRunBenchmark}
                    disabled={isRunningBenchmark}
                    className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold bg-gradient-to-r from-primary to-accent text-white shadow-lg shadow-primary/25 hover:opacity-90 transition-all disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${isRunningBenchmark ? "animate-spin" : ""}`} />
                    {isRunningBenchmark ? "Running Benchmark Scenarios..." : "Run Quality Suite"}
                  </button>
                </div>
              </div>

              {isRunningBenchmark && !qualityReport && (
                <div className="glass p-12 rounded-3xl border border-white/10 text-center space-y-3">
                  <RefreshCw className="w-8 h-8 animate-spin text-primary mx-auto" />
                  <h4 className="text-sm font-bold text-foreground">Executing Benchmark Scenarios</h4>
                  <p className="text-xs text-muted max-w-md mx-auto">
                    Simulating 8 customer profiles against the deterministic ranking engine and validating claim mathematical precision...
                  </p>
                </div>
              )}

              {qualityReport && (
                <>
                  {/* Quality Summary Scorecard */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    <div className="glass p-4 rounded-2xl border border-white/5 relative overflow-hidden">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-muted font-medium">Overall Quality Score</span>
                        <span className="p-2 rounded-xl bg-primary/10 text-primary-light">
                          <Award className="w-4 h-4" />
                        </span>
                      </div>
                      <p className="text-2xl font-bold mt-2 text-foreground">
                        {qualityReport.overallQualityScore}%
                      </p>
                      {qualityReport.regressionPassed ? (
                        <p className="text-[11px] text-emerald-400 mt-0.5 font-medium flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3" /> Regression Passed
                        </p>
                      ) : (
                        <p className="text-[11px] text-rose-400 mt-0.5 font-medium flex items-center gap-1">
                          <AlertTriangle className="w-3 h-3" /> Regression Failed
                        </p>
                      )}
                    </div>

                    <div className="glass p-4 rounded-2xl border border-white/5 relative overflow-hidden">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-muted font-medium">Mandatory Violations</span>
                        <span className="p-2 rounded-xl bg-emerald-500/10 text-emerald-400">
                          <ShieldCheck className="w-4 h-4" />
                        </span>
                      </div>
                      <p className="text-2xl font-bold mt-2 text-emerald-400">
                        {qualityReport.mandatoryConstraintViolationRate}%
                      </p>
                      <p className="text-[11px] text-muted mt-0.5">Strict zero-tolerance baseline</p>
                    </div>

                    <div className="glass p-4 rounded-2xl border border-white/5 relative overflow-hidden">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-muted font-medium">Ranking Agreement</span>
                        <span className="p-2 rounded-xl bg-purple-500/10 text-purple-400">
                          <Scale className="w-4 h-4" />
                        </span>
                      </div>
                      <p className="text-2xl font-bold mt-2 text-foreground">
                        {qualityReport.rankingAgreementRate}%
                      </p>
                      <p className="text-[11px] text-muted mt-0.5">Domain-reviewed archetype match</p>
                    </div>

                    <div className="glass p-4 rounded-2xl border border-white/5 relative overflow-hidden">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-muted font-medium">Claim Math Precision</span>
                        <span className="p-2 rounded-xl bg-blue-500/10 text-blue-400">
                          <Calculator className="w-4 h-4" />
                        </span>
                      </div>
                      <p className="text-2xl font-bold mt-2 text-blue-400">
                        {qualityReport.claimMathAccuracyRate}%
                      </p>
                      <p className="text-[11px] text-muted mt-0.5">Deductible & copay precision</p>
                    </div>
                  </div>

                  {/* Archetype Breakdown Table */}
                  <div className="glass rounded-3xl border border-white/10 overflow-hidden">
                    <div className="p-4 border-b border-white/10 flex items-center justify-between">
                      <div>
                        <h4 className="text-xs font-bold uppercase tracking-wider text-muted">
                          Verified Benchmark Scenarios ({qualityReport.scenarioResults.length} Profiles)
                        </h4>
                        <p className="text-[11px] text-muted mt-0.5">
                          Engine Version: <span className="text-foreground font-mono">{qualityReport.engineVersion}</span> • Verified Catalog Size: <span className="text-foreground font-semibold">{qualityReport.catalogPolicyCount} policies</span>
                        </p>
                      </div>
                      <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                        {qualityReport.passedScenarios}/{qualityReport.totalBenchmarkScenarios} Passed
                      </span>
                    </div>

                    <div className="overflow-x-auto">
                      <table className="w-full text-left text-xs">
                        <thead className="bg-white/5 border-b border-white/10 text-muted uppercase text-[10px]">
                          <tr>
                            <th className="p-3 font-semibold">Scenario Profile</th>
                            <th className="p-3 font-semibold">Category</th>
                            <th className="p-3 font-semibold">Mandatory Constraints</th>
                            <th className="p-3 font-semibold">Claim Math</th>
                            <th className="p-3 font-semibold">Top Recommendation</th>
                            <th className="p-3 font-semibold">Citation Coverage</th>
                            <th className="p-3 font-semibold">Latency</th>
                            <th className="p-3 font-semibold">Status</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                          {qualityReport.scenarioResults.map((s) => (
                            <tr key={s.scenarioId} className="hover:bg-white/[0.02]">
                              <td className="p-3">
                                <div className="font-semibold text-foreground">{s.scenarioName}</div>
                                {s.documentedGroundTruth && (
                                  <div className="text-[10px] text-primary-light/90 mt-0.5 font-medium">
                                    <span className="text-muted">Ground Truth: </span>{s.documentedGroundTruth}
                                  </div>
                                )}
                                <div className="text-[10px] text-muted">{s.auditNotes}</div>
                              </td>
                              <td className="p-3 text-muted">{s.category}</td>
                              <td className="p-3">
                                {s.mandatoryConstraintsHonored ? (
                                  <span className="inline-flex items-center gap-1 text-emerald-400 font-medium text-[11px]">
                                    <CheckCircle2 className="w-3.5 h-3.5" /> 100% Honored
                                  </span>
                                ) : (
                                  <span className="inline-flex items-center gap-1 text-rose-400 font-medium text-[11px]">
                                    <XCircle className="w-3.5 h-3.5" /> Violated
                                  </span>
                                )}
                              </td>
                              <td className="p-3">
                                {s.claimMathAccurate ? (
                                  <span className="inline-flex items-center gap-1 text-blue-400 font-medium text-[11px]">
                                    <CheckCircle2 className="w-3.5 h-3.5" /> Verified
                                  </span>
                                ) : (
                                  <span className="inline-flex items-center gap-1 text-amber-400 font-medium text-[11px]">
                                    <AlertTriangle className="w-3.5 h-3.5" /> Deviation
                                  </span>
                                )}
                              </td>
                              <td className="p-3">
                                <div className="font-semibold text-foreground">{s.topRankedPolicy || "None (Filtered Out)"}</div>
                                <div className="text-[10px] text-muted">
                                  Expected: {s.expectedTopPolicy} {s.topMatchAligned && <span className="text-emerald-400">✓</span>}
                                </div>
                              </td>
                              <td className="p-3 font-medium text-foreground">
                                {s.citationIntegrityScore <= 1 ? (s.citationIntegrityScore * 100).toFixed(0) : s.citationIntegrityScore.toFixed(0)}%
                              </td>
                              <td className="p-3 text-muted font-mono text-[11px]">
                                {s.executionTimeMs} ms
                              </td>
                              <td className="p-3">
                                <span
                                  className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                                    s.passed
                                      ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                                      : "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                                  }`}
                                >
                                  {s.passed ? "PASSED" : "FAILED"}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </>
              )}
            </div>
          )}

          {/* TAB 6: PHASE 5.6 AUTOMATED POLICY DISCOVERY & DATABASE-DRIVEN INGESTION */}
          {activeTab === "discovery" && (
            <div className="space-y-6">
              {/* Header with trigger button */}
              <div className="glass p-5 rounded-2xl border border-white/10 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-5 h-5 text-primary" />
                    <h3 className="text-base font-bold text-foreground">Official-Source Discovery & Controlled Ingestion Engine</h3>
                    <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-primary/10 text-primary border border-primary/20">
                      Phase 5.6
                    </span>
                  </div>
                  <p className="text-xs text-muted mt-1 max-w-2xl">
                    Inspects registered official insurer portals, extracts genuine policy PDF candidates, validates IRDAI UINs,
                    and routes qualitative contractual clauses through strict human review before live catalog publication.
                  </p>
                </div>
                <button
                  onClick={handleTriggerDiscovery}
                  disabled={isTriggeringDiscovery}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-semibold bg-gradient-to-r from-primary to-accent text-white shadow-lg shadow-primary/25 hover:opacity-90 transition-all disabled:opacity-50 whitespace-nowrap"
                >
                  <RefreshCw className={`w-4 h-4 ${isTriggeringDiscovery ? "animate-spin" : ""}`} />
                  {isTriggeringDiscovery ? "Scanning Portals..." : "Scan Official Portals"}
                </button>
              </div>

              {/* Discovery Metric Counters */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="glass p-4 rounded-2xl border border-white/5">
                  <span className="text-xs text-muted font-medium">Discovered Candidates</span>
                  <p className="text-2xl font-bold mt-1 text-foreground">{discoveryCandidates.length}</p>
                  <p className="text-[11px] text-muted">Across all lifecycle stages</p>
                </div>
                <div className="glass p-4 rounded-2xl border border-white/5">
                  <span className="text-xs text-muted font-medium">Pending Review Gate</span>
                  <p className="text-2xl font-bold mt-1 text-amber-400">
                    {discoveryCandidates.filter((c) => c.lifecycleStage === "PENDING_REVIEW").length}
                  </p>
                  <p className="text-[11px] text-muted">Awaiting human review</p>
                </div>
                <div className="glass p-4 rounded-2xl border border-white/5">
                  <span className="text-xs text-muted font-medium">Published & Active</span>
                  <p className="text-2xl font-bold mt-1 text-emerald-400">
                    {discoveryCandidates.filter((c) => c.lifecycleStage === "PUBLISHED_ELIGIBLE").length}
                  </p>
                  <p className="text-[11px] text-muted">Live in recommendations</p>
                </div>
                <div className="glass p-4 rounded-2xl border border-white/5">
                  <span className="text-xs text-muted font-medium">Completed Runs</span>
                  <p className="text-2xl font-bold mt-1 text-sky-400">{discoveryRuns.length}</p>
                  <p className="text-[11px] text-muted">Audit trail preserved</p>
                </div>
              </div>

              {/* Recent Discovery Runs */}
              <div className="glass p-5 rounded-2xl border border-white/5 space-y-3">
                <h4 className="text-sm font-bold text-foreground flex items-center gap-2">
                  <Clock className="w-4 h-4 text-sky-400" />
                  Recent Official-Source Discovery Runs
                </h4>
                {discoveryRuns.length === 0 ? (
                  <p className="text-xs text-muted py-3">No discovery runs executed yet. Click &quot;Scan Official Portals&quot; to begin.</p>
                ) : (
                  <div className="space-y-2">
                    {discoveryRuns.slice(0, 3).map((run) => (
                      <div
                        key={run.id}
                        className="p-3.5 rounded-xl bg-white/5 border border-white/5 flex flex-col md:flex-row md:items-center justify-between gap-2 text-xs"
                      >
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                              run.status === "COMPLETED"
                                ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                                : run.status === "IN_PROGRESS"
                                ? "bg-sky-500/10 text-sky-400 border border-sky-500/20 animate-pulse"
                                : "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                            }`}
                          >
                            {run.status}
                          </span>
                          <span className="font-mono text-muted text-[11px]">[{run.triggerType}]</span>
                          <span className="text-foreground font-semibold">
                            Discovered: {run.candidatesDiscovered} (New: {run.candidatesNew}, Dups: {run.candidatesDuplicate}, Acquired: {run.candidatesAcquired})
                          </span>
                        </div>
                        <div className="text-[11px] text-muted flex items-center gap-3">
                          <span>Scanned: {run.sourcesScanned} sources</span>
                          <span>{run.startedAt ? new Date(run.startedAt).toLocaleTimeString() : ""}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Candidates Pipeline Table */}
              <div className="glass p-5 rounded-2xl border border-white/5 space-y-4">
                <h4 className="text-sm font-bold text-foreground flex items-center gap-2">
                  <Layers className="w-4 h-4 text-primary" />
                  Discovered Product Candidates & Ingestion Pipeline
                </h4>
                {discoveryCandidates.length === 0 ? (
                  <p className="text-xs text-muted py-6 text-center">No candidate policies found. Execute a discovery run to ingest official products.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead>
                        <tr className="border-b border-white/10 text-muted text-[11px]">
                          <th className="pb-2 font-medium">Candidate Product</th>
                          <th className="pb-2 font-medium">Insurer</th>
                          <th className="pb-2 font-medium">IRDAI UIN</th>
                          <th className="pb-2 font-medium">Stage</th>
                          <th className="pb-2 font-medium">Provenance / SHA-256</th>
                          <th className="pb-2 font-medium text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-white/5">
                        {discoveryCandidates.map((c) => (
                          <tr key={c.id} className="hover:bg-white/5 transition-colors">
                            <td className="py-3 pr-2">
                              <span className="font-bold text-foreground block">{c.productName}</span>
                              <span className="text-[10px] text-muted font-mono">{c.normalizedProductCode}</span>
                            </td>
                            <td className="py-3 pr-2 text-muted">{c.insurerName}</td>
                            <td className="py-3 pr-2">
                              <span className="font-mono text-xs text-foreground block">{c.irdaUin || "Pending UIN"}</span>
                              <span className="text-[10px] text-muted">{c.uinRevision}</span>
                            </td>
                            <td className="py-3 pr-2">
                              <span
                                className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                                  c.lifecycleStage === "PUBLISHED_ELIGIBLE"
                                    ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                                    : c.lifecycleStage === "PENDING_REVIEW"
                                    ? "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                                    : c.lifecycleStage === "ACQUIRED"
                                    ? "bg-sky-500/10 text-sky-400 border border-sky-500/20"
                                    : "bg-white/10 text-muted"
                                }`}
                              >
                                {c.lifecycleStage}
                              </span>
                            </td>
                            <td className="py-3 pr-2">
                              {c.verifiedSha256 ? (
                                <span className="font-mono text-[10px] text-purple-300 block" title={c.verifiedSha256}>
                                  SHA: {c.verifiedSha256.substring(0, 16)}...
                                </span>
                              ) : (
                                <span className="text-[10px] text-muted block">Unacquired</span>
                              )}
                              {c.finalAcquisitionUrl && (
                                <a
                                  href={c.finalAcquisitionUrl}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="text-[10px] text-primary hover:underline flex items-center gap-1"
                                >
                                  Source Doc <ExternalLink className="w-2.5 h-2.5" />
                                </a>
                              )}
                            </td>
                            <td className="py-3 text-right">
                              {c.lifecycleStage === "ACQUISITION_PENDING" && (
                                <button
                                  onClick={() => handleAcquireCandidate(c.id)}
                                  disabled={operatingCandidateId === c.id}
                                  className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-sky-500/10 text-sky-400 hover:bg-sky-500/20 border border-sky-500/20 transition-all"
                                >
                                  {operatingCandidateId === c.id ? "Acquiring..." : "Acquire Doc"}
                                </button>
                              )}
                              {c.lifecycleStage === "ACQUIRED" && (
                                <button
                                  onClick={() => handleExtractCandidate(c.id)}
                                  disabled={operatingCandidateId === c.id}
                                  className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-purple-500/10 text-purple-400 hover:bg-purple-500/20 border border-purple-500/20 transition-all"
                                >
                                  {operatingCandidateId === c.id ? "Extracting..." : "Extract Clauses"}
                                </button>
                              )}
                              {c.lifecycleStage === "PENDING_REVIEW" && (
                                <div className="flex items-center justify-end gap-1.5">
                                  <button
                                    onClick={() => setSelectedCandidate(c)}
                                    className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-gradient-to-r from-amber-500 to-orange-500 text-white shadow-md hover:opacity-90 transition-all"
                                  >
                                    Review & Publish
                                  </button>
                                  <button
                                    onClick={() => handleRejectDiscoveryCandidate(c.id, c.productName)}
                                    className="px-2 py-1 rounded-lg text-[11px] font-semibold bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 border border-rose-500/20 transition-all"
                                  >
                                    Reject
                                  </button>
                                </div>
                              )}
                              {c.lifecycleStage === "PUBLISHED_ELIGIBLE" && (
                                <span className="text-emerald-400 flex items-center justify-end gap-1 font-semibold text-[11px]">
                                  <CheckCircle2 className="w-3.5 h-3.5" /> Published
                                </span>
                              )}
                              {c.lifecycleStage === "REJECTED" && (
                                <span className="text-rose-400 flex items-center justify-end gap-1 font-semibold text-[11px]" title={c.rejectionReason || "Rejected"}>
                                  <XCircle className="w-3.5 h-3.5" /> Rejected
                                </span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 7: REVIEWER CONFLICT RESOLUTION QUEUE (Phase 6.4) */}
          {activeTab === "conflicts" && (
            <div className="space-y-4">
              {/* Header Status Card */}
              <div className="glass p-5 rounded-2xl border border-amber-500/20 bg-gradient-to-r from-amber-500/10 via-amber-500/5 to-transparent space-y-3">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-amber-500/20 border border-amber-500/30 flex items-center justify-center flex-shrink-0">
                      <AlertTriangle className="w-5 h-5 text-amber-400" />
                    </div>
                    <div>
                      <h3 className="text-sm sm:text-base font-bold text-foreground">
                        Reviewer Evidence Conflict Resolution Console
                      </h3>
                      <p className="text-xs text-muted">
                        Phase 6.4 Prioritized Adjudication Queue • Extracted clauses with competing candidates or definition leaks
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="px-3 py-1 rounded-full text-xs font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30">
                      {conflicts.length} Pending Conflicts
                    </span>
                    <button
                      onClick={loadData}
                      className="p-2 rounded-xl glass hover:bg-white/10 text-muted hover:text-foreground transition-colors"
                      title="Refresh Queue"
                    >
                      <RefreshCw className="w-4 h-4" />
                    </button>
                  </div>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 pt-2 border-t border-white/5 text-xs">
                  <div className="p-2.5 rounded-xl bg-black/20 border border-white/5">
                    <span className="text-[10px] text-muted uppercase font-bold tracking-wider block">Verification Gate</span>
                    <span className="text-emerald-400 font-semibold flex items-center gap-1 mt-0.5">
                      <CheckCircle2 className="w-3.5 h-3.5" /> Enforced (Zero unverified leaks)
                    </span>
                  </div>
                  <div className="p-2.5 rounded-xl bg-black/20 border border-white/5">
                    <span className="text-[10px] text-muted uppercase font-bold tracking-wider block">Pipeline Status</span>
                    <span className="text-cyan-400 font-semibold flex items-center gap-1 mt-0.5">
                      <Clock className="w-3.5 h-3.5" /> Frozen &amp; Production Guarded
                    </span>
                  </div>
                  <div className="p-2.5 rounded-xl bg-black/20 border border-white/5">
                    <span className="text-[10px] text-muted uppercase font-bold tracking-wider block">Adjudication Policy</span>
                    <span className="text-amber-300 font-semibold flex items-center gap-1 mt-0.5">
                      <Shield className="w-3.5 h-3.5" /> Operative Clauses Trump Definitions
                    </span>
                  </div>
                </div>
              </div>

              {/* Conflict Items List */}
              {conflicts.length === 0 ? (
                <div className="glass p-12 rounded-2xl border border-white/5 text-center">
                  <CheckCircle2 className="w-12 h-12 text-emerald-400 mx-auto mb-3 opacity-80" />
                  <h3 className="text-base font-bold text-foreground">Zero Unresolved Conflicts</h3>
                  <p className="text-xs text-muted max-w-md mx-auto mt-1">
                    All extracted contractual clauses have been reconciled or adjudicated. The catalogue verification baseline is fully aligned with statutory policy documents.
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  {conflicts.map((item, idx) => (
                    <div
                      key={item.extractionId || idx}
                      className="glass p-5 rounded-2xl border border-white/10 hover:border-amber-500/30 transition-all space-y-4"
                    >
                      {/* Item Header */}
                      <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-2.5 border-b border-white/5 pb-3">
                        <div className="space-y-1">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30">
                              {item.taxonomyCategory}
                            </span>
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-primary/20 text-primary-light border border-primary/30 font-mono">
                              {item.fieldName}
                            </span>
                            <span className="text-xs text-muted">
                              Page {item.pageNumber ?? "?"}
                            </span>
                          </div>
                          <h4 className="text-sm font-bold text-foreground">
                            {item.productName || "Health Insurance Product"}
                          </h4>
                          <p className="text-xs text-muted">
                            {item.insurerName || "Authoritative Insurer"} • UIN: <span className="font-mono">{item.canonicalUin || "REGISTRY_MATCH"}</span>
                          </p>
                        </div>
                        <div className="text-left sm:text-right">
                          <span className="text-[10px] text-rose-400 font-semibold bg-rose-500/10 px-2 py-1 rounded border border-rose-500/20 block sm:inline-block">
                            Discrepancy: {item.conflictReason || "Competing Values Detected"}
                          </span>
                        </div>
                      </div>

                      {/* Side-by-Side Candidates */}
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                        {/* Primary Candidate */}
                        <div className="p-3.5 rounded-xl bg-black/30 border border-amber-500/20 space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="text-[10px] font-bold uppercase tracking-wider text-amber-400 flex items-center gap-1">
                              <AlertCircle className="w-3 h-3" /> Extracted Candidate A
                            </span>
                            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-white/5 text-muted">
                              Role: {item.evidenceRole}
                            </span>
                          </div>
                          <div className="space-y-1">
                            <div className="text-sm font-bold text-foreground">
                              {item.candidateValue}
                              {item.numericValue !== null && item.numericValue !== undefined && (
                                <span className="ml-2 text-xs text-muted font-normal">
                                  (Numeric: {item.numericValue} {item.unit})
                                </span>
                              )}
                            </div>
                            {item.verbatimClauseText && (
                              <div className="p-2 rounded bg-black/40 border border-white/5 text-[11px] text-muted italic font-mono leading-relaxed line-clamp-3">
                                &ldquo;{item.verbatimClauseText}&rdquo;
                              </div>
                            )}
                          </div>
                        </div>

                        {/* Competing Candidate(s) */}
                        <div className="p-3.5 rounded-xl bg-black/20 border border-white/5 space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="text-[10px] font-bold uppercase tracking-wider text-muted flex items-center gap-1">
                              <Scale className="w-3 h-3" /> Conflicting Candidate(s)
                            </span>
                            {item.conflictingCandidates && item.conflictingCandidates.length > 0 && (
                              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-white/5 text-muted">
                                {item.conflictingCandidates.length} Alternate{item.conflictingCandidates.length > 1 ? "s" : ""}
                              </span>
                            )}
                          </div>
                          {item.conflictingCandidates && item.conflictingCandidates.length > 0 ? (
                            <div className="space-y-2">
                              {item.conflictingCandidates.map((alt, aIdx) => (
                                <div key={aIdx} className="space-y-1">
                                  <div className="text-xs font-semibold text-slate-300">
                                    {alt.candidateValue || "Alternate Clause Value"}
                                    {alt.numericValue !== undefined && alt.numericValue !== null && (
                                      <span className="ml-1.5 text-[11px] text-muted font-normal">
                                        ({alt.numericValue} {alt.unit || ""})
                                      </span>
                                    )}
                                    <span className="ml-2 text-[10px] text-muted/70">
                                      • Pg {alt.originalPageNumber ?? "?"} ({alt.evidenceRole || "EVIDENCE"})
                                    </span>
                                  </div>
                                  {alt.originalClauseText && (
                                    <div className="p-2 rounded bg-black/40 border border-white/5 text-[11px] text-muted italic font-mono leading-relaxed line-clamp-2">
                                      &ldquo;{alt.originalClauseText}&rdquo;
                                    </div>
                                  )}
                                </div>
                              ))}
                            </div>
                          ) : (
                            <div className="text-xs text-muted italic py-3 text-center">
                              Discrepancy flagged by heuristic rule (e.g. definition vs operative coverage limit). No secondary candidate recorded.
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Action Bar */}
                      <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-white/5">
                        <button
                          type="button"
                          onClick={() => handleRejectConflict(item)}
                          disabled={isAdjudicating}
                          className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-rose-500/10 text-rose-300 hover:bg-rose-500/20 border border-rose-500/20 transition-all flex items-center gap-1"
                        >
                          <X className="w-3.5 h-3.5" /> Reject / Invalidate
                        </button>
                        <button
                          type="button"
                          onClick={() => handleOpenCorrection(item)}
                          disabled={isAdjudicating}
                          className="px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-primary/20 text-primary-light hover:bg-primary/30 border border-primary/30 transition-all flex items-center gap-1"
                        >
                          <Edit3 className="w-3.5 h-3.5" /> Manual Correction
                        </button>
                        <button
                          type="button"
                          onClick={() => handleApproveConflict(item)}
                          disabled={isAdjudicating}
                          className="px-4 py-1.5 rounded-xl text-xs font-semibold bg-gradient-to-r from-emerald-500 to-teal-500 text-white shadow-md shadow-emerald-500/20 hover:opacity-90 transition-all flex items-center gap-1"
                        >
                          <Check className="w-3.5 h-3.5" /> Approve Candidate A
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </main>

      {/* REVIEW & PUBLISH MODAL */}
      <AnimatePresence>
        {selectedDraft && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="glass p-6 rounded-3xl border border-white/10 max-w-2xl w-full max-h-[90vh] overflow-y-auto space-y-4"
            >
              <div className="flex items-start justify-between border-b border-white/10 pb-4">
                <div>
                  <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/10 text-amber-400 border border-amber-500/20">
                    Draft Review & Approval Gate
                  </span>
                  <h3 className="text-lg font-bold text-foreground mt-1">{selectedDraft.policyName}</h3>
                  <p className="text-xs text-muted">{selectedDraft.provider} • UIN: {selectedDraft.irdaUin}</p>
                </div>
                <button
                  onClick={() => setSelectedDraft(null)}
                  className="p-1 rounded-lg text-muted hover:text-foreground hover:bg-white/10"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Contradiction Warning */}
              {selectedDraft.contractUncertainFields && selectedDraft.contractUncertainFields.length > 0 && (
                <div className="p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/30 text-amber-200 text-xs space-y-1">
                  <div className="flex items-center gap-1.5 font-bold">
                    <AlertTriangle className="w-4 h-4 text-amber-400" />
                    Contractual Contradictions Detected:
                  </div>
                  <ul className="list-disc pl-5 text-[11px] space-y-0.5 text-amber-300">
                    {selectedDraft.contractUncertainFields.map((f: string, i: number) => (
                      <li key={i}>{f}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Extracted Terms Preview */}
              <div className="grid grid-cols-2 gap-3 text-xs bg-white/5 p-4 rounded-2xl border border-white/5">
                <div>
                  <span className="text-muted block text-[10px]">Room Rent:</span>
                  <span className="font-semibold text-foreground">
                    {selectedDraft.contractTerms?.roomRentLimit || "Single Private AC"}
                  </span>
                </div>
                <div>
                  <span className="text-muted block text-[10px]">Co-Payment:</span>
                  <span className="font-semibold text-foreground">
                    {selectedDraft.contractTerms?.copayPercentage ?? 0}%
                  </span>
                </div>
                <div>
                  <span className="text-muted block text-[10px]">PED Waiting Period:</span>
                  <span className="font-semibold text-foreground">
                    {selectedDraft.contractTerms?.waitingPeriodPedMonths ?? 36} months
                  </span>
                </div>
                <div>
                  <span className="text-muted block text-[10px]">Restoration:</span>
                  <span className="font-semibold text-foreground">
                    {selectedDraft.contractTerms?.restorationBenefit || "100% Recharge"}
                  </span>
                </div>
              </div>

              {/* Policy Change Impact Analyzer Section */}
              {selectedDraft.changeImpactReport && (
                <div className="space-y-3 pt-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Scale className="w-4 h-4 text-primary-light" />
                      <h4 className="text-xs font-bold text-foreground">Policy Change Impact Analysis</h4>
                    </div>
                    <div className="flex items-center gap-1.5">
                      {selectedDraft.changeImpactReport.materialChangesDetected ? (
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30 flex items-center gap-1">
                          <AlertTriangle className="w-3 h-3 text-amber-400" /> Material Changes Detected
                        </span>
                      ) : (
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3 text-emerald-400" /> Non-Material Update
                        </span>
                      )}
                      {selectedDraft.changeImpactReport.requiresSeniorReview && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-300 border border-rose-500/30 flex items-center gap-1">
                          <ShieldAlert className="w-3 h-3 text-rose-400" /> Senior Review Mandated
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Contractual Fact Diffs Table */}
                  {selectedDraft.changeImpactReport.contractualDiffs && selectedDraft.changeImpactReport.contractualDiffs.length > 0 && (
                    <div className="rounded-2xl border border-white/10 overflow-hidden text-xs">
                      <div className="bg-white/5 px-3 py-2 text-[11px] font-semibold text-muted border-b border-white/10 grid grid-cols-12 gap-2">
                        <div className="col-span-3">Contractual Clause</div>
                        <div className="col-span-3">Prior Revision ({selectedDraft.changeImpactReport.previousRevision || "None"})</div>
                        <div className="col-span-3">New Revision ({selectedDraft.changeImpactReport.newRevision || "Draft"})</div>
                        <div className="col-span-3">Impact & Severity</div>
                      </div>
                      <div className="divide-y divide-white/5 bg-background/50">
                        {selectedDraft.changeImpactReport.contractualDiffs.map((diff: FactDiff, idx: number) => (
                          <div key={idx} className="p-3 grid grid-cols-12 gap-2 items-start hover:bg-white/[0.02]">
                            <div className="col-span-3 font-medium text-foreground">
                              {diff.fieldName}
                            </div>
                            <div className="col-span-3 text-rose-400/80 line-through text-[11px]">
                              {diff.previousValue}
                            </div>
                            <div className="col-span-3 text-emerald-400 font-semibold text-[11px]">
                              {diff.newValue}
                            </div>
                            <div className="col-span-3 space-y-1">
                              <span
                                className={`px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider ${
                                  diff.severity === "CRITICAL"
                                    ? "bg-rose-500/20 text-rose-300 border border-rose-500/30"
                                    : diff.severity === "MODERATE"
                                    ? "bg-amber-500/20 text-amber-300 border border-amber-500/30"
                                    : "bg-blue-500/20 text-blue-300 border border-blue-500/30"
                                }`}
                              >
                                {diff.severity}
                              </span>
                              <p className="text-[10px] text-muted leading-tight">{diff.impactExplanation}</p>
                              {diff.newEvidenceCitation && (
                                <p className="text-[9px] text-muted/60 font-mono">
                                  Cite: {diff.newEvidenceCitation}
                                </p>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Downstream Engine Impact Forecasts */}
                  {(selectedDraft.changeImpactReport.rankingScoreImpacts?.length > 0 ||
                    selectedDraft.changeImpactReport.eligibilityImpacts?.length > 0 ||
                    selectedDraft.changeImpactReport.claimSimulationImpacts?.length > 0) && (
                    <div className="p-3.5 rounded-2xl bg-white/5 border border-white/10 text-xs space-y-2">
                      <div className="font-semibold text-foreground flex items-center gap-1.5 text-[11px]">
                        <TrendingDown className="w-3.5 h-3.5 text-amber-400" />
                        Downstream Recommendation Engine Impact Forecast:
                      </div>
                      <div className="grid grid-cols-1 md:grid-cols-3 gap-2 text-[11px]">
                        {selectedDraft.changeImpactReport.eligibilityImpacts?.length > 0 && (
                          <div className="p-2 rounded-xl bg-black/30 border border-white/5 space-y-1">
                            <span className="text-[10px] text-muted uppercase font-bold tracking-wider">Eligibility Rules</span>
                            {selectedDraft.changeImpactReport.eligibilityImpacts.map((e: string, i: number) => (
                              <p key={i} className="text-amber-300/90 text-[10px]">• {e}</p>
                            ))}
                          </div>
                        )}
                        {selectedDraft.changeImpactReport.rankingScoreImpacts?.length > 0 && (
                          <div className="p-2 rounded-xl bg-black/30 border border-white/5 space-y-1">
                            <span className="text-[10px] text-muted uppercase font-bold tracking-wider">Scoring Penalties</span>
                            {selectedDraft.changeImpactReport.rankingScoreImpacts.map((r: string, i: number) => (
                              <p key={i} className="text-rose-300/90 text-[10px]">• {r}</p>
                            ))}
                          </div>
                        )}
                        {selectedDraft.changeImpactReport.claimSimulationImpacts?.length > 0 && (
                          <div className="p-2 rounded-xl bg-black/30 border border-white/5 space-y-1">
                            <span className="text-[10px] text-muted uppercase font-bold tracking-wider">Claim Simulations</span>
                            {selectedDraft.changeImpactReport.claimSimulationImpacts.map((c: string, i: number) => (
                              <p key={i} className="text-cyan-300/90 text-[10px]">• {c}</p>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Reviewer Notes Input */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Reviewer Audit Justification & Notes:
                </label>
                <textarea
                  value={reviewerNotes}
                  onChange={(e) => setReviewerNotes(e.target.value)}
                  placeholder="e.g., Verified against official Customer Information Sheet v2024. Contractual clauses confirmed correct."
                  rows={3}
                  className="w-full px-3 py-2 text-xs rounded-xl bg-white/5 border border-white/10 text-foreground focus:outline-none focus:border-primary"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-white/10">
                <button
                  type="button"
                  onClick={() => handleRejectDraft(selectedDraft.id)}
                  disabled={isSubmittingReview}
                  className="px-4 py-2 rounded-xl text-xs font-semibold bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 border border-rose-500/20 transition-all"
                >
                  Reject / Flag Outdated
                </button>
                <button
                  type="button"
                  onClick={() => handleApproveDraft(selectedDraft.id)}
                  disabled={isSubmittingReview}
                  className="px-5 py-2 rounded-xl text-xs font-semibold bg-gradient-to-r from-emerald-500 to-teal-500 text-white shadow-lg shadow-emerald-500/25 hover:opacity-90 transition-all"
                >
                  {isSubmittingReview ? "Publishing..." : "Approve & Publish to Live Catalog"}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* REGISTER SOURCE MODAL */}
      <AnimatePresence>
        {showAddSourceModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="glass p-6 rounded-3xl border border-white/10 max-w-lg w-full space-y-4"
            >
              <div className="flex items-center justify-between border-b border-white/10 pb-3">
                <h3 className="text-base font-bold text-foreground">Register Official Document Source</h3>
                <button
                  onClick={() => setShowAddSourceModal(false)}
                  className="p-1 rounded-lg text-muted hover:text-foreground"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleCreateSource} className="space-y-3 text-xs">
                <div>
                  <label className="block text-muted mb-1">Source Name *</label>
                  <input
                    type="text"
                    required
                    value={newSource.sourceName || ""}
                    onChange={(e) => setNewSource({ ...newSource, sourceName: e.target.value })}
                    placeholder="e.g., Care Advantage Official Terms"
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground"
                  />
                </div>

                <div>
                  <label className="block text-muted mb-1">Insurer / Provider *</label>
                  <input
                    type="text"
                    required
                    value={newSource.provider || ""}
                    onChange={(e) => setNewSource({ ...newSource, provider: e.target.value })}
                    placeholder="e.g., Care Health Insurance Ltd."
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground"
                  />
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-muted mb-1">IRDAI UIN (if known)</label>
                    <input
                      type="text"
                      value={newSource.irdaUin || ""}
                      onChange={(e) => setNewSource({ ...newSource, irdaUin: e.target.value })}
                      placeholder="e.g., CHIHLIP21161V012021"
                      className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground"
                    />
                  </div>
                  <div>
                    <label className="block text-muted mb-1">Document Type</label>
                    <select
                      value={newSource.documentType || "POLICY_WORDING"}
                      onChange={(e) => setNewSource({ ...newSource, documentType: e.target.value })}
                      className="w-full px-3 py-2 rounded-xl bg-background border border-white/10 text-foreground"
                    >
                      <option value="POLICY_WORDING">Policy Wording</option>
                      <option value="CUSTOMER_INFORMATION_SHEET">Customer Information Sheet</option>
                      <option value="PROSPECTUS">Prospectus</option>
                      <option value="RATE_TABLE">Rate Table</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-muted mb-1">Official Document URL *</label>
                  <input
                    type="url"
                    required
                    value={newSource.sourceUrl || ""}
                    onChange={(e) => setNewSource({ ...newSource, sourceUrl: e.target.value })}
                    placeholder="https://example.com/downloads/policy-wordings.pdf"
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground"
                  />
                </div>

                <div className="flex items-center justify-end gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowAddSourceModal(false)}
                    className="px-4 py-2 rounded-xl text-muted hover:text-foreground"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-5 py-2 rounded-xl bg-primary text-white font-semibold hover:opacity-90"
                  >
                    Save & Monitor Source
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* CURATED OFFICIAL SOURCE REGISTRATION MODAL */}
      <AnimatePresence>
        {showCuratedModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="glass p-6 rounded-3xl border border-emerald-500/20 max-w-xl w-full space-y-4 shadow-2xl"
            >
              <div className="flex items-center justify-between border-b border-white/10 pb-3">
                <div className="flex items-center gap-2">
                  <span className="p-1.5 rounded-lg bg-emerald-500/20 text-emerald-400">
                    <ShieldCheck className="w-5 h-5" />
                  </span>
                  <div>
                    <h3 className="text-base font-bold text-foreground">Curate & Stage Official Policy PDF</h3>
                    <p className="text-[11px] text-muted">
                      Manual fallback for authorized reviewers when automated discovery encounters CDN bot shields
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => setShowCuratedModal(false)}
                  className="p-1 rounded-lg text-muted hover:text-foreground"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-xs text-emerald-300/90 leading-relaxed">
                <strong>Strict Governance Enforced:</strong> The URL will be validated against the approved insurer domain allowlist, downloaded over a socket-pinned TLS connection with RFC 6125 SAN check, verified for <code className="text-white font-mono">%PDF-</code> magic bytes (max 50MB), hashed with SHA-256, and parsed for statutory IRDAI UIN. The result is staged as <strong>EXTRACTION_PENDING_REVIEW</strong> and will not be published without human review.
              </div>

              <form onSubmit={handleRegisterCuratedSource} className="space-y-3 text-xs">
                <div>
                  <label className="block text-muted mb-1 font-semibold">
                    Official Document URL * <span className="text-[10px] text-muted font-normal">(HTTPS official insurer or IRDAI domain)</span>
                  </label>
                  <input
                    type="url"
                    required
                    value={curatedForm.sourceUrl}
                    onChange={(e) => setCuratedForm({ ...curatedForm, sourceUrl: e.target.value })}
                    placeholder="https://uiic.co.in/web/sites/default/files/basic-documents/List-of-Retail-Products.pdf"
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground font-mono text-[11px] focus:outline-none focus:border-emerald-500"
                  />
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-muted mb-1">Insurer / Provider</label>
                    <input
                      type="text"
                      value={curatedForm.provider}
                      onChange={(e) => setCuratedForm({ ...curatedForm, provider: e.target.value })}
                      placeholder="e.g., United India Insurance"
                      className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground"
                    />
                  </div>
                  <div>
                    <label className="block text-muted mb-1">Product Name</label>
                    <input
                      type="text"
                      value={curatedForm.canonicalProductName}
                      onChange={(e) => setCuratedForm({ ...curatedForm, canonicalProductName: e.target.value })}
                      placeholder="e.g., Individual Health Insurance"
                      className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-muted mb-1">Expected IRDAI UIN (optional)</label>
                    <input
                      type="text"
                      value={curatedForm.expectedUin}
                      onChange={(e) => setCuratedForm({ ...curatedForm, expectedUin: e.target.value })}
                      placeholder="e.g., UIIPAIP26041V012526"
                      className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground font-mono uppercase"
                    />
                  </div>
                  <div>
                    <label className="block text-muted mb-1">Expected Revision (optional)</label>
                    <input
                      type="text"
                      value={curatedForm.expectedRevision}
                      onChange={(e) => setCuratedForm({ ...curatedForm, expectedRevision: e.target.value })}
                      placeholder="e.g., V012526"
                      className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground font-mono"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-muted mb-1">Source Name / Identifier</label>
                  <input
                    type="text"
                    value={curatedForm.sourceName}
                    onChange={(e) => setCuratedForm({ ...curatedForm, sourceName: e.target.value })}
                    placeholder="e.g., UIIC Retail Products Schedule 2024"
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground"
                  />
                </div>

                <div>
                  <label className="block text-muted mb-1">Reviewer Curation Notes &amp; Audit Trail</label>
                  <textarea
                    rows={2}
                    value={curatedForm.curationNotes}
                    onChange={(e) => setCuratedForm({ ...curatedForm, curationNotes: e.target.value })}
                    placeholder="e.g., Verified from official PSU downloads page. Contains latest statutory retail product wording."
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground resize-none"
                  />
                </div>

                <div className="flex items-center justify-end gap-2 pt-2 border-t border-white/10">
                  <button
                    type="button"
                    onClick={() => setShowCuratedModal(false)}
                    disabled={isSubmittingCurated}
                    className="px-4 py-2 rounded-xl text-muted hover:text-foreground"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmittingCurated}
                    className="px-5 py-2 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-500 text-white font-semibold shadow-lg shadow-emerald-500/25 hover:opacity-90 transition-all flex items-center gap-1.5"
                  >
                    {isSubmittingCurated ? (
                      <>
                        <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                        Validating &amp; Staging PDF...
                      </>
                    ) : (
                      <>
                        <ShieldCheck className="w-3.5 h-3.5" />
                        Validate, Download &amp; Stage Draft
                      </>
                    )}
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* PHASE 5.6: CANDIDATE REVIEW & PUBLICATION MODAL */}
      <AnimatePresence>
        {selectedCandidate && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="glass p-6 rounded-3xl border border-white/10 max-w-2xl w-full max-h-[90vh] overflow-y-auto space-y-4"
            >
              <div className="flex items-start justify-between border-b border-white/10 pb-4">
                <div>
                  <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/10 text-amber-400 border border-amber-500/20">
                    Mandatory Contractual Human Review Gate
                  </span>
                  <h3 className="text-lg font-bold text-foreground mt-1">{selectedCandidate.productName}</h3>
                  <p className="text-xs text-muted">
                    {selectedCandidate.insurerName} • UIN: {selectedCandidate.irdaUin || "Pending UIN"} ({selectedCandidate.uinRevision || "V01"})
                  </p>
                </div>
                <button
                  onClick={() => setSelectedCandidate(null)}
                  className="p-1 rounded-lg text-muted hover:text-foreground hover:bg-white/10"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Provenance details */}
              <div className="p-3.5 rounded-xl bg-white/5 border border-white/5 text-xs space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className="text-muted">Verified Physical SHA-256:</span>
                  <span className="font-mono text-[11px] text-purple-300">
                    {selectedCandidate.verifiedSha256 || "Unverified"}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted">Acquisition URL:</span>
                  <a
                    href={selectedCandidate.finalAcquisitionUrl || selectedCandidate.sourceUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="text-primary hover:underline flex items-center gap-1 text-[11px]"
                  >
                    Official Document <ExternalLink className="w-3 h-3" />
                  </a>
                </div>
              </div>

              {/* Uncertain contractual fields alert */}
              {selectedCandidate.contractUncertainFields && selectedCandidate.contractUncertainFields.length > 0 && (
                <div className="p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/30 text-amber-200 text-xs space-y-1">
                  <div className="flex items-center gap-1.5 font-bold">
                    <AlertTriangle className="w-4 h-4 text-amber-400" />
                    Ambiguous / Uncertain Terms Requiring Review:
                  </div>
                  <ul className="list-disc pl-5 text-[11px] space-y-0.5 text-amber-300">
                    {selectedCandidate.contractUncertainFields.map((field, idx) => (
                      <li key={idx}>Qualitative clause &quot;{field}&quot; requires human contractual confirmation.</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Extracted core clauses preview */}
              <div className="grid grid-cols-2 gap-3 text-xs bg-white/5 p-4 rounded-2xl border border-white/5">
                <div>
                  <span className="text-muted block text-[10px]">Room Rent Capping:</span>
                  <span className="font-semibold text-foreground">
                    {selectedCandidate.roomRentSummary || "Single Private AC Room (Verified)"}
                  </span>
                </div>
                <div>
                  <span className="text-muted block text-[10px]">Co-Payment Terms:</span>
                  <span className="font-semibold text-foreground">
                    {selectedCandidate.copaySummary || "No mandatory co-payment"}
                  </span>
                </div>
                <div>
                  <span className="text-muted block text-[10px]">PED Waiting Period:</span>
                  <span className="font-semibold text-foreground">
                    {selectedCandidate.pedWaitingSummary || "36 months"}
                  </span>
                </div>
                <div>
                  <span className="text-muted block text-[10px]">Restoration Benefit:</span>
                  <span className="font-semibold text-foreground">
                    {selectedCandidate.restorationSummary || "100% restoration"}
                  </span>
                </div>
              </div>

              {/* Reviewer audit trail input */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Reviewer Audit Justification & Notes:
                </label>
                <textarea
                  value={candidateNotes}
                  onChange={(e) => setCandidateNotes(e.target.value)}
                  placeholder="e.g., Reviewed against official policy wording PDF. Contractual terms, room rent, and PED waiting confirmed."
                  rows={3}
                  className="w-full px-3 py-2 text-xs rounded-xl bg-white/5 border border-white/10 text-foreground focus:outline-none focus:border-primary"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-white/10">
                <button
                  type="button"
                  onClick={() => setSelectedCandidate(null)}
                  disabled={isPublishingCandidate}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-muted hover:text-foreground"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handlePublishCandidate}
                  disabled={isPublishingCandidate}
                  className="px-5 py-2 rounded-xl text-xs font-semibold bg-gradient-to-r from-emerald-500 to-teal-500 text-white shadow-lg shadow-emerald-500/25 hover:opacity-90 transition-all flex items-center gap-1.5"
                >
                  {isPublishingCandidate ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      Publishing to MongoDB...
                    </>
                  ) : (
                    <>
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Approve &amp; Publish to Live Catalogue
                    </>
                  )}
                </button>
              </div>
            </motion.div>
          </div>
        )}

        {/* MANUAL EXTRACTION CORRECTION & ADJUDICATION MODAL */}
        {showCorrectionModal && selectedConflict && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="glass p-6 rounded-3xl border border-primary/20 max-w-xl w-full max-h-[90vh] overflow-y-auto space-y-4 shadow-2xl shadow-primary/10"
            >
              <div className="flex items-start justify-between border-b border-white/10 pb-4">
                <div>
                  <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-primary/20 text-primary-light border border-primary/30">
                    Reviewer Evidence Adjudication Console
                  </span>
                  <h3 className="text-base font-bold text-foreground mt-1">
                    Manual Extraction Correction
                  </h3>
                  <p className="text-xs text-muted">
                    {selectedConflict.productName} • Field: <span className="font-mono text-cyan-300">{selectedConflict.fieldName}</span>
                  </p>
                </div>
                <button
                  onClick={() => setShowCorrectionModal(false)}
                  className="p-1 rounded-lg text-muted hover:text-foreground hover:bg-white/10"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Original Extraction Info */}
              <div className="p-3.5 rounded-xl bg-black/30 border border-white/5 space-y-2 text-xs">
                <div className="flex items-center justify-between text-muted text-[11px]">
                  <span>Current Raw Candidate:</span>
                  <span className="font-bold text-amber-300">{selectedConflict.candidateValue}</span>
                </div>
                {selectedConflict.verbatimClauseText && (
                  <div className="p-2 rounded bg-black/40 border border-white/5 text-[11px] text-muted italic font-mono line-clamp-3">
                    &ldquo;{selectedConflict.verbatimClauseText}&rdquo;
                  </div>
                )}
              </div>

              {/* Correction Form Fields */}
              <div className="space-y-3 text-xs">
                <div>
                  <label className="font-semibold text-foreground block mb-1">
                    Corrected Display Value:
                  </label>
                  <input
                    type="text"
                    value={correctionForm.correctedValue}
                    onChange={(e) => setCorrectionForm({ ...correctionForm, correctedValue: e.target.value })}
                    placeholder="e.g. 0.0, Nil, 1% of Sum Insured, 24 Months"
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground text-xs focus:outline-none focus:border-primary"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="font-semibold text-foreground block mb-1">
                      Normalized Numeric:
                    </label>
                    <input
                      type="number"
                      step="any"
                      value={correctionForm.correctedNumeric}
                      onChange={(e) => setCorrectionForm({ ...correctionForm, correctedNumeric: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground text-xs focus:outline-none focus:border-primary"
                    />
                  </div>
                  <div>
                    <label className="font-semibold text-foreground block mb-1">
                      Measurement Unit:
                    </label>
                    <select
                      value={correctionForm.correctedUnit}
                      onChange={(e) => setCorrectionForm({ ...correctionForm, correctedUnit: e.target.value })}
                      className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-white/10 text-foreground text-xs focus:outline-none focus:border-primary"
                    >
                      <option value="PERCENTAGE">PERCENTAGE (%)</option>
                      <option value="CURRENCY_INR">CURRENCY_INR (₹)</option>
                      <option value="DAYS">DAYS</option>
                      <option value="MONTHS">MONTHS</option>
                      <option value="PROCEDURES_COUNT">PROCEDURES_COUNT</option>
                      <option value="HOURS">HOURS</option>
                      <option value="RATIO">RATIO</option>
                      <option value="TEXT">TEXT / CATEGORY</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="font-semibold text-foreground block mb-1">
                    Evidence Role:
                  </label>
                  <select
                    value={correctionForm.correctedEvidenceRole}
                    onChange={(e) => setCorrectionForm({ ...correctionForm, correctedEvidenceRole: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-white/10 text-foreground text-xs focus:outline-none focus:border-primary"
                  >
                    <option value="OPERATIVE_CLAUSE">OPERATIVE_CLAUSE (Policy Section Benefit Limit)</option>
                    <option value="BENEFIT_LIMIT">BENEFIT_LIMIT</option>
                    <option value="POLICY_SCHEDULE">POLICY_SCHEDULE</option>
                    <option value="DEFINITION">DEFINITION (Descriptive clause)</option>
                    <option value="WAITING_PERIOD">WAITING_PERIOD</option>
                    <option value="EXCLUSION">EXCLUSION</option>
                    <option value="CONDITION">CONDITION</option>
                  </select>
                </div>

                <div>
                  <label className="font-semibold text-foreground block mb-1">
                    Reviewer Justification &amp; Notes:
                  </label>
                  <textarea
                    rows={2}
                    value={correctionForm.notes}
                    onChange={(e) => setCorrectionForm({ ...correctionForm, notes: e.target.value })}
                    placeholder="e.g. Validated against official policy wording Section 4.2. Operative limit is Nil co-pay."
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-foreground text-xs focus:outline-none focus:border-primary"
                  />
                </div>

                <div className="flex items-center gap-2 pt-1">
                  <input
                    type="checkbox"
                    id="approveAfterCorrection"
                    checked={correctionForm.approveAfterCorrection}
                    onChange={(e) => setCorrectionForm({ ...correctionForm, approveAfterCorrection: e.target.checked })}
                    className="rounded border-white/10 text-primary focus:ring-0"
                  />
                  <label htmlFor="approveAfterCorrection" className="text-xs text-muted cursor-pointer">
                    Mark as verified &amp; approved immediately upon saving
                  </label>
                </div>
              </div>

              {/* Modal Actions */}
              <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-white/10">
                <button
                  type="button"
                  onClick={() => setShowCorrectionModal(false)}
                  disabled={isAdjudicating}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-muted hover:text-foreground"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleSubmitCorrection}
                  disabled={isAdjudicating}
                  className="px-5 py-2 rounded-xl text-xs font-semibold bg-primary hover:bg-primary/90 text-white shadow-lg shadow-primary/20 transition-all flex items-center gap-1.5"
                >
                  {isAdjudicating ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      Applying Correction...
                    </>
                  ) : (
                    <>
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Save &amp; Resolve Conflict
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
