function normalizeBaseUrl(url: string): string {
  return url.endsWith("/") ? url.slice(0, -1) : url;
}

function resolveApiBase(): string {
  if (typeof window !== "undefined") {
    // If an explicit non-localhost external API URL is configured, use it
    if (process.env.NEXT_PUBLIC_API_URL && !process.env.NEXT_PUBLIC_API_URL.includes("localhost")) {
      return normalizeBaseUrl(process.env.NEXT_PUBLIC_API_URL);
    }
    // Local dev on localhost:3000 without Nginx proxy
    const isLocalBrowser = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
    if (isLocalBrowser && window.location.port === "3000") {
      return normalizeBaseUrl(process.env.NEXT_PUBLIC_API_URL || "http://localhost:5000/api");
    }
    // Production / EC2 / Behind Nginx reverse proxy: use relative /api
    return "/api";
  }

  // Server-side (Node.js SSR / Container)
  const serverUrl = process.env.INTERNAL_API_URL || process.env.NEXT_PUBLIC_API_URL || "http://backend:5000/api";
  return normalizeBaseUrl(serverUrl);
}

class ApiClient {
  private customBase?: string;

  constructor(base?: string) {
    if (base && !base.includes("localhost:5000")) {
      this.customBase = normalizeBaseUrl(base);
    }
  }

  get base(): string {
    if (this.customBase) return this.customBase;
    return resolveApiBase();
  }

  private getUrl(path: string): string {
    const cleanPath = path.startsWith("/") ? path : `/${path}`;
    return `${this.base}${cleanPath}`;
  }

  private async request<T>(
    path: string,
    options: RequestInit = {}
  ): Promise<T> {
    const token = typeof window !== "undefined"
      ? (window.localStorage.getItem("vidhura_auth_token") || window.localStorage.getItem("claritysure_auth_token"))
      : null;
    const headers = {
      "Content-Type": "application/json",
      ...options.headers,
    } as Record<string, string>;

    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const url = this.getUrl(path);
    let res = await fetch(url, {
      ...options,
      headers,
    });

    // Auto-refresh token if 401 unauthorized on authenticated requests
    if (res.status === 401 && !path.startsWith("/auth/")) {
      const newToken = await this.refreshToken();
      if (newToken) {
        headers.Authorization = `Bearer ${newToken}`;
        res = await fetch(url, {
          ...options,
          headers,
        });
      }
    }

    if (!res.ok) {
      const error = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(error.error || error.message || `API Error: ${res.status}`);
    }

    return res.json();
  }

  async refreshToken(): Promise<string | null> {
    if (typeof window === "undefined") return null;
    const refreshToken = window.localStorage.getItem("vidhura_refresh_token");
    if (!refreshToken) return null;

    try {
      const res = await fetch(this.getUrl("/auth/refresh"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) {
        window.localStorage.removeItem("vidhura_auth_token");
        window.localStorage.removeItem("vidhura_refresh_token");
        return null;
      }
      const data = await res.json();
      if (data && data.token) {
        window.localStorage.setItem("vidhura_auth_token", data.token);
        if (data.refreshToken) {
          window.localStorage.setItem("vidhura_refresh_token", data.refreshToken);
        }
        return data.token;
      }
    } catch {
      return null;
    }
    return null;
  }

  // Health
  health() {
    return this.request<{ status: string; version: string }>("/health");
  }

  // Policies
  getPolicies() {
    return this.request<{ success: boolean; data: Policy[]; count: number }>("/policies");
  }

  getPolicy(id: string) {
    return this.request<{ success: boolean; data: Policy }>(`/policies/${id}`);
  }

  createPolicy(data: Partial<Policy>) {
    return this.request<{ success: boolean; data: Policy }>("/policies", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  updatePolicy(id: string, data: Partial<Policy>) {
    return this.request<{ success: boolean; data: Policy }>(`/policies/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  deletePolicy(id: string) {
    return this.request<{ success: boolean }>(`/policies/${id}`, {
      method: "DELETE",
    });
  }

  // Analysis
  analyzePolicy(file: File) {
    const formData = new FormData();
    formData.append("policyFile", file);

    const url = this.getUrl("/analyze");
    const token = typeof window !== "undefined"
      ? (window.localStorage.getItem("vidhura_auth_token") || window.localStorage.getItem("claritysure_auth_token"))
      : null;
    const headers: Record<string, string> = {};
    if (token) headers.Authorization = `Bearer ${token}`;

    return fetch(url, {
      method: "POST",
      body: formData,
      headers,
      // Omit Content-Type so browser sets it with the correct boundary
    }).then(async (res) => {
      if (!res.ok) {
        const error = await res.json().catch(() => ({ message: res.statusText }));
        throw new Error(error.message || `API Error: ${res.status}`);
      }
      return res.json() as Promise<{ success: boolean; data: PolicyAnalysis }>;
    });
  }

  analyzePolicyAsync(file: File) {
    const formData = new FormData();
    formData.append("policyFile", file);

    const url = this.getUrl("/analyze/async");
    const token = typeof window !== "undefined"
      ? (window.localStorage.getItem("vidhura_auth_token") || window.localStorage.getItem("claritysure_auth_token"))
      : null;
    const headers: Record<string, string> = {};
    if (token) headers.Authorization = `Bearer ${token}`;

    return fetch(url, {
      method: "POST",
      body: formData,
      headers,
    }).then(async (res) => {
      if (!res.ok) {
        const error = await res.json().catch(() => ({ message: res.statusText }));
        throw new Error(error.message || `API Error: ${res.status}`);
      }
      return res.json() as Promise<{
        success: boolean;
        data: {
          taskId: string;
          fileName: string;
          status: "PROCESSING" | "COMPLETED" | "FAILED";
          progress: number;
          step: string;
          message: string;
          cached: boolean;
          result?: PolicyAnalysis;
          error?: string;
        };
      }>;
    });
  }

  getAnalysisStatus(taskId: string) {
    return this.request<{
      success: boolean;
      data: {
        taskId: string;
        fileName: string;
        status: "PROCESSING" | "COMPLETED" | "FAILED";
        progress: number;
        step: string;
        message: string;
        cached: boolean;
        result?: PolicyAnalysis;
        error?: string;
      };
    }>(`/analyze/status/${taskId}`);
  }

  getAnalysisStreamUrl(taskId: string) {
    return this.getUrl(`/analyze/stream/${taskId}`);
  }

  // Scenario
  simulateScenario(question: string, policyId?: string, language?: string) {
    return this.request<{ success: boolean; data: ScenarioResult }>("/scenario", {
      method: "POST",
      body: JSON.stringify({ question, policyId, language }),
    });
  }

  // Recommendations
  generateRecommendations(data: RecommendationRequest) {
    return this.request<{ success: boolean; data: RecommendationResponse }>("/recommend", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  getRecommendationGuidance(data: GuidanceRequest) {
    return this.request<{ success: boolean; data: GuidanceResponse }>("/recommend/guidance", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  getRecommendationHistory() {
    return this.request<{ success: boolean; data: RecommendationHistoryItem[] }>("/recommend/history");
  }

  getRecommendationById(id: string) {
    return this.request<{ success: boolean; data: RecommendationHistoryItem }>(`/recommend/${id}`);
  }

  getPolicyCatalog() {
    return this.request<{ success: boolean; data: StructuredPolicy[] }>("/recommend/catalog");
  }

  // Policy Discovery & Scalable Catalog Ingestion Pipeline
  discoverPolicy(data: PolicyDiscoveryRequest) {
    return this.request<{ success: boolean; data: PolicyIngestionResult }>("/policies/discover", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  verifyPolicy(id: string, data: PolicyVerificationRequest) {
    return this.request<{ success: boolean; data: any }>(`/policies/${id}/verify`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  flagPolicy(id: string, reason?: string, status?: string) {
    const params = new URLSearchParams();
    if (reason) params.set("reason", reason);
    if (status) params.set("status", status);
    return this.request<{ success: boolean; data: any }>(`/policies/${id}/flag?${params.toString()}`, {
      method: "POST",
    });
  }

  rollbackPolicy(id: string, reason: string) {
    return this.request<{ success: boolean; data: any }>(
      `/policies/${id}/rollback?reason=${encodeURIComponent(reason)}`,
      { method: "POST" }
    );
  }

  getCatalogPolicies(status?: string) {
    const query = status ? `?status=${encodeURIComponent(status)}` : "";
    return this.request<{ success: boolean; data: any[]; count: number }>(`/policies/catalog${query}`);
  }

  getCatalogStats() {
    return this.request<{ success: boolean; data: CatalogStats }>("/policies/catalog/stats");
  }

  refreshPolicyCache() {
    return this.request<{ success: boolean; data: { message: string; activeVerifiedPoliciesCount: number } }>("/policies/cache/refresh", {
      method: "POST",
    });
  }

  getPolicyDrafts() {
    return this.request<{ success: boolean; data: any[]; count: number }>("/policies/drafts");
  }

  getPolicySources() {
    return this.request<{ success: boolean; data: PolicySource[]; count: number }>("/policies/sources");
  }

  registerPolicySource(source: Partial<PolicySource>) {
    return this.request<{ success: boolean; data: PolicySource }>("/policies/sources", {
      method: "POST",
      body: JSON.stringify(source),
    });
  }

  registerCuratedOfficialSource(request: {
    sourceUrl: string;
    sourceName?: string;
    provider?: string;
    canonicalProductName?: string;
    expectedUin?: string;
    expectedRevision?: string;
    curationNotes?: string;
  }) {
    return this.request<{
      success: boolean;
      data: {
        success: boolean;
        message: string;
        registeredSource: PolicySource;
        stagedDocument: DiscoveredDocument;
        stagedPolicyId: string;
        irdaUin: string;
        uinRevision: string;
        sha256Checksum: string;
        fileSizeBytes: number;
        verificationStatus: string;
      };
    }>("/policies/sources/register-curated", {
      method: "POST",
      body: JSON.stringify(request),
    });
  }

  crawlPolicySource(id: string, force = false) {
    return this.request<{ success: boolean; data: { sourceId: string; outcome: string } }>(`/policies/sources/${id}/crawl?force=${force}`, {
      method: "POST",
    });
  }

  crawlAllPolicySources(force = false, discoverNewSources = true) {
    return this.request<{ success: boolean; data: CrawlSummary }>(`/policies/sources/crawl-all?force=${force}&discoverNewSources=${discoverNewSources}`, {
      method: "POST",
    });
  }

  discoverPolicySources(candidateUrls?: string[]) {
    return this.request<{ success: boolean; data: DiscoveryReport }>("/policies/sources/discover", {
      method: "POST",
      body: candidateUrls ? JSON.stringify(candidateUrls) : undefined,
    });
  }

  getDiscoveredDocuments() {
    return this.request<{ success: boolean; data: DiscoveredDocument[]; count: number }>("/policies/documents");
  }

  getQualityBenchmarkReport() {
    return this.request<{ success: boolean; data: RecommendationQualityReport }>("/policies/quality-report");
  }

  // Compare
  comparePolicies(policyIds?: string[]) {
    return this.request<{ success: boolean; data: ComparisonResult }>("/compare", {
      method: "POST",
      body: JSON.stringify({ policyIds }),
    });
  }

  // Settings
  getSettings() {
    return this.request<{ success: boolean; data: UserSettings }>("/settings");
  }

  saveSettings(settings: UserSettings) {
    return this.request<{ success: boolean; data: UserSettings }>("/settings", {
      method: "PUT",
      body: JSON.stringify(settings),
    });
  }

  // Auth
  login(email: string, password: string) {
    return this.request<{ success: boolean; token: string; refreshToken?: string; user: Record<string, any> }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
  }

  register(name: string, email: string, password: string) {
    return this.request<{ success: boolean; token: string; refreshToken?: string; user: Record<string, any> }>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ name, email, password }),
    });
  }

  googleSignIn(credential: string) {
    return this.request<{ success: boolean; token: string; refreshToken?: string; user: Record<string, any> }>("/auth/google", {
      method: "POST",
      body: JSON.stringify({ credential }),
    });
  }

  fetchCurrentUser() {
    return this.request<{ success: boolean; data: Record<string, any> }>("/auth/me");
  }

  changePassword(currentPassword: string, newPassword: string) {
    return this.request<{ success: boolean; error?: string }>("/auth/password", {
      method: "PUT",
      body: JSON.stringify({ currentPassword, newPassword }),
    });
  }

  forgotPassword(email: string) {
    return this.request<{ success: boolean; message?: string; devOtp?: string; error?: string }>("/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email }),
    });
  }

  verifyOtp(email: string, otp: string) {
    return this.request<{ success: boolean; message?: string; error?: string }>("/auth/verify-otp", {
      method: "POST",
      body: JSON.stringify({ email, otp }),
    });
  }

  resetPassword(email: string, otp: string, newPassword: string) {
    return this.request<{ success: boolean; message?: string; error?: string }>("/auth/reset-password", {
      method: "POST",
      body: JSON.stringify({ email, otp, newPassword }),
    });
  }

  // Analytics
  getAnalytics() {
    return this.request<{ success: boolean; data: AnalyticsData }>("/analytics");
  }

  // Claim Simulator
  simulateClaim(data: ClaimSimulationRequest) {
    return this.request<{
      success: boolean;
      message?: string;
      data: ClaimSimulationResponse;
    }>("/claim-simulator/simulate", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  getClaimScenario(id: string) {
    return this.request<{
      success: boolean;
      message?: string;
      data: SavedScenarioDetail;
    }>(`/claim-simulator/scenarios/${encodeURIComponent(id)}`);
  }

  getSampleClaimScenarios() {
    return this.request<{
      success: boolean;
      message?: string;
      data: SampleScenario[];
    }>("/claim-simulator/sample-scenarios");
  }

  compareClaims(data: ClaimComparisonRequest) {
    return this.request<{
      success: boolean;
      message?: string;
      data: ClaimComparisonResponse;
    }>("/claim-simulator/compare", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  getInsurerMetrics(insurerCode: string, reportingPeriod?: string) {
    const params = reportingPeriod ? `?reportingPeriod=${encodeURIComponent(reportingPeriod)}` : "";
    return this.request<{
      success: boolean;
      message?: string;
      data: InsurerPerformanceSummary;
    }>(`/external-evidence/metrics/${encodeURIComponent(insurerCode)}${params}`);
  }

  getHospitalNetwork(params: { insurer?: string; city?: string; pinCode?: string }) {
    const q = new URLSearchParams();
    if (params.insurer) q.append("insurer", params.insurer);
    if (params.city) q.append("city", params.city);
    if (params.pinCode) q.append("pinCode", params.pinCode);
    const qs = q.toString() ? `?${q.toString()}` : "";
    return this.request<{
      success: boolean;
      message?: string;
      count?: number;
      data: HospitalNetworkSummary;
    }>(`/external-evidence/hospitals${qs}`);
  }

  getQuote(params: {
    policyCode: string;
    age: number;
    sumInsured: number;
    pinCode?: string;
    familyConstruct?: string;
  }) {
    const q = new URLSearchParams();
    q.append("policyCode", params.policyCode);
    q.append("age", String(params.age));
    q.append("sumInsured", String(params.sumInsured));
    if (params.pinCode) q.append("pinCode", params.pinCode);
    if (params.familyConstruct) q.append("familyConstruct", params.familyConstruct);
    return this.request<{
      success: boolean;
      message?: string;
      data: QuoteDetail;
    }>(`/external-evidence/quotes?${q.toString()}`);
  }

  getPolicyIntelligenceProfile(
    policyCode: string,
    params?: {
      age?: number;
      sumInsured?: number;
      city?: string;
      pinCode?: string;
      familyConstruct?: string;
      suitabilityScore?: number;
    }
  ) {
    const q = new URLSearchParams();
    if (params?.age) q.append("age", String(params.age));
    if (params?.sumInsured) q.append("sumInsured", String(params.sumInsured));
    if (params?.city) q.append("city", params.city);
    if (params?.pinCode) q.append("pinCode", params.pinCode);
    if (params?.familyConstruct) q.append("familyConstruct", params.familyConstruct);
    if (params?.suitabilityScore !== undefined) q.append("suitabilityScore", String(params.suitabilityScore));
    const qs = q.toString() ? `?${q.toString()}` : "";
    return this.request<{
      success: boolean;
      message?: string;
      data: PolicyIntelligenceProfile;
    }>(`/policies/${encodeURIComponent(policyCode)}/intelligence-profile${qs}`);
  }

  ingestEvidence(data: IngestEvidenceRequest) {
    return this.request<{
      success: boolean;
      message?: string;
      data: EvidenceRecord;
    }>("/external-evidence/ingest", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  verifyEvidence(recordId: string, data: VerifyEvidenceRequest) {
    return this.request<{
      success: boolean;
      message?: string;
      data: EvidenceRecord;
    }>(`/external-evidence/verify/${encodeURIComponent(recordId)}`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  ingestHospital(data: IngestHospitalRequest) {
    return this.request<{
      success: boolean;
      message?: string;
      data: HospitalNode;
    }>("/external-evidence/hospitals/ingest", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }
}

// Types
export interface Policy {
  id: string;
  name: string;
  provider: string;
  type: string;
  premium: string;
  coverage: string;
  expiryDate: string;
  healthScore: number;
  riskLevel: "high" | "medium" | "low";
  status: "active" | "expiring" | "expired";
  createdAt: string;
  analysis?: PolicyAnalysis;
}

export interface PolicyAnalysis {
  policyName: string;
  provider: string;
  type: string;
  premium?: string;
  coverage?: string;
  expiryDate?: string;
  clauses: string[];
  exclusions: string[];
  waitingPeriods: Array<{ condition: string; duration: string; note: string }>;
  riders: Array<{ name: string; details: string; additionalPremium?: string }>;
  hiddenConditions: string[];
  scores: {
    transparency: number;
    risk: number;
    fairness: number;
    claimReliability: number;
    overall: number;
  };
  simplifiedSummary: string;
  roomRentLimit?: string;
  roomRentCapped?: boolean;
  copay?: string;
  copayPercentage?: number;
  deductible?: string;
  deductibleAmount?: number;
  diseaseSubLimits?: string;
  preHospitalizationDays?: number;
  postHospitalizationDays?: number;
  restorationBenefit?: string;
  noClaimBonus?: string;
}

export interface ScenarioResult {
  covered: boolean | "partial" | "unknown";
  coverageStatus: string;
  summary: string;
  estimatedCost: { total: number; covered: number; outOfPocket: number } | null;
  conditions: string[];
  recommendation: string;
  relevantClauses: string[];
}

export interface ClaimInput {
  type: string;
  disease: string;
  hospitalBill: number;
  policyId?: string;
  memberAge?: number;
  isPreExisting?: boolean;
  hospitalType?: "network" | "non-network";
  roomType?: string;
}

export interface ClaimPrediction {
  approvalProbability: number;
  hospitalBill: number;
  coveredAmount: number;
  outOfPocket: number;
  breakdown: {
    deductible: number;
    coPay: number;
    roomRentExcess: number;
    subLimitDeduction: number;
    networkDiscount: number;
  };
  rejectionReasons: string[];
  requiredDocuments: Array<{ name: string; critical: boolean }>;
  tips: string[];
  timeline: string;
  similarClaims: { approved: number; rejected: number; avgPayout: number };
}

export interface ComparisonResult {
  policies: ComparisonPolicy[];
  recommendation: {
    bestPolicy: string;
    reason: string;
    savings: string;
  };
}

export interface ComparisonPolicy {
  id: string;
  name: string;
  provider: string;
  premium: number | string;
  coverage: number | string;
  scores: { transparency: number; risk: number; fairness: number; claimReliability: number };
  pros: string[];
  cons: string[];
  exclusions: number;
  hiddenTraps: number;
  roomRentLimit?: string;
  roomRentCapped?: boolean;
  copay?: string;
  copayPercentage?: number;
  deductible?: string;
  deductibleAmount?: number;
  diseaseSubLimits?: string;
  preHospitalizationDays?: number;
  postHospitalizationDays?: number;
  restorationBenefit?: string;
  noClaimBonus?: string;
}

export interface UserSettings {
  profile: {
    name: string;
    email: string;
    age: number;
    occupation: string;
    phone: string;
  };
  healthProfile: {
    familyMembers: string;
    preExistingConditions: string[];
    familyHistory: string[];
    bloodGroup: string;
  };
  preferences: {
    language: string;
    notifications: boolean;
    darkMode: boolean;
    twoFactorAuth: boolean;
    dataSharing: boolean;
  };
}

export interface AnalyticsData {
  kpis: {
    trustScore: { value: number; trend: number };
    claimPreparedness: { value: number; trend: number };
    hiddenRisks: { value: number; trend: number };
    coverageGap: { value: string; trend: number };
  };
  trustScoreTrend: Array<{ month: string; score: number }>;
  policyHealth: {
    transparency: number;
    riskProtection: number;
    claimReadiness: number;
    valueForMoney: number;
    overall: number;
  };
  coverageBreakdown: Array<{
    type: string;
    amount: string;
    percentage: number;
  }>;
  riskDistribution: Array<{ category: string; count: number; severity: string }>;
}

export interface MemberInput {
  relation: string;
  displayName?: string;
  age: number;
  preExistingConditions?: string[];
}

export interface RecommendationRequest {
  primaryAge: number;
  insuranceType: string;
  members?: MemberInput[];
  cityOrPincode: string;
  annualBudget: number;
  budgetType: "STRICT" | "PREFERRED";
  desiredSumInsured?: number | null;
  helpMeDecideSumInsured?: boolean;
  primaryPriority: "AFFORDABLE_PREMIUMS" | "BROADER_COVERAGE" | "LOWER_OUT_OF_POCKET" | "BALANCED";
  preferredHospitals?: string[];
  preExistingConditions?: string[];
  hasExistingInsurance?: boolean;
  existingSumInsured?: number | null;
  portingPolicy?: boolean;
  policyRenewalDate?: string;
  maternityBenefitNeeded?: boolean;
  opdBenefitNeeded?: boolean;
  copayTolerance?: string;
}

export interface GuidanceRequest {
  primaryAge: number;
  insuranceType: string;
  memberCount: number;
  cityOrPincode: string;
  annualBudget?: number;
}

export interface GuidanceResponse {
  recommendedSumInsured: number;
  formattedRecommendedSumInsured: string;
  rationale: string;
  medicalInflationInsight: string;
  zone: string;
  pricingZone?: string;
  estimatedMinBudget: number;
  estimatedMaxBudget: number;
  estimatedAnnualPremiumMin?: number;
  estimatedAnnualPremiumMax?: number;
  formattedEstimatedPremiumRange?: string;
  budgetAdequacyNote: string;
  cityHealthcareInsights?: string[];
  recommendationsByLifeStage?: string[];
  tips: string[];
}

export interface ProfileSummary {
  primaryAge: number;
  insuranceType: string;
  totalInsuredMembers: number;
  cityOrPincode: string;
  pricingZone: string;
  annualBudget: number;
  formattedBudget: string;
  budgetType: string;
  selectedSumInsured: number;
  formattedSumInsured: string;
  primaryPriorityLabel: string;
  declaredConditions: string[];
}

export interface ProfileGuidance {
  recommendedSumInsured: number;
  formattedRecommendedSumInsured: string;
  sumInsuredRationale: string;
  zoneClassification: string;
  medicalInflationInsight: string;
  budgetAdequacyNote: string;
}

export interface DiseaseSublimit {
  diseaseName: string;
  sublimitAmount: number;
  formattedLimit: string;
  note: string;
}

export interface LimitationAssessment {
  roomRentCondition: string;
  roomRentCapped: boolean;
  proportionateDeductionRisk: boolean;
  roomRentWarning: string;
  copayPercentage: number;
  copayNote: string;
  sublimits: DiseaseSublimit[];
  initialWaitingDays: number;
  specificIllnessWaitingMonths: number;
  pedWaitingMonths: number;
  pedStatusForUser: string;
  restorationSummary: string;
  noClaimBonusSummary: string;
}

export interface OutOfPocketRiskAnalysis {
  riskLevel: "LOW" | "MODERATE" | "HIGH";
  summary: string;
  estimatedExposurePercentage: number;
  sampleScenarioBill: number;
  estimatedCoveredAmount: number;
  estimatedOutofPocketCost: number;
  exposureFactors: string[];
}

export interface DataClassification {
  contractualFactsStatus: string;
  pricingClassification: string;
  isBindingQuote: boolean;
  pricingDisclaimer: string;
  billSimulationClassification: string;
  billSimulationAssumptions: string;
  regulatoryMetricsClassification: string;
  regulatoryPeriod: string;
}

export interface ThreeQuestionAnswers {
  whyRecommended: string;
  verifiedFactsSupporting: string[];
  estimatesAndAssumptions: string[];
}

export interface PolicyEvidenceItem {
  clauseName: string;
  sectionReference: string;
  documentName: string;
  verbatimText: string;
  verifiedFact: string;
  tag: string;
  evidenceConfidence?: string;
  sourceDocumentVersion?: string;
}

export interface InsurerMetrics {
  claimSettlementRatio: number;
  incurredClaimsRatio: number;
  cashlessHospitalsCount: number;
  topHospitalNetworks: string[];
  avgGrievanceResolutionDays: number;
  claimPaymentSpeed: string;
  csrMetricName?: string;
  csrReportingPeriod?: string;
  csrSource?: string;
  csrCalculationMethod?: string;
  networkAsOfDate?: string;
  networkSource?: string;
}

export interface RankedPolicyCard {
  rank: number;
  policyId: string;
  policyCode: string;
  policyName: string;
  provider: string;
  irdaUin: string;
  uinRevision?: string;
  verificationStatus?: string;
  regulatoryFilingSource?: string;
  officialDocumentUrl?: string;
  lastAuditedDate?: string;
  planName: string;
  category: string;
  policyWordingDocument: string;
  overallMatchScore: number;
  costScore: number;
  coverageScore: number;
  outOfPocketProtectionScore: number;
  insurerReliabilityScore: number;
  calculatedAnnualPremium: number;
  formattedAnnualPremium: string;
  sumInsured: number;
  formattedSumInsured: string;
  withinBudget: boolean;
  budgetDifference: number;
  budgetComparisonText: string;
  matchBadges: string[];
  matchReasons: string[];
  limitations: LimitationAssessment;
  outOfPocketRisk: OutOfPocketRiskAnalysis;
  keyExclusions: string[];
  supportingEvidence: PolicyEvidenceItem[];
  insurerMetrics: InsurerMetrics;
  dataClassification?: DataClassification;
  threeQuestionAnswers?: ThreeQuestionAnswers;
  policySnapshotVersion?: string;
  scoringEngineVersion?: string;
  evaluationTimestamp?: string;
  reproducibilitySnapshot?: {
    policySnapshotVersion?: string;
    scoringEngineVersion?: string;
    evaluationTimestamp?: string;
    contractTermsSnapshot?: {
      roomRentLimit?: string;
      roomRentCapped?: boolean;
      copayPercentage?: number;
      waitingPeriodPedMonths?: number;
      waitingPeriodInitialDays?: number;
      waitingPeriodSpecificMonths?: number;
      deductibleAmount?: number;
      restorationAvailable?: boolean;
    };
    pricingModelSnapshot?: {
      selectedSumInsured?: number;
      calculatedAnnualPremium?: number;
      pricingZone?: string;
      pricingStatus?: string;
      pricingClassification?: string;
    };
    insurerMetricsSnapshot?: {
      claimSettlementRatio?: number;
      incurredClaimsRatio?: number;
      cashlessHospitalsCount?: number;
      reportingPeriod?: string;
    };
    scoringBreakdown?: {
      costWeight?: number;
      coverageWeight?: number;
      oopWeight?: number;
      reliabilityWeight?: number;
      costScore?: number;
      coverageScore?: number;
      oopScore?: number;
      reliabilityScore?: number;
      overallScore?: number;
      primaryPriority?: string;
    };
    inputRequirementsSnapshot?: {
      primaryAge?: number;
      insuranceType?: string;
      pricingZone?: string;
      annualBudget?: number;
      budgetType?: string;
      declaredConditionsCount?: number;
      totalInsuredMembers?: number;
    };
  };
}

export interface RecommendationResponse {
  recommendationId: string;
  profileId: string;
  generatedAt: string;
  profileSummary: ProfileSummary;
  guidance: ProfileGuidance;
  rankedPolicies: RankedPolicyCard[];
  totalPoliciesEvaluated: number;
  eligiblePoliciesCount: number;
}

export interface RecommendationHistoryItem {
  id: string;
  userId: string;
  profileId: string;
  userRequirements?: any;
  guidance: ProfileGuidance;
  rankedPolicies: RankedPolicyCard[];
  createdAt: string;
}

export interface StructuredPolicy {
  id: string;
  policyCode: string;
  policyName: string;
  provider: string;
  irdaUin: string;
  planName: string;
  category: string;
  summary: string;
  policyWordingDocument: string;
  insurerMetrics: InsurerMetrics;
  officialEvidence: PolicyEvidenceItem[];
  keyExclusions: string[];
}

export interface PolicyDiscoveryRequest {
  documentUrl?: string;
  documentName?: string;
  sourceType?: string;
  policyName?: string;
  provider?: string;
  irdaUin?: string;
  uinRevision?: string;
  rawPolicyText?: string;
  autoPublishIfHighConfidence?: boolean;
}

export interface PolicyVerificationRequest {
  decision: "APPROVE" | "REJECT" | "FLAG_OUTDATED" | "FLAG_INCOMPLETE";
  reviewerName?: string;
  reviewerNotes?: string;
  overrideFields?: Record<string, any>;
}

export interface PolicyIngestionResult {
  policyId: string;
  policyCode: string;
  policyName: string;
  provider: string;
  irdaUin: string;
  uinRevision?: string;
  verificationStatus: string;
  contractConfidenceScore: number;
  pricingConfidenceScore: number;
  pricingStatus: string;
  confidenceScore: number;
  readyForPublication: boolean;
  requiresHumanVerification: boolean;
  contractUncertainFields: string[];
  pricingUncertainFields: string[];
  uncertainFields: string[];
  criticalAuditFindings: string[];
  extractedSummary: Record<string, any>;
  message: string;
}

export interface CatalogStats {
  totalPolicies: number;
  verifiedPublishedPolicies: number;
  pendingReviewPolicies: number;
  draftPolicies: number;
  flaggedOrOutdatedPolicies: number;
}

export interface PolicySource {
  id: string;
  sourceName: string;
  provider: string;
  canonicalProductName?: string;
  irdaUin?: string;
  expectedRevision?: string;
  documentType?: string;
  sourceAuthority?: string;
  sourceUrl: string;
  active: boolean;
  lastEtag?: string;
  lastModifiedHeader?: string;
  lastSha256Checksum?: string;
  lastFileSizeBytes?: number;
  lastCheckedAt?: string;
  lastFetchedAt?: string;
  lastSuccessfulExtractionAt?: string;
  lastHttpStatusCode?: number;
  status: string;
  lastErrorMessage?: string;
  consecutiveFailures: number;
  pollFrequencyHours: number;
  linkedDraftPolicyId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DiscoveredDocument {
  id: string;
  sourceId?: string;
  sourceUrl: string;
  documentName: string;
  sha256Checksum: string;
  fileSizeBytes: number;
  etag?: string;
  contentType?: string;
  fetchedAt: string;
  extractionStatus: string;
  extractedPolicyId?: string;
  irdaUin?: string;
  uinRevision?: string;
  confidenceScore?: number;
  errorMessage?: string;
}

export interface DomainDiagnostics {
  rootUrl: string;
  domain: string;
  resolvedIp?: string;
  httpStatusCode: number;
  responseMessage?: string;
  contentType?: string;
  redirectLocation?: string;
  redirectChain?: string[];
  errorCategory: "SUCCESS_200" | "REDIRECT_FOLLOWED" | "BOT_PROTECTION_403" | "OBSOLETE_ENDPOINT_404" | "MISDIRECTED_OR_BAD_421_400" | "TIMEOUT_ERROR" | "SSRF_BLOCKED" | "CONNECTION_ERROR" | string;
  sanitizedExcerpt?: string;
  linksExtracted: number;
}

export interface DiscoveryReport {
  candidateSourcesFound: number;
  candidateSourcesRegistered: number;
  candidateSourcesRejected: number;
  rejectionReasons: Record<string, number>;
  registeredSources: PolicySource[];
  discoveryMessages: string[];
  domainDiagnostics?: DomainDiagnostics[];
}

export interface CrawlSummary {
  discoveryMode?: "LIVE_DISCOVERY" | "CATALOG_FALLBACK" | "LIVE_DISCOVERY_EMPTY" | "HYBRID" | "DISABLED" | string;
  liveCandidatesDiscovered?: number;
  fallbackCatalogCandidates?: number;
  lastLiveScanSuccessful?: boolean;
  lastLiveScanTimestamp?: string;
  liveScannedUrls?: string[];
  totalSources: number;
  checkedSources: number;
  sourcesScanned?: number;
  candidateSourcesDiscovered?: number;
  candidateSourcesRegistered?: number;
  candidateSourcesRejected?: number;
  candidateRejectionReasons?: Record<string, number>;
  documentsDownloaded?: number;
  newDocumentsExtracted: number;
  unchangedDocumentsSkipped: number;
  newRevisionsDetected?: number;
  draftsCreated?: number;
  failedSources: number;
  failureDetails?: string[];
  messages: string[];
  domainDiagnostics?: DomainDiagnostics[];
}

export interface FactDiff {
  fieldName: string;
  previousValue: string;
  newValue: string;
  isMaterial: boolean;
  severity: "CRITICAL" | "MODERATE" | "INFORMATIONAL";
  impactExplanation: string;
  previousEvidenceCitation?: string;
  newEvidenceCitation?: string;
}

export interface PolicyChangeImpactReport {
  policyCode: string;
  productName: string;
  provider: string;
  previousRevision?: string;
  newRevision?: string;
  analyzedAt: string;
  materialChangesDetected: boolean;
  requiresSeniorReview: boolean;
  contractualDiffs: FactDiff[];
  eligibilityImpacts: string[];
  rankingScoreImpacts: string[];
  claimSimulationImpacts: string[];
}

export interface ScenarioEvaluationResult {
  scenarioId: string;
  scenarioName: string;
  category: string;
  passed: boolean;
  eligiblePoliciesFound: number;
  topRankedPolicy: string;
  expectedTopPolicy: string;
  topMatchAligned: boolean;
  mandatoryConstraintsHonored: boolean;
  claimMathAccurate: boolean;
  citationIntegrityScore: number;
  executionTimeMs: number;
  documentedGroundTruth?: string;
  independentActuarialBasis?: string;
  auditNotes: string;
}

export interface RecommendationQualityReport {
  evaluatedAt: string;
  engineVersion: string;
  catalogPolicyCount: number;
  totalBenchmarkScenarios: number;
  passedScenarios: number;
  overallQualityScore: number;
  mandatoryConstraintViolationRate: number;
  rankingAgreementRate: number;
  claimMathAccuracyRate: number;
  citationCoverageRate: number;
  regressionPassed: boolean;
  scenarioResults: ScenarioEvaluationResult[];
}

// Claim Simulator Types & DTOs
export type BillItemCategory =
  | "ROOM_RENT"
  | "ICU_CHARGES"
  | "NURSING_CHARGES"
  | "SURGEON_FEES"
  | "OT_CHARGES"
  | "MEDICINES"
  | "DIAGNOSTICS"
  | "CONSUMABLES"
  | "IMPLANTS"
  | "AMBULANCE"
  | "OTHER";

export interface BillLineItem {
  lineId: string;
  category: BillItemCategory;
  description: string;
  unitPrice: number;
  quantity: number;
  totalAmount: number;
}

export interface ClaimSimulationRequest {
  policyId: string;
  uinRevision?: string;
  patientAge: number;
  diagnosis: string;
  accidentalInjury?: boolean;
  preExistingCondition?: boolean;
  policyContinuityMonths?: number;
  admissionDate?: string;
  dischargeDate?: string;
  hospitalType?: string;
  hospitalRoomCategoryOpted?: string;
  selectedSumInsured: number;
  priorClaimsInCurrentYear?: number;
  subsequentClaimForSameIllness?: boolean;
  billLines: BillLineItem[];
}

export interface DeductionItem {
  deductionType: string;
  expenseCategory: string;
  amountDeducted: number;
  explanation: string;
  calculationFormula?: string;
  sectionReference?: string;
  verbatimClauseText?: string;
  clauseConfidence?: string;
}

export type CalculationStatus =
  | "FULL_DETERMINISTIC_VERIFIED"
  | "PARTIAL_ESTIMATE"
  | "MANUAL_REVIEW_REQUIRED"
  | "POLICY_UNAPPROVED_REJECTED";

export interface ClaimSimulationResponse {
  simulationId: string;
  scenarioId?: string;
  policyId: string;
  policyCode: string;
  policyName: string;
  provider: string;
  irdaUin: string;
  uinRevision: string;
  sourceDocumentHash?: string;
  ruleSetVersion?: string;
  calculationEngineVersion: string;
  calculationStatus: CalculationStatus;
  statusReason?: string;
  totalSubmittedBill: number;
  totalEligibleExpenses: number;
  totalNonCoveredExpenses: number;
  totalDeductions: number;
  estimatedInsurerPayment: number;
  estimatedPatientLiability: number;
  reconciliationIdentity: string;
  reconciled: boolean;
  restorationTriggered: boolean;
  restorationAmountGranted: number;
  restorationExplanation?: string;
  deductionBreakdown: DeductionItem[];
  appliedClauses: string[];
  unresolvedRules: string[];
  legalDisclaimer: string;
  evaluatedAt: string;
}

export interface SavedScenarioDetail {
  scenarioId: string;
  userId: string;
  policyId: string;
  policyCode: string;
  irdaUin: string;
  uinRevision: string;
  patientAge: number;
  diagnosis: string;
  accidentalInjury: boolean;
  preExistingCondition: boolean;
  policyContinuityMonths: number;
  admissionDate?: string;
  dischargeDate?: string;
  hospitalType: string;
  hospitalRoomCategoryOpted: string;
  selectedSumInsured: number;
  totalSubmittedBill: number;
  syntheticExample: boolean;
  createdAt: string;
  billLines: BillLineItem[];
  result: ClaimSimulationResponse;
}

export interface SampleScenario {
  sampleId: string;
  title: string;
  scenarioType: string;
  description: string;
  clinicalNarrative: string;
  targetPolicyCode: string;
  targetPolicyName: string;
  request: ClaimSimulationRequest;
  syntheticExample: boolean;
  disclaimer: string;
  educationalTakeaway: string;
}

export interface ClaimComparisonRequest {
  policyIds: string[];
  scenario: ClaimSimulationRequest;
}

export interface PolicyClaimComparisonItem {
  policyId: string;
  policyCode: string;
  policyName: string;
  provider: string;
  irdaUin: string;
  uinRevision: string;
  sourceDocumentHash?: string;
  syntheticExample: boolean;
  calculationStatus: CalculationStatus;
  reconciled: boolean;

  totalSubmittedBill: number;
  totalEligibleExpenses: number;
  totalNonCoveredExpenses: number;
  totalDeductions: number;
  estimatedInsurerPayment: number;
  estimatedPatientLiability: number;

  roomRentDeductions: number;
  sublimitDeductions: number;
  costSharingDeductions: number;
  waitingPeriodDeductions: number;

  restorationTriggered: boolean;
  restorationAmountGranted: number;
  restorationExplanation?: string;

  unresolvedRules: string[];
  appliedClauseCount: number;
  fullSimulation: ClaimSimulationResponse;

  // Evidence Layer & Provenance Fields
  authenticAcquiredDocument?: boolean;
  verificationStatus?: string;
  reviewerApprovalStatus?: string;
  verifiedBy?: string;
  roomRentLimit?: string;
  roomRentCapped?: boolean;
  proportionateDeductionApplies?: boolean;
  copayPercentage?: number;
  deductibleAmount?: number;
  waitingPeriodInitialDays?: number;
  waitingPeriodPedMonths?: number;
  waitingPeriodSpecificMonths?: number;
  keyExclusions?: string[];
  uncertaintyNotice?: string;
  documentEvidence?: any[];
}

export interface ClauseDelta {
  feature: string;
  policyAValue: string;
  policyBValue: string;
  financialImpact: string;
  clauseCitationA: string;
  clauseCitationB: string;
}

export interface ClaimComparisonResponse {
  scenarioDiagnosis: string;
  totalSubmittedBill: number;
  comparisons: PolicyClaimComparisonItem[];
  highestPayoutPolicyName: string;
  lowestOutOfPocketPolicyName: string;
  payoutVariance?: number;
  patientLiabilityVariance?: number;
  varianceExplanation?: string;
  clauseDeltas?: ClauseDelta[];
  disclaimer: string;
  evaluatedAt: string;
}

export interface InsurerPerformanceSummary {
  insurerCode: string;
  insurerName?: string;
  reportingPeriod: string;
  incurredClaimRatio?: string;
  incurredClaimRatioValue?: number;
  settlementRatioLessThan3Months?: string;
  settlementRatioLessThan3MonthsValue?: number;
  annualSettlementRatio?: string;
  annualSettlementRatioValue?: number;
  repudiationRatio?: string;
  repudiationRatioValue?: number;
  complaintsPer10kClaims?: string;
  complaintsPer10kClaimsValue?: number;
  solvencyRatio?: string;
  solvencyRatioValue?: number;
  claimTurnaroundDays?: string;
  claimTurnaroundDaysValue?: number;
  overallPerformanceRating: "EXCELLENT" | "VERY_GOOD" | "STANDARD" | "REQUIRES_ATTENTION" | "REQUIRES_VERIFICATION";
  statutorySourceTitle?: string;
  statutoryDocumentHash?: string;
  sourceUrl?: string;
  verificationStatus: string;
  verifiedStatutory: boolean;
}

export interface HospitalNode {
  hospitalId: string;
  hospitalName: string;
  registryRohiniCode: string;
  address?: string;
  city: string;
  pinCode: string;
  latitude?: number;
  longitude?: number;
  category?: string;
  cashlessActive: boolean;
  tier: string;
  agreementValidUntil?: string;
  verificationStatus: string;
}

export interface HospitalNetworkSummary {
  insurerCode: string;
  city?: string;
  pinCode?: string;
  totalNetworkHospitalsInCity: number;
  preferredTier1Count: number;
  secondaryCount: number;
  cashlessAccessibilityScore: number;
  verificationBadge: string;
  networkDisclaimer?: string;
  hospitals: HospitalNode[];
}

export interface QuoteDetail {
  quoteId: string;
  policyCode: string;
  uinRevision?: string;
  insurerCode: string;
  sumInsured: number;
  age: number;
  pinCode?: string;
  zone?: string;
  familyConstruct: string;
  quotedAnnualPremium: number;
  quotedBasePremium?: number;
  quotedGst?: number;
  quotingChannel: string;
  quoteTimestamp: string;
  quoteReferenceNumber?: string;
  sourceDocumentHash?: string;
  isAuthentic: boolean;
  verificationStatus: string;
  formattedAnnualPremium: string;
  isStale: boolean;
}

export interface EvidenceRecord {
  id: string;
  evidenceType: string;
  insurerCode: string;
  insurerName?: string;
  metricCategory: string;
  metricKey: string;
  metricValue: string;
  numericValue: number;
  metricUnit?: string;
  reportingPeriod: string;
  publicationDate?: string;
  sourceDocumentTitle: string;
  sourceUrl: string;
  sourceDocumentHash: string;
  citationPageNumber?: number;
  citationTableReference?: string;
  verificationStatus: string;
  verifiedBy?: string;
  verifiedAt?: string;
  verificationNotes?: string;
  ingestionTimestamp: string;
}

export interface IngestEvidenceRequest {
  evidenceType: string;
  insurerCode: string;
  insurerName?: string;
  metricCategory: string;
  metricKey: string;
  metricValue: string;
  numericValue: number;
  metricUnit?: string;
  reportingPeriod: string;
  publicationDate?: string;
  sourceDocumentTitle: string;
  sourceUrl: string;
  sourceDocumentHash: string;
  citationPageNumber?: number;
  citationTableReference?: string;
  verificationNotes?: string;
}

export interface VerifyEvidenceRequest {
  verificationStatus: string;
  verificationNotes?: string;
}

export interface IngestHospitalRequest {
  hospitalId: string;
  hospitalName: string;
  registryRohiniCode: string;
  address?: string;
  city: string;
  state?: string;
  pinCode: string;
  latitude?: number;
  longitude?: number;
  category?: string;
  cashlessAgreements: Array<{
    insurerCode: string;
    insurerName?: string;
    cashlessActive: boolean;
    tier?: string;
    agreementValidUntil?: string;
    sourceDocumentHash?: string;
    verificationStatus?: string;
  }>;
}

export interface PolicyContractOverview {
  policyId: string;
  policyCode: string;
  policyName: string;
  provider: string;
  irdaUin: string;
  uinRevision: string;
  planName?: string;
  category?: string;
  description?: string;
  roomRentCapped: boolean;
  roomRentCapPerDay?: number;
  proportionateDeductionApplies: boolean;
  roomRentLimitDescription: string;
  copayPercentage: number;
  copayDescription: string;
  deductibleAmount: number;
  waitingPeriodInitialDays: number;
  waitingPeriodPedMonths: number;
  waitingPeriodSpecificIllnessMonths: number;
  dayCareTreatmentsCovered: boolean;
  restorationBenefit: boolean;
  cumulativeBonusDescription: string;
  officialDocumentUrl?: string;
  policyWordingDocument?: string;
  regulatoryFilingSource?: string;
  syntheticFixture: boolean;
  verificationStatus: string;
}

export interface BenchmarkClaimSimulationSummary {
  diagnosis: string;
  totalSubmittedBill: number;
  estimatedInsurerPayment: number;
  estimatedPatientLiability: number;
  isReconciled: boolean;
  roomRentExcessDeduction: number;
  proportionateDeduction: number;
  nonMedicalConsumablesDeduction: number;
  copayDeduction: number;
  contractualExplanation: string;
  clauseCitations: string[];
}

export interface EvidenceQualityScorecard {
  contractualGroundTruthStatus: string;
  externalStatutoryEvidenceStatus: string;
  quoteVerificationStatus: string;
  hospitalRegistryStatus: string;
  overallQualityScore: number;
  verifiedDisclosures: string[];
  pendingOrMissingDisclosures: string[];
  statutoryDocumentHash?: string;
  lastAuditedDate?: string;
  disclaimer?: string;
}

export interface PolicySuitability {
  suitabilityScore: number;
  suitabilityRating: string;
  suitabilitySummary: string;
  keyFitFactors: string[];
  potentialGaps: string[];
  independentOfExternalEvidence: boolean;
}

export interface EvidenceCompleteness {
  completenessScore: number;
  completenessStatus: string;
  contractualTermsVerified: boolean;
  statutoryPerformanceVerified: boolean;
  authenticQuoteVerified: boolean;
  hospitalNetworkVerified: boolean;
  verifiedCount: number;
  totalRequiredCount: number;
  auditSummary: string;
}

export interface PolicyIntelligenceProfile {
  policySuitability: PolicySuitability;
  evidenceCompleteness: EvidenceCompleteness;
  contractOverview: PolicyContractOverview;
  insurerPerformance: InsurerPerformanceSummary;
  hospitalAccessibility: HospitalNetworkSummary;
  premiumQuotation: QuoteDetail;
  benchmarkClaimSimulation: BenchmarkClaimSimulationSummary;
  evidenceQualityScorecard: EvidenceQualityScorecard;
}

export const api = new ApiClient();
export default api;
