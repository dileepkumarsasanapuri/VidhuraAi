"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import { FileText, Filter, Upload, Shield, Loader2, Trash2, AlertTriangle, X } from "lucide-react";
import Sidebar from "@/components/Sidebar";
import PolicyCard, { PolicyData } from "@/components/PolicyCard";
import { api, Policy } from "@/lib/api";
import { useRouter } from "next/navigation";
import { useToast } from "@/context/ToastContext";

export default function PoliciesPage() {
  const router = useRouter();
  const toast = useToast();
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [policyToDelete, setPolicyToDelete] = useState<Policy | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    async function fetchPolicies() {
      try {
        const response = await api.getPolicies();
        if (response.data) {
          setPolicies(response.data);
        }
      } catch (error) {
        console.error("Failed to fetch policies", error);
      } finally {
        setIsLoading(false);
      }
    }
    fetchPolicies();
  }, []);

  const handleDeleteClick = (policyData: PolicyData) => {
    const fullPolicy = policies.find((p) => p.id === policyData.id);
    if (fullPolicy) {
      setPolicyToDelete(fullPolicy);
    }
  };

  const handleConfirmDelete = async () => {
    if (!policyToDelete) return;
    setIsDeleting(true);
    try {
      await api.deletePolicy(policyToDelete.id);
      setPolicies((prev) => prev.filter((p) => p.id !== policyToDelete.id));
      toast.success(
        `Policy "${policyToDelete.name}" was permanently removed.`,
        "Policy Deleted"
      );
      setPolicyToDelete(null);
    } catch (err: any) {
      console.error("Failed to delete policy:", err);
      toast.error(
        err.message || "Failed to delete policy. Please try again.",
        "Deletion Failed"
      );
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/policies" />
      <main className="flex-1 overflow-y-auto">
        <header className="sticky top-0 z-30 glass border-b border-border px-6 h-16 flex items-center justify-between">
          <div className="ml-12 lg:ml-0">
            <h1 className="text-lg font-bold">My Policies</h1>
            <p className="text-xs text-muted">
              {isLoading ? "Loading policies..." : `${policies.length} polic${policies.length === 1 ? 'y' : 'ies'} active`}
            </p>
          </div>
          <Link
            href="/dashboard/upload"
            className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-medium bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-opacity"
          >
            <Upload className="w-3.5 h-3.5" /> Upload Policy
          </Link>
        </header>

        <div className="p-6">
          {isLoading ? (
            <div className="flex items-center justify-center py-24">
              <Loader2 className="w-8 h-8 text-primary animate-spin" />
            </div>
          ) : policies.length === 0 ? (
            <div className="max-w-md mx-auto text-center py-16 px-6 glass rounded-2xl border border-white/5">
              <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center border border-primary/20">
                <FileText className="w-8 h-8 text-primary-light" />
              </div>
              <h3 className="text-lg font-bold mb-2">No policies uploaded yet</h3>
              <p className="text-sm text-muted mb-6 leading-relaxed">
                Upload your genuine insurance policy documents (PDF) to unlock AI-powered clause analysis, hidden condition alerts, and claim readiness simulations.
              </p>
              <Link
                href="/dashboard/upload"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-all glow-primary"
              >
                <Upload className="w-4 h-4" /> Upload Your First Policy
              </Link>
            </div>
          ) : (
            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
              {policies.map((p, i) => (
                <PolicyCard
                  key={p.id}
                  policy={p as any}
                  delay={i * 0.08}
                  onClick={() => router.push(`/dashboard/policies/${p.id}`)}
                  onDelete={handleDeleteClick}
                />
              ))}
              {/* Add new policy card */}
              <Link href="/dashboard/upload">
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: policies.length * 0.08 }}
                  className="glass rounded-2xl p-5 border-2 border-dashed border-border hover:border-primary/30 flex flex-col items-center justify-center min-h-[200px] cursor-pointer group transition-all h-full"
                >
                  <div className="w-12 h-12 rounded-xl bg-white/5 flex items-center justify-center mb-3 group-hover:bg-primary/10 transition-colors">
                    <FileText className="w-6 h-6 text-muted group-hover:text-primary-light transition-colors" />
                  </div>
                  <p className="text-sm font-medium text-muted group-hover:text-foreground transition-colors">Add New Policy</p>
                  <p className="text-xs text-muted/50 mt-1">Upload a policy PDF</p>
                </motion.div>
              </Link>
            </div>
          )}
        </div>
      </main>

      {/* Delete Confirmation Modal */}
      <AnimatePresence>
        {policyToDelete && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => !isDeleting && setPolicyToDelete(null)}
              className="absolute inset-0 bg-black/70 backdrop-blur-sm"
            />

            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="relative w-full max-w-md glass rounded-3xl p-6 border border-red-500/30 shadow-2xl z-10 space-y-4"
            >
              <div className="flex items-start justify-between">
                <div className="w-12 h-12 rounded-2xl bg-red-500/10 border border-red-500/20 flex items-center justify-center text-red-400">
                  <AlertTriangle className="w-6 h-6" />
                </div>
                <button
                  type="button"
                  onClick={() => !isDeleting && setPolicyToDelete(null)}
                  className="p-1 rounded-lg text-muted hover:text-white transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div>
                <h3 className="text-lg font-bold text-foreground">Delete Policy</h3>
                <p className="text-xs text-muted mt-1">
                  Are you sure you want to permanently delete this policy?
                </p>
              </div>

              <div className="p-3.5 glass rounded-2xl border border-white/5 space-y-1">
                <p className="text-sm font-semibold text-foreground truncate">{policyToDelete.name}</p>
                <p className="text-xs text-muted">{policyToDelete.provider} • {policyToDelete.type}</p>
                <p className="text-[11px] text-red-400/90 pt-1">
                  ⚠️ All extracted clauses, hidden exclusions, waiting periods, and simulation history will be permanently deleted.
                </p>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  disabled={isDeleting}
                  onClick={() => setPolicyToDelete(null)}
                  className="px-4 py-2 text-xs font-semibold rounded-xl glass hover:bg-white/10 text-muted hover:text-white transition-all disabled:opacity-50"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={isDeleting}
                  onClick={handleConfirmDelete}
                  className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-xl bg-red-600 hover:bg-red-500 text-white shadow-lg shadow-red-600/30 transition-all disabled:opacity-50"
                >
                  {isDeleting ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      Deleting...
                    </>
                  ) : (
                    <>
                      <Trash2 className="w-3.5 h-3.5" />
                      Yes, Delete Policy
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
