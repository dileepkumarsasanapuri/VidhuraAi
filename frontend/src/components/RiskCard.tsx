"use client";

import React from "react";
import { motion } from "framer-motion";
import { AlertTriangle, AlertCircle, Info, CheckCircle } from "lucide-react";

export interface RiskItem {
  id: string;
  title: string;
  description: string;
  severity: "high" | "medium" | "low" | "info";
  category: string;
}

interface RiskCardProps {
  risk: RiskItem;
  delay?: number;
}

const severityConfig = {
  high: {
    icon: AlertTriangle,
    bg: "from-red-500/15 to-red-600/5",
    border: "border-red-500/20",
    badge: "bg-red-500/15 text-red-400",
    iconColor: "text-red-400",
    label: "High Risk",
  },
  medium: {
    icon: AlertCircle,
    bg: "from-amber-500/15 to-amber-600/5",
    border: "border-amber-500/20",
    badge: "bg-amber-500/15 text-amber-400",
    iconColor: "text-amber-400",
    label: "Medium Risk",
  },
  low: {
    icon: CheckCircle,
    bg: "from-emerald-500/15 to-emerald-600/5",
    border: "border-emerald-500/20",
    badge: "bg-emerald-500/15 text-emerald-400",
    iconColor: "text-emerald-400",
    label: "Low Risk",
  },
  info: {
    icon: Info,
    bg: "from-blue-500/15 to-blue-600/5",
    border: "border-blue-500/20",
    badge: "bg-blue-500/15 text-blue-400",
    iconColor: "text-blue-400",
    label: "Info",
  },
};

export default function RiskCard({ risk, delay = 0 }: RiskCardProps) {
  const config = severityConfig[risk.severity];
  const Icon = config.icon;

  return (
    <motion.div
      initial={{ opacity: 0, x: -20 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay, duration: 0.4 }}
      className={`glass glass-hover rounded-xl p-4 border ${config.border} transition-all duration-300`}
    >
      <div className="flex items-start gap-3">
        <div
          className={`w-9 h-9 rounded-lg bg-gradient-to-br ${config.bg} flex items-center justify-center flex-shrink-0`}
        >
          <Icon className={`w-4 h-4 ${config.iconColor}`} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1 flex-wrap">
            <h4 className="text-sm font-semibold">{risk.title}</h4>
            <span
              className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${config.badge}`}
            >
              {config.label}
            </span>
          </div>
          <p className="text-xs text-muted leading-relaxed">
            {risk.description}
          </p>
          <span className="inline-block mt-2 text-[10px] text-muted/60 bg-white/5 px-2 py-0.5 rounded">
            {risk.category}
          </span>
        </div>
      </div>
    </motion.div>
  );
}
