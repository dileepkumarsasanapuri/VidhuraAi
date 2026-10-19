"use client";

import React, { useState, useEffect } from "react";
import {
  Calculator,
  History,
  FileText,
  ShieldCheck,
  AlertTriangle,
  RotateCcw,
  Sparkles,
  AlertCircle,
  HelpCircle,
  CheckCircle2,
  ChevronRight,
  RefreshCw,
  GitCompare,
} from "lucide-react";
import {
  ClaimSimulationRequest,
  ClaimSimulationResponse,
  SavedScenarioDetail,
  api,
} from "@/lib/api";
import ClaimScenarioForm from "@/components/claim-simulator/ClaimScenarioForm";
import ResultsWaterfall from "@/components/claim-simulator/ResultsWaterfall";
import SavedScenariosModal from "@/components/claim-simulator/SavedScenariosModal";
import ProvenanceBadge from "@/components/claim-simulator/ProvenanceBadge";
import CrossPolicyComparisonView from "@/components/claim-simulator/CrossPolicyComparisonView";

const DEFAULT_SCENARIO: ClaimSimulationRequest = {
  policyId: "UIIHLIP22070V042122",
  uinRevision: "V042122",
  patientAge: 42,
  diagnosis: "Acute Appendicitis (Laparoscopic Appendectomy)",
  hospitalType: "NETWORK_CASHLESS",
  hospitalRoomCategoryOpted: "SINGLE_PRIVATE_AC",
  selectedSumInsured: 500000,
  policyContinuityMonths: 24,
  priorClaimsInCurrentYear: 0,
  billLines: [
    {
      lineId: "L1",
      category: "ROOM_RENT",
      description: "Single Private Room Rent (3 days @ ₹6,500)",
      unitPrice: 6500,
      quantity: 3,
      totalAmount: 19500,
    },
    {
      lineId: "L2",
      category: "SURGEON_FEES",
      description: "Surgeon & Anesthesia Professional Fee",
      unitPrice: 45000,
      quantity: 1,
      totalAmount: 45000,
    },
    {
      lineId: "L3",
      category: "OT_CHARGES",
      description: "Operation Theatre & Equipment",
      unitPrice: 28000,
      quantity: 1,
      totalAmount: 28000,
    },
    {
      lineId: "L4",
      category: "MEDICINES",
      description: "Hospital Pharmacy & IV Fluids",
      unitPrice: 18500,
      quantity: 1,
      totalAmount: 18500,
    },
    {
      lineId: "L5",
      category: "CONSUMABLES",
      description: "PPE, Gloves, Trocar & Disposables",
      unitPrice: 12000,
      quantity: 1,
      totalAmount: 12000,
    },
  ],
};

export default function ClaimSimulatorPage() {
  const [activeTab, setActiveTab] = useState<"SIMULATOR" | "COMPARISON">("SIMULATOR");
  const [simulationResult, setSimulationResult] = useState<ClaimSimulationResponse | null>(null);
  const [prefillRequest, setPrefillRequest] = useState<ClaimSimulationRequest | null>(DEFAULT_SCENARIO);
  const [activeScenarioId, setActiveScenarioId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSavedModalOpen, setIsSavedModalOpen] = useState<boolean>(false);
  const [recentScenarioIds, setRecentScenarioIds] = useState<string[]>([]);

  // Load recent scenario IDs from localStorage on mount
  useEffect(() => {
    try {
      const stored = localStorage.getItem("vidhura_recent_scenarios");
      if (stored) {
        setRecentScenarioIds(JSON.parse(stored));
      }
    } catch {
      // Ignore storage errors
    }
  }, []);

  const saveRecentScenarioId = (id: string) => {
    if (!id) return;
    try {
      const updated = [id, ...recentScenarioIds.filter((x) => x !== id)].slice(0, 5);
      setRecentScenarioIds(updated);
      localStorage.setItem("vidhura_recent_scenarios", JSON.stringify(updated));
    } catch {
      // Ignore storage errors
    }
  };

  const handleSimulate = async (request: ClaimSimulationRequest) => {
    setIsLoading(true);
    setErrorMessage(null);
    setPrefillRequest(request);

    try {
      const response = await api.simulateClaim(request);
      if (response.success && response.data) {
        setSimulationResult(response.data);
        if (response.data.simulationId) {
          setActiveScenarioId(response.data.simulationId);
          saveRecentScenarioId(response.data.simulationId);
        }
        window.scrollTo({ top: 0, behavior: "smooth" });
      } else {
        setErrorMessage(
          response.message || "Failed to complete deterministic claim calculation."
        );
      }
    } catch (err: any) {
      const msg = err.message || "An unexpected error occurred during claim calculation.";
      setErrorMessage(msg);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } finally {
      setIsLoading(false);
    }
  };

  const handleSelectSavedScenario = (saved: SavedScenarioDetail) => {
    if (saved.result) {
      setSimulationResult(saved.result);
      setActiveScenarioId(saved.scenarioId);
      saveRecentScenarioId(saved.scenarioId);

      // Reconstitute request so user can "Modify Bill"
      const reconstitutedReq: ClaimSimulationRequest = {
        policyId: saved.policyId || saved.policyCode,
        uinRevision: saved.uinRevision,
        patientAge: saved.patientAge,
        diagnosis: saved.diagnosis,
        accidentalInjury: saved.accidentalInjury,
        preExistingCondition: saved.preExistingCondition,
        policyContinuityMonths: saved.policyContinuityMonths,
        admissionDate: saved.admissionDate,
        dischargeDate: saved.dischargeDate,
        hospitalType: saved.hospitalType,
        hospitalRoomCategoryOpted: saved.hospitalRoomCategoryOpted,
        selectedSumInsured: saved.selectedSumInsured,
        billLines: saved.billLines || [],
      };
      setPrefillRequest(reconstitutedReq);
      setErrorMessage(null);
      setActiveTab("SIMULATOR");
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  };

  const handleResetForm = () => {
    setSimulationResult(null);
    setActiveScenarioId(null);
    setErrorMessage(null);
  };

  const handleInspectFullSimulationFromComparison = (sim: ClaimSimulationResponse) => {
    setSimulationResult(sim);
    setActiveTab("SIMULATOR");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <div className="space-y-8 max-w-7xl mx-auto pb-16">
      {/* Top Header & Page Navigation */}
      <div className="glass rounded-2xl p-6 md:p-8 border border-white/10 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-96 h-96 bg-primary/10 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-primary/20 text-primary-light border border-primary/30 flex items-center gap-1.5">
                <Calculator className="w-3.5 h-3.5" />
                Deterministic Engine v2.0
              </span>
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 flex items-center gap-1">
                <ShieldCheck className="w-3.5 h-3.5" />
                Zero Hallucination
              </span>
            </div>
            <h1 className="text-2xl md:text-3xl font-extrabold text-white tracking-tight">
              Interactive Claim Simulator & Comparison
            </h1>
            <p className="text-sm text-slate-300 max-w-2xl leading-relaxed">
              Test hospital bills against reviewer-approved statutory health insurance policies.
              Every deduction is deterministically calculated and linked to verbatim policy clauses.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setIsSavedModalOpen(true)}
              className="px-4 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-semibold text-slate-200 flex items-center gap-2 transition-colors shadow-sm"
              title="Open previously saved calculation snapshot"
            >
              <History className="w-4 h-4 text-slate-400" />
              <span>Saved Scenarios</span>
            </button>

            {simulationResult && (
              <button
                onClick={handleResetForm}
                className="px-4 py-2.5 rounded-xl bg-primary hover:bg-primary-dark text-white text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-lg shadow-primary/20"
              >
                <RotateCcw className="w-4 h-4" />
                <span>New Simulation</span>
              </button>
            )}
          </div>
        </div>

        {/* Tab Navigation: Single Policy Simulator vs Cross-Policy Comparison */}
        <div className="flex gap-2 mt-6 pt-5 border-t border-white/10">
          <button
            type="button"
            onClick={() => setActiveTab("SIMULATOR")}
            className={`px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-2 transition-all ${
              activeTab === "SIMULATOR"
                ? "bg-primary text-white shadow-lg shadow-primary/20"
                : "bg-white/5 text-slate-400 hover:text-white hover:bg-white/10"
            }`}
          >
            <Calculator className="w-3.5 h-3.5" />
            <span>Single Policy Simulator</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab("COMPARISON")}
            className={`px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-2 transition-all ${
              activeTab === "COMPARISON"
                ? "bg-primary text-white shadow-lg shadow-primary/20"
                : "bg-white/5 text-slate-400 hover:text-white hover:bg-white/10"
            }`}
          >
            <GitCompare className="w-3.5 h-3.5" />
            <span>Cross-Policy Comparison</span>
          </button>
        </div>
      </div>

      {/* Error Banner */}
      {errorMessage && (
        <div className="p-5 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm flex items-start gap-3 shadow-lg">
          <AlertCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
          <div className="space-y-1">
            <p className="font-semibold text-rose-200">Simulation Error</p>
            <p className="text-xs leading-relaxed">{errorMessage}</p>
          </div>
        </div>
      )}

      {/* Main View: Active Tab Selector */}
      {activeTab === "SIMULATOR" ? (
        simulationResult ? (
          <ResultsWaterfall
            simulation={simulationResult}
            onReset={handleResetForm}
          />
        ) : (
          <ClaimScenarioForm
            onSubmit={handleSimulate}
            isLoading={isLoading}
            prefillRequest={prefillRequest}
          />
        )
      ) : (
        <CrossPolicyComparisonView
          currentScenario={prefillRequest || DEFAULT_SCENARIO}
          onSelectFullSimulation={handleInspectFullSimulationFromComparison}
        />
      )}

      {/* Saved Scenarios Retrieval Modal */}
      <SavedScenariosModal
        isOpen={isSavedModalOpen}
        onClose={() => setIsSavedModalOpen(false)}
        onSelectScenario={handleSelectSavedScenario}
        recentScenarioIds={recentScenarioIds}
      />
    </div>
  );
}
