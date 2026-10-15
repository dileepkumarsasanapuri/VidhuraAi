"use client";

import React from "react";
import { motion } from "framer-motion";
import { LucideIcon } from "lucide-react";

interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: LucideIcon;
  trend?: { value: number; positive: boolean };
  color?: string;
  delay?: number;
}

export default function StatCard({
  title,
  value,
  subtitle,
  icon: Icon,
  trend,
  color = "primary",
  delay = 0,
}: StatCardProps) {
  const colorMap: Record<string, { bg: string; text: string; glow: string }> = {
    primary: {
      bg: "from-indigo-500/15 to-indigo-600/5",
      text: "text-indigo-400",
      glow: "shadow-indigo-500/10",
    },
    accent: {
      bg: "from-cyan-500/15 to-cyan-600/5",
      text: "text-cyan-400",
      glow: "shadow-cyan-500/10",
    },
    success: {
      bg: "from-emerald-500/15 to-emerald-600/5",
      text: "text-emerald-400",
      glow: "shadow-emerald-500/10",
    },
    warning: {
      bg: "from-amber-500/15 to-amber-600/5",
      text: "text-amber-400",
      glow: "shadow-amber-500/10",
    },
    danger: {
      bg: "from-red-500/15 to-red-600/5",
      text: "text-red-400",
      glow: "shadow-red-500/10",
    },
  };

  const c = colorMap[color] || colorMap.primary;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.5 }}
      className={`glass glass-hover rounded-2xl p-5 hover:shadow-lg ${c.glow} transition-all duration-300`}
    >
      <div className="flex items-start justify-between mb-3">
        <div
          className={`w-10 h-10 rounded-xl bg-gradient-to-br ${c.bg} flex items-center justify-center`}
        >
          <Icon className={`w-5 h-5 ${c.text}`} />
        </div>
        {trend && (
          <span
            className={`text-xs font-medium px-2 py-0.5 rounded-full ${
              trend.positive
                ? "bg-emerald-500/15 text-emerald-400"
                : "bg-red-500/15 text-red-400"
            }`}
          >
            {trend.positive ? "+" : ""}
            {trend.value}%
          </span>
        )}
      </div>
      <h3 className="text-2xl font-bold mb-1">{value}</h3>
      <p className="text-sm text-muted">{title}</p>
      {subtitle && <p className="text-xs text-muted/70 mt-1">{subtitle}</p>}
    </motion.div>
  );
}
