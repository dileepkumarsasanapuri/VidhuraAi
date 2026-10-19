"use client";

import React, { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  X,
  History,
  Search,
  ArrowRight,
  Clock,
  ShieldCheck,
  AlertTriangle,
  FileText,
  AlertCircle,
  ExternalLink,
} from "lucide-react";
import { SavedScenarioDetail, api } from "@/lib/api";
import ProvenanceBadge from "./ProvenanceBadge";

interface SavedScenariosModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectScenario: (scenario: SavedScenarioDetail) => void;
  recentScenarioIds?: string[];
}

export default function SavedScenariosModal({
  isOpen,
  onClose,
  onSelectScenario,
  recentScenarioIds = [],
}: SavedScenariosModalProps) {
  const [scenarioIdInput, setScenarioIdInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadedScenario, setLoadedScenario] = useState<SavedScenarioDetail | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setScenarioIdInput("");
      setError(null);
      setLoadedScenario(null);
    }
  }, [isOpen]);

  // Handle ESC key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    if (isOpen) {
      window.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  const handleFetchScenario = async (idToFetch?: string) => {
    const targetId = (idToFetch || scenarioIdInput).trim();
    if (!targetId) {
      setError("Please enter a valid Claim Scenario ID.");
      return;
    }

    setIsLoading(true);
    setError(null);
    setLoadedScenario(null);

    try {
      const response = await api.getClaimScenario(targetId);
      if (response.success && response.data) {
        setLoadedScenario(response.data);
      } else {
        setError(response.message || "Failed to load saved scenario snapshot.");
      }
    } catch (err: any) {
      const message = err.message || "Error retrieving scenario snapshot";
      if (message.includes("403") || message.toLowerCase().includes("access denied")) {
        setError("Access Denied: You do not have permission to view this claim scenario. It belongs to another authenticated account.");
      } else if (message.includes("404") || message.toLowerCase().includes("not found")) {
        setError(`No saved claim scenario found with ID '${targetId}'.`);
      } else {
        setError(message);
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleConfirmOpen = () => {
    if (loadedScenario) {
      onSelectScenario(loadedScenario);
      onClose();
    }
  };

  const formatInr = (val?: number) => {
    if (val === undefined || val === null) return "₹0.00";
    return "₹" + val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 bg-black/70 backdrop-blur-sm"
          onClick={onClose}
          aria-hidden="true"
        />

        {/* Modal Window */}
        <motion.div
          initial={{ scale: 0.95, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          exit={{ scale: 0.95, opacity: 0 }}
          transition={{ duration: 0.2 }}
          className="relative w-full max-w-2xl bg-slate-900 border border-white/10 rounded-2xl shadow-2xl overflow-hidden z-10 flex flex-col max-h-[90vh]"
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-title"
        >
          {/* Header */}
          <div className="p-6 border-b border-white/10 flex items-center justify-between bg-slate-950/60">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-primary/20 text-primary-light border border-primary/30">
                <History className="w-5 h-5" />
              </div>
              <div>
                <h2 id="modal-title" className="text-lg font-bold text-white">
                  Reopen Saved Claim Scenario
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Retrieve immutable historical calculation snapshots without recalculating against newer terms.
                </p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Body */}
          <div className="p-6 space-y-6 overflow-y-auto flex-1">
            {/* Search Input Box */}
            <div className="space-y-2">
              <label className="text-xs font-semibold text-slate-300">
                Scenario ID or Simulation Snapshot UUID
              </label>
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    value={scenarioIdInput}
                    onChange={(e) => setScenarioIdInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        handleFetchScenario();
                      }
                    }}
                    placeholder="e.g. SCN-xxx or 67123abc..."
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-950 border border-white/15 text-white text-sm font-mono focus:outline-none focus:border-primary transition-colors"
                  />
                </div>
                <button
                  type="button"
                  onClick={() => handleFetchScenario()}
                  disabled={isLoading || !scenarioIdInput.trim()}
                  className="px-5 py-2.5 rounded-xl bg-primary hover:bg-primary-dark disabled:opacity-50 text-white text-xs font-bold flex items-center gap-2 transition-colors shrink-0 shadow-lg shadow-primary/20"
                >
                  {isLoading ? (
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  ) : (
                    <span>Retrieve</span>
                  )}
                </button>
              </div>
            </div>

            {/* Error Message */}
            {error && (
              <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-start gap-2.5">
                <AlertCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            {/* Recent Scenario IDs from this session */}
            {recentScenarioIds.length > 0 && !loadedScenario && (
              <div className="space-y-2">
                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  Recently Simulated in this Session
                </p>
                <div className="space-y-1.5">
                  {recentScenarioIds.map((id) => (
                    <button
                      type="button"
                      key={id}
                      onClick={() => {
                        setScenarioIdInput(id);
                        handleFetchScenario(id);
                      }}
                      className="w-full p-3 rounded-xl bg-white/5 hover:bg-white/10 border border-white/5 hover:border-white/15 text-left flex items-center justify-between transition-colors group"
                    >
                      <span className="text-xs font-mono text-slate-300 group-hover:text-white">
                        {id}
                      </span>
                      <ArrowRight className="w-3.5 h-3.5 text-slate-500 group-hover:text-primary-light group-hover:translate-x-0.5 transition-all" />
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Loaded Scenario Snapshot Preview */}
            {loadedScenario && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                className="p-5 rounded-xl bg-slate-950 border border-primary/30 space-y-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-white/10 pb-3">
                  <div>
                    <span className="text-[10px] text-slate-400 uppercase font-mono block">
                      Saved Calculation Snapshot
                    </span>
                    <h3 className="text-sm font-bold text-white">
                      {loadedScenario.result?.policyName || loadedScenario.policyCode}
                    </h3>
                  </div>
                  <ProvenanceBadge
                    status={loadedScenario.result?.calculationStatus}
                    policyCode={loadedScenario.policyCode}
                    isSynthetic={loadedScenario.syntheticExample}
                  />
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                  <div>
                    <span className="text-slate-400 block text-[11px]">Patient / Diagnosis</span>
                    <span className="text-slate-200 font-medium">
                      Age {loadedScenario.patientAge} • {loadedScenario.diagnosis}
                    </span>
                  </div>
                  <div>
                    <span className="text-slate-400 block text-[11px]">Evaluated On</span>
                    <span className="text-slate-200 font-mono text-[11px] flex items-center gap-1 mt-0.5">
                      <Clock className="w-3 h-3 text-slate-500" />
                      {new Date(loadedScenario.createdAt).toLocaleString("en-IN")}
                    </span>
                  </div>
                  <div>
                    <span className="text-slate-400 block text-[11px]">Filing Revision</span>
                    <span className="text-slate-200 font-mono">
                      {loadedScenario.uinRevision || "Statutory Lock"}
                    </span>
                  </div>
                </div>

                {/* Financial Summary Preview */}
                <div className="p-3 rounded-lg bg-white/5 border border-white/5 grid grid-cols-3 gap-2 text-center">
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase">Submitted Bill</span>
                    <span className="text-xs font-mono font-bold text-white">
                      {formatInr(loadedScenario.totalSubmittedBill)}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-emerald-400 block uppercase">Insurer Pay</span>
                    <span className="text-xs font-mono font-bold text-emerald-400">
                      {formatInr(loadedScenario.result?.estimatedInsurerPayment)}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-rose-400 block uppercase">Patient Liab</span>
                    <span className="text-xs font-mono font-bold text-rose-400">
                      {formatInr(loadedScenario.result?.estimatedPatientLiability)}
                    </span>
                  </div>
                </div>

                <div className="flex justify-end pt-2">
                  <button
                    type="button"
                    onClick={handleConfirmOpen}
                    className="px-5 py-2.5 rounded-xl bg-primary hover:bg-primary-dark text-white text-xs font-bold flex items-center gap-2 shadow-lg shadow-primary/20 transition-colors"
                  >
                    <span>Load Historical Snapshot</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </motion.div>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
