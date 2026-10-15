const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:5000/api";

class ApiClient {
  private base: string;

  constructor(base: string) {
    this.base = base;
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

    const url = `${this.base}${path}`;
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
      const res = await fetch(`${this.base}/auth/refresh`, {
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

    const url = `${this.base}/analyze`;
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

    const url = `${this.base}/analyze/async`;
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
    return `${this.base}/analyze/stream/${taskId}`;
  }

  // Scenario
  simulateScenario(question: string, policyId?: string, language?: string) {
    return this.request<{ success: boolean; data: ScenarioResult }>("/scenario", {
      method: "POST",
      body: JSON.stringify({ question, policyId, language }),
    });
  }

  // Claims
  predictClaim(data: ClaimInput) {
    return this.request<{ success: boolean; data: ClaimPrediction }>("/claims/predict", {
      method: "POST",
      body: JSON.stringify(data),
    });
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

export const api = new ApiClient(API_BASE);
export default api;
