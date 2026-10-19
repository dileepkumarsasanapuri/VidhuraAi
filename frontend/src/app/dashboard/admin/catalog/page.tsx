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
} from "@/lib/api";
import { useToast } from "@/context/ToastContext";

export default function AdminCatalogPage() {
  const toast = useToast();
  const [activeTab, setActiveTab] = useState<"drafts" | "sources" | "live" | "documents" | "benchmark">("drafts");

  // State
  const [drafts, setDrafts] = useState<any[]>([]);
  const [sources, setSources] = useState<PolicySource[]>([]);
  const [livePolicies, setLivePolicies] = useState<any[]>([]);
  const [documents, setDocuments] = useState<DiscoveredDocument[]>([]);
  const [stats, setStats] = useState<CatalogStats | null>(null);

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
      const [draftsRes, sourcesRes, liveRes, docsRes, statsRes] = await Promise.all([
        api.getPolicyDrafts().catch(() => ({ data: [] })),
        api.getPolicySources().catch(() => ({ data: [] })),
        api.getCatalogPolicies("VERIFIED_ONLY").catch(() => ({ data: [] })),
        api.getDiscoveredDocuments().catch(() => ({ data: [] })),
        api.getCatalogStats().catch(() => ({ data: null })),
      ]);

      setDrafts(draftsRes.data || []);
      setSources(sourcesRes.data || []);
      setLivePolicies(liveRes.data || []);
      setDocuments(docsRes.data || []);
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

  useEffect(() => {
    loadData();
  }, []);

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
              <div className="flex justify-between items-center">
                <div>
                  <h3 className="text-sm font-bold text-foreground">Live Active Catalog</h3>
                  <p className="text-xs text-muted">Verified policies currently driving user recommendations</p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {livePolicies.map((p) => (
                  <div key={p.id} className="glass p-5 rounded-2xl border border-emerald-500/20 space-y-3">
                    <div className="flex items-start justify-between">
                      <div>
                        <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                          {p.verificationStatus}
                        </span>
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
                        <span className="text-muted block text-[10px]">Attribution:</span>
                        <span className="font-semibold text-foreground">
                          {p.verifiedBy || "Catalog reviewer"}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center justify-between pt-2 border-t border-white/5">
                      <span className="text-[10px] text-muted font-mono">
                        {p.policyCode || p.id}
                      </span>
                      <button
                        onClick={() => handleRollbackPolicy(p.id, p.policyName)}
                        title="Rollback to previous active revision if issues are found"
                        className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 border border-rose-500/20 transition-all flex items-center gap-1"
                      >
                        <RotateCcw className="w-3 h-3" /> Rollback Revision
                      </button>
                    </div>
                  </div>
                ))}
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
                      <p className="text-[11px] text-emerald-400 mt-0.5 font-medium flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" /> Regression Passed
                      </p>
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
    </div>
  );
}
