"use client";

import React from "react";
import { motion } from "framer-motion";

interface SkeletonCardProps {
  className?: string;
  lines?: number;
}

export function SkeletonCard({ className = "", lines = 3 }: SkeletonCardProps) {
  return (
    <div className={`glass rounded-2xl p-5 animate-pulse ${className}`}>
      <div className="flex items-center gap-3 mb-4">
        <div className="w-10 h-10 rounded-xl bg-white/5" />
        <div className="flex-1">
          <div className="h-4 bg-white/5 rounded-lg w-3/4 mb-2" />
          <div className="h-3 bg-white/5 rounded-lg w-1/2" />
        </div>
      </div>
      {Array.from({ length: lines }).map((_, i) => (
        <div
          key={i}
          className="h-3 bg-white/5 rounded-lg mb-2"
          style={{ width: `${85 - i * 15}%` }}
        />
      ))}
    </div>
  );
}

export function SkeletonDashboard() {
  return (
    <div className="p-6 space-y-6 animate-pulse">
      {/* Welcome skeleton */}
      <div className="glass rounded-2xl p-6 h-28" />

      {/* Stats row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="glass rounded-2xl p-5 h-24" />
        ))}
      </div>

      {/* Main grid */}
      <div className="grid lg:grid-cols-3 gap-6">
        {[1, 2, 3].map((i) => (
          <div key={i} className="glass rounded-2xl p-6 h-64" />
        ))}
      </div>
    </div>
  );
}

export function SkeletonTable({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-3 animate-pulse">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="glass rounded-xl p-4 flex items-center gap-4">
          <div className="w-8 h-8 rounded-lg bg-white/5 flex-shrink-0" />
          <div className="flex-1">
            <div className="h-4 bg-white/5 rounded-lg w-2/3 mb-1" />
            <div className="h-3 bg-white/5 rounded-lg w-1/3" />
          </div>
          <div className="h-6 w-16 bg-white/5 rounded-full" />
        </div>
      ))}
    </div>
  );
}
