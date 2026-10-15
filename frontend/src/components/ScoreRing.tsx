"use client";

import React from "react";
import { motion } from "framer-motion";

interface ScoreRingProps {
  score: number;
  maxScore?: number;
  size?: number;
  strokeWidth?: number;
  label?: string;
  color?: string;
  showPercentage?: boolean;
}

export default function ScoreRing({
  score,
  maxScore = 100,
  size = 120,
  strokeWidth = 8,
  label,
  color = "#6366f1",
  showPercentage = true,
}: ScoreRingProps) {
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const percentage = (score / maxScore) * 100;
  const offset = circumference - (percentage / 100) * circumference;

  const getColor = () => {
    if (color !== "#6366f1") return color;
    if (percentage >= 80) return "#10b981";
    if (percentage >= 60) return "#06b6d4";
    if (percentage >= 40) return "#f59e0b";
    return "#ef4444";
  };

  const ringColor = getColor();

  return (
    <div className="flex flex-col items-center gap-2">
      <div className="relative" style={{ width: size, height: size }}>
        <svg
          width={size}
          height={size}
          viewBox={`0 0 ${size} ${size}`}
          className="-rotate-90"
        >
          {/* Background ring */}
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke="rgba(255,255,255,0.05)"
            strokeWidth={strokeWidth}
          />
          {/* Score ring */}
          <motion.circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke={ringColor}
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            strokeDasharray={circumference}
            initial={{ strokeDashoffset: circumference }}
            animate={{ strokeDashoffset: offset }}
            transition={{ duration: 1.5, ease: "easeOut" }}
            style={{
              filter: `drop-shadow(0 0 6px ${ringColor}40)`,
            }}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-xl font-bold" style={{ color: ringColor }}>
            {showPercentage ? `${Math.round(percentage)}%` : score}
          </span>
        </div>
      </div>
      {label && (
        <span className="text-xs text-muted font-medium text-center">
          {label}
        </span>
      )}
    </div>
  );
}
