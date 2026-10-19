"use client";

import React from "react";
import { ShieldCheck, AlertTriangle, HelpCircle, FileWarning } from "lucide-react";

interface ProvenanceBadgeProps {
  status?: string;
  sourceHash?: string;
  isSynthetic?: boolean;
  policyCode?: string;
  reviewerApprovalStatus?: string;
  className?: string;
}

/**
 * Strict evidence provenance badge for policies and claim scenarios.
 *
 * Strict Provenance Rules:
 * 1. A green 'Verified Contractual Wording' badge CANNOT be produced merely by frontend fixture metadata.
 * 2. It strictly requires a genuine 64-character SHA-256 cryptographic document hash received from the backend,
 *    and cannot contain mock, placeholder, or synthetic fixture indicators.
 * 3. HDFC ERGO demo models and synthetic test cases are explicitly badged as 'Synthetic Demo Fixture'.
 * 4. Policies pending reviewer sign-off are badged as 'Review Pending'.
 * 5. Policies with ambiguous terms are badged as 'Manual Review Required'.
 * 6. Policies without verified statutory SHA-256 hashes are badged as 'Unverified Document'.
 */
export default function ProvenanceBadge({
  status,
  sourceHash,
  isSynthetic,
  policyCode,
  reviewerApprovalStatus,
  className = "",
}: ProvenanceBadgeProps) {
  const cleanHash = sourceHash?.replace(/^sha256:/i, "").trim() || "";
  const isValidSha256 = /^[a-fA-F0-9]{64}$/.test(cleanHash);

  // 1. Synthetic Fixture Gate
  const isFixtureDemo =
    Boolean(isSynthetic) ||
    reviewerApprovalStatus === "SYNTHETIC_DEMO" ||
    Boolean(policyCode?.includes("HDFC")) ||
    cleanHash.includes("mock") ||
    cleanHash.includes("placeholder") ||
    cleanHash.includes("fixture");

  if (isFixtureDemo) {
    return (
      <div
        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-500/10 text-amber-400 border border-amber-500/30 ${className}`}
        title="Illustrative demonstration fixture. Calculations are grounded in synthetic test clauses and not yet extracted from an official statutory IRDAI PDF filing."
      >
        <AlertTriangle className="w-3.5 h-3.5 text-amber-400 shrink-0" />
        <span>Synthetic Demo Fixture</span>
      </div>
    );
  }

  // 2. Draft / Review Pending Gate
  if (
    status === "EXTRACTION_PENDING_REVIEW" ||
    reviewerApprovalStatus === "EXTRACTION_PENDING_REVIEW"
  ) {
    return (
      <div
        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-blue-500/10 text-blue-400 border border-blue-500/30 ${className}`}
        title="Extraction complete from policy wording PDF; pending human reviewer verification."
      >
        <HelpCircle className="w-3.5 h-3.5 text-blue-400 shrink-0" />
        <span>Review Pending Approval</span>
      </div>
    );
  }

  // 3. Manual Review Required Gate
  if (status === "MANUAL_REVIEW_REQUIRED") {
    return (
      <div
        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-purple-500/10 text-purple-400 border border-purple-500/30 ${className}`}
        title="Contractual clauses in this policy are ambiguous and require manual compliance clarification."
      >
        <HelpCircle className="w-3.5 h-3.5 text-purple-400 shrink-0" />
        <span>Manual Review Required</span>
      </div>
    );
  }

  // 4. Cryptographic Grounding Gate: Must have valid 64-char SHA-256 from backend
  if (!isValidSha256) {
    return (
      <div
        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-white/10 ${className}`}
        title="Document hash is unverified or missing cryptographic checksum against an official statutory IRDAI PDF filing."
      >
        <FileWarning className="w-3.5 h-3.5 text-slate-400 shrink-0" />
        <span>Unverified Document Hash</span>
      </div>
    );
  }

  // 5. Reviewer Approval Check: Green verified wording requires approved status
  const isApproved =
    status === "VERIFIED_OFFICIAL_CATALOG" ||
    status === "FULL_DETERMINISTIC_VERIFIED" ||
    reviewerApprovalStatus === "REVIEWER_APPROVED";

  if (!isApproved) {
    return (
      <div
        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-white/10 ${className}`}
        title="Policy wording document has cryptographic hash but has not achieved official reviewer approval."
      >
        <FileWarning className="w-3.5 h-3.5 text-slate-400 shrink-0" />
        <span>Pending Reviewer Approval</span>
      </div>
    );
  }

  // 6. Genuine Acquired Document Provenance (e.g. New India, UIIC with verified SHA-256 & reviewer sign-off)
  return (
    <div
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 ${className}`}
      title={`Grounded in genuine acquired policy document (SHA-256: ${cleanHash.slice(0, 16)}...) with reviewer sign-off`}
    >
      <ShieldCheck className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
      <span>Verified Contractual Wording</span>
    </div>
  );
}
