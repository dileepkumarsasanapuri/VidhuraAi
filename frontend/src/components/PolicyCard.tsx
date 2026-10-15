"use client";

import React from "react";
import { motion } from "framer-motion";
import { FileText, Shield, Clock, ChevronRight, Trash2 } from "lucide-react";

export interface PolicyData {
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
}

interface PolicyCardProps {
  policy: PolicyData;
  delay?: number;
  onClick?: () => void;
  onDelete?: (policy: PolicyData) => void;
}

export default function PolicyCard({
  policy,
  delay = 0,
  onClick,
  onDelete,
}: PolicyCardProps) {
  const statusConfig = {
    active: { label: "Active", className: "bg-emerald-500/15 text-emerald-400" },
    expiring: { label: "Expiring Soon", className: "bg-amber-500/15 text-amber-400" },
    expired: { label: "Expired", className: "bg-red-500/15 text-red-400" },
  };

  const riskConfig = {
    high: "text-red-400",
    medium: "text-amber-400",
    low: "text-emerald-400",
  };

  const statusC = statusConfig[policy.status];

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.5 }}
      onClick={onClick}
      className="glass glass-hover rounded-2xl p-5 cursor-pointer group transition-all duration-300"
    >
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500/15 to-cyan-500/15 flex items-center justify-center">
            <FileText className="w-5 h-5 text-indigo-400" />
          </div>
          <div>
            <h3 className="text-sm font-semibold">{policy.name}</h3>
            <p className="text-xs text-muted">{policy.provider}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`text-[10px] px-2.5 py-1 rounded-full font-medium ${statusC.className}`}
          >
            {statusC.label}
          </span>
          {onDelete && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onDelete(policy);
              }}
              className="p-1.5 rounded-lg text-muted/60 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              title="Delete Policy"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3 mb-4">
        <div>
          <p className="text-[10px] text-muted mb-0.5">Premium</p>
          <p className="text-sm font-semibold">{policy.premium}</p>
        </div>
        <div>
          <p className="text-[10px] text-muted mb-0.5">Coverage</p>
          <p className="text-sm font-semibold">{policy.coverage}</p>
        </div>
        <div>
          <p className="text-[10px] text-muted mb-0.5">Health Score</p>
          <p className={`text-sm font-semibold ${riskConfig[policy.riskLevel]}`}>
            {policy.healthScore}/100
          </p>
        </div>
      </div>

      <div className="flex items-center justify-between pt-3 border-t border-border">
        <div className="flex items-center gap-1.5 text-xs text-muted">
          <Clock className="w-3 h-3" />
          <span>Expires {policy.expiryDate}</span>
        </div>
        <div className="flex items-center gap-1 text-xs text-primary-light opacity-0 group-hover:opacity-100 transition-opacity">
          <span>View Details</span>
          <ChevronRight className="w-3 h-3" />
        </div>
      </div>
    </motion.div>
  );
}
