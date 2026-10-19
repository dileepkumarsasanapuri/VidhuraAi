"use client";

import React, { useState, useEffect } from "react";
import {
  Calculator,
  Plus,
  Trash2,
  Copy,
  AlertCircle,
  Sparkles,
  RotateCcw,
  Building2,
  User,
  Stethoscope,
  Calendar,
  Layers,
  FileText,
  DollarSign,
  HelpCircle,
  CheckCircle2,
} from "lucide-react";
import {
  BillItemCategory,
  BillLineItem,
  ClaimSimulationRequest,
  SampleScenario,
  api,
} from "@/lib/api";
import ProvenanceBadge from "./ProvenanceBadge";

interface PolicyOption {
  id: string;
  code: string;
  name: string;
  provider: string;
  irdaUin: string;
  revision: string;
  sourceHash?: string;
  isSynthetic: boolean;
  defaultSumInsured: number;
}

// Built-in verified and synthetic policy options
const KNOWN_POLICIES: PolicyOption[] = [
  {
    id: "UIIHLIP22070V042122",
    code: "UIIHLIP22070V042122",
    name: "Family Medicare Policy",
    provider: "United India Insurance Company Limited",
    irdaUin: "UIIHLIP22070V042122",
    revision: "V042122",
    sourceHash: "9728ea60fce9e3e99b21c8b70abbf5d0b43236cbbc78901856311f77920efb3b",
    isSynthetic: false,
    defaultSumInsured: 500000,
  },
  {
    id: "NIAHLIP25040V102425",
    code: "NIAHLIP25040V102425",
    name: "New India Mediclaim Policy",
    provider: "The New India Assurance Company Limited",
    irdaUin: "NIAHLIP25040V102425",
    revision: "V102425",
    sourceHash: "b37d2519f4fe00e75f28727168ca6aafa9093f8c723fafa7db2f12e887fcea1f",
    isSynthetic: false,
    defaultSumInsured: 500000,
  },
  {
    id: "NIAHLIP21279V032021",
    code: "NIAHLIP21279V032021",
    name: "New India Premier Mediclaim",
    provider: "The New India Assurance Company Limited",
    irdaUin: "NIAHLIP21279V032021",
    revision: "V032021",
    sourceHash: "2d827d7c96d4d66687408387f69991d4c9e72d7c11ab6901ed6dc610d9870743",
    isSynthetic: false,
    defaultSumInsured: 1500000,
  },
  {
    id: "HDFHLIP21147V012021",
    code: "HDFHLIP21147V012021",
    name: "Optima Secure",
    provider: "HDFC ERGO General Insurance Co. Ltd.",
    irdaUin: "HDFHLIP21147V012021",
    revision: "V012021",
    isSynthetic: true, // Explicitly marked as synthetic demo fixture
    defaultSumInsured: 500000,
  },
];

const BILL_CATEGORIES: { value: BillItemCategory; label: string; group: string }[] = [
  { value: "ROOM_RENT", label: "Room Rent / Boarding", group: "Hospitalization" },
  { value: "ICU_CHARGES", label: "ICU / ICCU Charges", group: "Hospitalization" },
  { value: "NURSING_CHARGES", label: "Nursing & Monitoring", group: "Hospitalization" },
  { value: "SURGEON_FEES", label: "Surgeon & Anesthesia Fees", group: "Professional" },
  { value: "OT_CHARGES", label: "Operation Theatre (OT) Charges", group: "Professional" },
  { value: "MEDICINES", label: "Medicines & Pharmacy", group: "Medical" },
  { value: "DIAGNOSTICS", label: "Pathology & Radiology Diagnostics", group: "Medical" },
  { value: "CONSUMABLES", label: "Consumables & PPE (Non-Medical)", group: "Non-Medical" },
  { value: "IMPLANTS", label: "Stents, Implants & Prosthetics", group: "Devices" },
  { value: "AMBULANCE", label: "Road Ambulance Charges", group: "Ancillary" },
  { value: "OTHER", label: "Other Hospital Expenses", group: "Ancillary" },
];

const ROOM_CATEGORIES = [
  { value: "GENERAL_WARD", label: "General Ward" },
  { value: "TWIN_SHARING", label: "Twin Sharing AC" },
  { value: "SINGLE_PRIVATE_AC", label: "Single Private AC" },
  { value: "DELUXE_SUITE", label: "Deluxe Suite / Executive" },
];

const SUM_INSURED_PRESETS = [300000, 500000, 1000000, 1500000, 2500000, 5000000];

interface ClaimScenarioFormProps {
  onSubmit: (request: ClaimSimulationRequest) => Promise<void>;
  isLoading: boolean;
  prefillRequest?: ClaimSimulationRequest | null;
}

export default function ClaimScenarioForm({
  onSubmit,
  isLoading,
  prefillRequest,
}: ClaimScenarioFormProps) {
  // Policies list
  const [policies, setPolicies] = useState<PolicyOption[]>(KNOWN_POLICIES);
  const [selectedPolicyCode, setSelectedPolicyCode] = useState<string>("UIIHLIP22070V042122");
  const [exactRevision, setExactRevision] = useState<string>("V042122");

  // Sample Scenarios
  const [sampleScenarios, setSampleScenarios] = useState<SampleScenario[]>([]);
  const [selectedSampleId, setSelectedSampleId] = useState<string>("");

  // Patient & Admission Form State
  const [patientAge, setPatientAge] = useState<number>(42);
  const [diagnosis, setDiagnosis] = useState<string>("Acute Appendicitis (Laparoscopic Appendectomy)");
  const [continuityMonths, setContinuityMonths] = useState<number>(24);
  const [hospitalType, setHospitalType] = useState<string>("NETWORK_CASHLESS");
  const [roomCategory, setRoomCategory] = useState<string>("SINGLE_PRIVATE_AC");
  const [sumInsured, setSumInsured] = useState<number>(500000);
  const [priorClaims, setPriorClaims] = useState<number>(0);
  const [accidentalInjury, setAccidentalInjury] = useState<boolean>(false);
  const [preExistingCondition, setPreExistingCondition] = useState<boolean>(false);
  const [subsequentClaimForSameIllness, setSubsequentClaimForSameIllness] = useState<boolean>(false);

  // Bill Line Items
  const [billLines, setBillLines] = useState<BillLineItem[]>([
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
      description: "Surgeon, Anesthetist & Surgical Team Professional Fee",
      unitPrice: 45000,
      quantity: 1,
      totalAmount: 45000,
    },
    {
      lineId: "L3",
      category: "OT_CHARGES",
      description: "Laparoscopic Operation Theatre & Equipment",
      unitPrice: 28000,
      quantity: 1,
      totalAmount: 28000,
    },
    {
      lineId: "L4",
      category: "MEDICINES",
      description: "Hospital Pharmacy (IV Fluids, Antibiotics & Analgesics)",
      unitPrice: 18500,
      quantity: 1,
      totalAmount: 18500,
    },
    {
      lineId: "L5",
      category: "CONSUMABLES",
      description: "PPE, Gloves, Trocar, Surgical Drapes & Disposables",
      unitPrice: 12000,
      quantity: 1,
      totalAmount: 12000,
    },
    {
      lineId: "L6",
      category: "DIAGNOSTICS",
      description: "Pre-op Ultrasound Abdomen, Blood Panel & ECG",
      unitPrice: 8500,
      quantity: 1,
      totalAmount: 8500,
    },
  ]);

  // Validation errors
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Load Policies and Sample Scenarios from API on mount
  useEffect(() => {
    let isMounted = true;

    // Fetch sample scenarios
    api
      .getSampleClaimScenarios()
      .then((res) => {
        if (isMounted && res.success && res.data && res.data.length > 0) {
          setSampleScenarios(res.data);
        }
      })
      .catch(() => {
        // Fallback to local sample presets if endpoint is pending
      });

    // Fetch verified policies from catalog
    api
      .getCatalogPolicies("VERIFIED")
      .then((res) => {
        if (isMounted && res.success && res.data && res.data.length > 0) {
          const catalogPolicies: PolicyOption[] = res.data.map((p: any) => ({
            id: p.id || p.policyCode || p.irdaUin,
            code: p.policyCode || p.irdaUin,
            name: p.policyName || p.planName || "Verified Health Policy",
            provider: p.provider || "Insurance Provider",
            irdaUin: p.irdaUin || p.policyCode,
            revision: p.uinRevision || "V01",
            isSynthetic: Boolean(p.syntheticFixture) || Boolean(p.isSynthetic) || (p.sourceDocumentHash || "").includes("mock") || p.policyCode?.includes("HDFC") || p.reviewerApprovalStatus === "SYNTHETIC_DEMO",
            defaultSumInsured: 500000,
          }));

          // Merge without duplicates based on IRDA UIN
          setPolicies((prev) => {
            const existingCodes = new Set(prev.map((x) => x.irdaUin.toUpperCase()));
            const newOnes = catalogPolicies.filter(
              (cp) => !existingCodes.has(cp.irdaUin.toUpperCase())
            );
            return [...prev, ...newOnes];
          });
        }
      })
      .catch(() => {
        // Retain KNOWN_POLICIES
      });

    return () => {
      isMounted = false;
    };
  }, []);

  // Sync when prefillRequest changes
  useEffect(() => {
    if (prefillRequest) {
      applyScenarioRequest(prefillRequest);
    }
  }, [prefillRequest]);

  const selectedPolicy =
    policies.find((p) => p.code === selectedPolicyCode || p.id === selectedPolicyCode) ||
    policies[0];

  const handlePolicyChange = (code: string) => {
    setSelectedPolicyCode(code);
    const pol = policies.find((p) => p.code === code || p.id === code);
    if (pol) {
      setExactRevision(pol.revision);
      if (pol.defaultSumInsured && sumInsured < 100000) {
        setSumInsured(pol.defaultSumInsured);
      }
    }
  };

  const applyScenarioRequest = (req: ClaimSimulationRequest) => {
    setSelectedPolicyCode(req.policyId);
    if (req.uinRevision) setExactRevision(req.uinRevision);
    setPatientAge(req.patientAge || 40);
    setDiagnosis(req.diagnosis || "");
    setContinuityMonths(req.policyContinuityMonths ?? 12);
    setHospitalType(req.hospitalType || "NETWORK_CASHLESS");
    setRoomCategory(req.hospitalRoomCategoryOpted || "SINGLE_PRIVATE_AC");
    setSumInsured(req.selectedSumInsured || 500000);
    setPriorClaims(req.priorClaimsInCurrentYear || 0);
    setAccidentalInjury(Boolean(req.accidentalInjury));
    setPreExistingCondition(Boolean(req.preExistingCondition));
    setSubsequentClaimForSameIllness(Boolean(req.subsequentClaimForSameIllness));

    if (req.billLines && req.billLines.length > 0) {
      setBillLines(
        req.billLines.map((line, idx) => ({
          ...line,
          lineId: line.lineId || `L${idx + 1}`,
          totalAmount: line.totalAmount ?? line.unitPrice * (line.quantity || 1),
        }))
      );
    }
    setErrors({});
  };

  const handleSampleSelect = (sampleId: string) => {
    setSelectedSampleId(sampleId);
    const sample = sampleScenarios.find((s) => s.sampleId === sampleId);
    if (sample && sample.request) {
      applyScenarioRequest(sample.request);
    }
  };

  // Bill Line item management
  const handleAddBillLine = () => {
    const newLineId = `L${billLines.length + 1}`;
    setBillLines([
      ...billLines,
      {
        lineId: newLineId,
        category: "MEDICINES",
        description: "Pharmacy Medication",
        unitPrice: 5000,
        quantity: 1,
        totalAmount: 5000,
      },
    ]);
  };

  const handleRemoveBillLine = (index: number) => {
    if (billLines.length <= 1) {
      setErrors((prev) => ({ ...prev, bill: "At least one bill item is required." }));
      return;
    }
    const updated = billLines.filter((_, i) => i !== index);
    setBillLines(updated);
  };

  const handleDuplicateBillLine = (index: number) => {
    const item = billLines[index];
    const newLineId = `L${billLines.length + 1}`;
    const newItem: BillLineItem = {
      ...item,
      lineId: newLineId,
      description: `${item.description} (Copy)`,
    };
    setBillLines([...billLines, newItem]);
  };

  const handleBillLineChange = (
    index: number,
    field: keyof BillLineItem,
    value: string | number
  ) => {
    const updated = [...billLines];
    const current = { ...updated[index] };

    if (field === "unitPrice") {
      const price = Math.max(0, Number(value) || 0);
      current.unitPrice = price;
      current.totalAmount = price * current.quantity;
    } else if (field === "quantity") {
      const qty = Math.max(1, parseInt(String(value), 10) || 1);
      current.quantity = qty;
      current.totalAmount = current.unitPrice * qty;
    } else if (field === "totalAmount") {
      current.totalAmount = Math.max(0, Number(value) || 0);
    } else if (field === "category") {
      current.category = value as BillItemCategory;
    } else if (field === "description") {
      current.description = String(value);
    }

    updated[index] = current;
    setBillLines(updated);
  };

  // Calculate total submitted bill
  const totalSubmittedBill = billLines.reduce(
    (sum, item) => sum + (Number(item.totalAmount) || 0),
    0
  );

  // Client-side form validation before submitting
  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!selectedPolicyCode) {
      newErrors.policy = "Please select an approved health insurance policy.";
    }
    if (!diagnosis || diagnosis.trim().length === 0) {
      newErrors.diagnosis = "Clinical diagnosis or procedure is required.";
    }
    if (patientAge < 0 || patientAge > 120) {
      newErrors.age = "Patient age must be between 0 and 120.";
    }
    if (sumInsured < 100000) {
      newErrors.sumInsured = "Sum Insured must be at least ₹1,00,000.";
    }
    if (billLines.length === 0) {
      newErrors.bill = "Hospital bill must contain at least one itemized line.";
    }

    // Check each line item
    billLines.forEach((item, idx) => {
      if (!item.description || item.description.trim().length === 0) {
        newErrors[`line_${idx}_desc`] = `Line ${idx + 1}: Description is required.`;
      }
      if (item.totalAmount < 0) {
        newErrors[`line_${idx}_amount`] = `Line ${idx + 1}: Amount cannot be negative.`;
      }
    });

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) return;

    const request: ClaimSimulationRequest = {
      policyId: selectedPolicy.code || selectedPolicy.id,
      uinRevision: exactRevision || selectedPolicy.revision,
      patientAge,
      diagnosis: diagnosis.trim(),
      accidentalInjury,
      preExistingCondition,
      policyContinuityMonths: continuityMonths,
      hospitalType,
      hospitalRoomCategoryOpted: roomCategory,
      selectedSumInsured: sumInsured,
      priorClaimsInCurrentYear: priorClaims,
      subsequentClaimForSameIllness,
      billLines,
    };

    await onSubmit(request);
  };

  const handleReset = () => {
    setSelectedPolicyCode("UIIHLIP22070V042122");
    setExactRevision("V042122");
    setPatientAge(42);
    setDiagnosis("Acute Appendicitis (Laparoscopic Appendectomy)");
    setContinuityMonths(24);
    setHospitalType("NETWORK_CASHLESS");
    setRoomCategory("SINGLE_PRIVATE_AC");
    setSumInsured(500000);
    setPriorClaims(0);
    setAccidentalInjury(false);
    setPreExistingCondition(false);
    setSubsequentClaimForSameIllness(false);
    setSelectedSampleId("");
    setErrors({});
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-8 animate-in fade-in duration-300">
      {/* 1. Curated Sample Scenarios Bar */}
      {sampleScenarios.length > 0 && (
        <div className="glass rounded-2xl p-5 border border-white/10 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-accent" />
              <h3 className="text-sm font-semibold text-white">
                Educational Sample Scenarios
              </h3>
            </div>
            <span className="text-[11px] text-amber-400 font-medium px-2 py-0.5 rounded-full bg-amber-500/10 border border-amber-500/20">
              Synthetic Demonstration Fixtures
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {sampleScenarios.map((sample) => (
              <button
                type="button"
                key={sample.sampleId}
                onClick={() => handleSampleSelect(sample.sampleId)}
                className={`p-3 rounded-xl border text-left transition-all ${
                  selectedSampleId === sample.sampleId
                    ? "bg-primary/20 border-primary shadow-lg shadow-primary/10"
                    : "bg-white/5 border-white/10 hover:bg-white/10 hover:border-white/20"
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs font-bold text-white truncate">
                    {sample.title}
                  </span>
                  {selectedSampleId === sample.sampleId && (
                    <CheckCircle2 className="w-3.5 h-3.5 text-accent shrink-0 ml-1" />
                  )}
                </div>
                <p className="text-[11px] text-slate-400 line-clamp-2">
                  {sample.description}
                </p>
                <div className="mt-2 flex items-center justify-between text-[10px] text-slate-500">
                  <span className="font-mono">{sample.targetPolicyName}</span>
                  <span className="text-amber-400/80">Synthetic Fixture</span>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 2. Policy Selection & Revision Lock */}
      <div className="glass rounded-2xl p-6 border border-white/10 space-y-5">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-2 border-b border-white/10 pb-4">
          <div>
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              <Building2 className="w-5 h-5 text-primary-light" />
              1. Policy Selection & Contractual Revision Lock
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Select an approved policy. The calculation engine locks strictly to the verified statutory revision.
            </p>
          </div>
          <ProvenanceBadge
            isSynthetic={selectedPolicy?.isSynthetic}
            policyCode={selectedPolicy?.code}
            sourceHash={selectedPolicy?.sourceHash}
            status="FULL_DETERMINISTIC_VERIFIED"
            className="self-start md:self-auto"
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Policy Dropdown */}
          <div className="md:col-span-2 space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Approved Policy & Insurer *
            </label>
            <select
              value={selectedPolicyCode}
              onChange={(e) => handlePolicyChange(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-white text-sm focus:outline-none focus:border-primary transition-colors"
            >
              {policies.map((p) => (
                <option key={p.id} value={p.code}>
                  {p.name} — {p.provider} ({p.irdaUin}) {p.isSynthetic ? "[Synthetic Fixture]" : "[Verified Wording]"}
                </option>
              ))}
            </select>
            {errors.policy && (
              <p className="text-xs text-rose-400 flex items-center gap-1">
                <AlertCircle className="w-3.5 h-3.5" /> {errors.policy}
              </p>
            )}
          </div>

          {/* Exact Revision */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
              <span>Statutory Revision</span>
              <span className="text-[10px] text-slate-500 font-mono">UIN Locked</span>
            </label>
            <input
              type="text"
              value={exactRevision}
              onChange={(e) => setExactRevision(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-white text-sm font-mono focus:outline-none focus:border-primary transition-colors"
              placeholder="e.g. V042122"
            />
            <p className="text-[11px] text-slate-500">
              Engine verifies clauses against this specific regulatory filing revision.
            </p>
          </div>
        </div>

        {/* Selected Policy Info Card */}
        {selectedPolicy && (
          <div className="p-3.5 rounded-xl bg-slate-950/60 border border-white/5 flex flex-wrap items-center justify-between gap-3 text-xs">
            <div className="space-y-0.5">
              <span className="text-slate-400">IRDAI UIN: </span>
              <span className="text-slate-200 font-mono font-medium">{selectedPolicy.irdaUin}</span>
              <span className="text-slate-500 mx-2">•</span>
              <span className="text-slate-400">Provider: </span>
              <span className="text-slate-200 font-medium">{selectedPolicy.provider}</span>
            </div>
            {selectedPolicy.isSynthetic ? (
              <span className="text-amber-400 text-[11px]">
                ⚠ Demonstrative model: Ground truth grounded in synthetic test clauses
              </span>
            ) : (
              <span className="text-emerald-400 text-[11px]">
                ✓ Extracted from genuine acquired policy document
              </span>
            )}
          </div>
        )}
      </div>

      {/* 3. Patient, Hospital & Coverage Parameters */}
      <div className="glass rounded-2xl p-6 border border-white/10 space-y-6">
        <div className="border-b border-white/10 pb-4">
          <h2 className="text-base font-bold text-white flex items-center gap-2">
            <Stethoscope className="w-5 h-5 text-accent" />
            2. Patient Clinical Context & Hospital Details
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Key clinical and policy continuity variables determining waiting period eligibility and room rent applicability.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {/* Diagnosis / Treatment */}
          <div className="md:col-span-2 space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Clinical Diagnosis / Procedure Name *
            </label>
            <input
              type="text"
              value={diagnosis}
              onChange={(e) => setDiagnosis(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-white text-sm focus:outline-none focus:border-primary transition-colors"
              placeholder="e.g. Laparoscopic Cholecystectomy, Cataract with IOL, Dengue"
            />
            {errors.diagnosis && (
              <p className="text-xs text-rose-400 flex items-center gap-1">
                <AlertCircle className="w-3.5 h-3.5" /> {errors.diagnosis}
              </p>
            )}
          </div>

          {/* Patient Age */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Patient Age (Years) *
            </label>
            <input
              type="number"
              min="0"
              max="120"
              value={patientAge}
              onChange={(e) => setPatientAge(parseInt(e.target.value, 10) || 0)}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-white text-sm focus:outline-none focus:border-primary transition-colors"
            />
            {errors.age && (
              <p className="text-xs text-rose-400 flex items-center gap-1">
                <AlertCircle className="w-3.5 h-3.5" /> {errors.age}
              </p>
            )}
          </div>

          {/* Policy Continuity */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
              <span>Active Policy Continuity</span>
              <span className="text-[11px] text-slate-400">{continuityMonths} Months</span>
            </label>
            <input
              type="number"
              min="0"
              max="360"
              value={continuityMonths}
              onChange={(e) => setContinuityMonths(parseInt(e.target.value, 10) || 0)}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-white text-sm focus:outline-none focus:border-primary transition-colors"
            />
            <p className="text-[11px] text-slate-500">
              Months of continuous coverage; triggers PED waiver if continuity exceeds 24-48 months.
            </p>
          </div>

          {/* Hospital Type */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Hospital Admission Channel
            </label>
            <select
              value={hospitalType}
              onChange={(e) => setHospitalType(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-white text-sm focus:outline-none focus:border-primary transition-colors"
            >
              <option value="NETWORK_CASHLESS">Network Hospital (Cashless Pre-Auth)</option>
              <option value="NON_NETWORK_REIMBURSEMENT">Non-Network Hospital (Reimbursement Claim)</option>
            </select>
          </div>

          {/* Hospital Room Category Opted */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Hospital Room Category Opted *
            </label>
            <select
              value={roomCategory}
              onChange={(e) => setRoomCategory(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-white text-sm focus:outline-none focus:border-primary transition-colors"
            >
              {ROOM_CATEGORIES.map((cat) => (
                <option key={cat.value} value={cat.value}>
                  {cat.label}
                </option>
              ))}
            </select>
            <p className="text-[11px] text-slate-500">
              Exceeding the policy room rent limit triggers proportionate deductions on doctor/surgeon fees.
            </p>
          </div>
        </div>

        {/* Sum Insured and Prior Claims */}
        <div className="pt-2 border-t border-white/5 grid grid-cols-1 md:grid-cols-2 gap-5">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
              <span>Selected Base Sum Insured (SI) *</span>
              <span className="text-xs font-mono font-bold text-accent">
                ₹{sumInsured.toLocaleString("en-IN")}
              </span>
            </label>
            {/* Quick Presets */}
            <div className="flex flex-wrap gap-2 mb-2">
              {SUM_INSURED_PRESETS.map((amt) => (
                <button
                  type="button"
                  key={amt}
                  onClick={() => setSumInsured(amt)}
                  className={`px-2.5 py-1 rounded-lg text-xs font-mono transition-colors ${
                    sumInsured === amt
                      ? "bg-primary text-white font-bold"
                      : "bg-white/5 text-slate-300 hover:bg-white/10"
                  }`}
                >
                  ₹{(amt / 100000).toFixed(0)}L
                </button>
              ))}
            </div>
            <input
              type="number"
              min="100000"
              step="50000"
              value={sumInsured}
              onChange={(e) => setSumInsured(Math.max(0, parseInt(e.target.value, 10) || 0))}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-white text-sm font-mono focus:outline-none focus:border-primary transition-colors"
            />
            {errors.sumInsured && (
              <p className="text-xs text-rose-400 flex items-center gap-1">
                <AlertCircle className="w-3.5 h-3.5" /> {errors.sumInsured}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
              <span>Prior Claims in Current Policy Year</span>
              <span className="text-xs font-mono text-slate-400">
                ₹{priorClaims.toLocaleString("en-IN")}
              </span>
            </label>
            <input
              type="number"
              min="0"
              step="10000"
              value={priorClaims}
              onChange={(e) => setPriorClaims(Math.max(0, parseInt(e.target.value, 10) || 0))}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-white text-sm font-mono focus:outline-none focus:border-primary transition-colors"
              placeholder="0.00"
            />
            <p className="text-[11px] text-slate-500">
              Reduces available base sum insured; evaluates restoration availability if SI is exhausted.
            </p>
          </div>
        </div>

        {/* Contractual Flags */}
        <div className="pt-2 border-t border-white/5 grid grid-cols-1 md:grid-cols-3 gap-4">
          <label className="flex items-start gap-3 p-3 rounded-xl bg-slate-950/40 border border-white/5 hover:border-white/10 cursor-pointer transition-colors">
            <input
              type="checkbox"
              checked={accidentalInjury}
              onChange={(e) => setAccidentalInjury(e.target.checked)}
              className="mt-1 rounded text-primary focus:ring-primary border-slate-700 bg-slate-900"
            />
            <div className="text-xs">
              <span className="font-semibold text-white block">Accidental Injury (Emergency)</span>
              <span className="text-slate-400 text-[11px]">
                Waives standard 30-day initial waiting period as per IRDAI master guidelines.
              </span>
            </div>
          </label>

          <label className="flex items-start gap-3 p-3 rounded-xl bg-slate-950/40 border border-white/5 hover:border-white/10 cursor-pointer transition-colors">
            <input
              type="checkbox"
              checked={preExistingCondition}
              onChange={(e) => setPreExistingCondition(e.target.checked)}
              className="mt-1 rounded text-primary focus:ring-primary border-slate-700 bg-slate-900"
            />
            <div className="text-xs">
              <span className="font-semibold text-white block">Pre-Existing Condition (PED)</span>
              <span className="text-slate-400 text-[11px]">
                Invokes the policy PED clause (e.g. 24 or 36 months waiting period).
              </span>
            </div>
          </label>

          <label className="flex items-start gap-3 p-3 rounded-xl bg-slate-950/40 border border-white/5 hover:border-white/10 cursor-pointer transition-colors">
            <input
              type="checkbox"
              checked={subsequentClaimForSameIllness}
              onChange={(e) => setSubsequentClaimForSameIllness(e.target.checked)}
              className="mt-1 rounded text-primary focus:ring-primary border-slate-700 bg-slate-900"
            />
            <div className="text-xs">
              <span className="font-semibold text-white block">Subsequent Claim for Same Illness</span>
              <span className="text-slate-400 text-[11px]">
                Restricts restoration benefit eligibility in policies that exclude identical ailments.
              </span>
            </div>
          </label>
        </div>
      </div>

      {/* 4. Itemized Hospital Bill Line Items */}
      <div className="glass rounded-2xl p-6 border border-white/10 space-y-6">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-white/10 pb-4">
          <div>
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              <FileText className="w-5 h-5 text-primary-light" />
              3. Itemized Hospital Bill Breakdown
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Add itemized invoice items. The engine inspects each category against room limits, non-medical exclusions, and sublimits.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <div className="px-3.5 py-1.5 rounded-xl bg-slate-950 border border-white/10 text-right">
              <span className="text-[10px] text-slate-400 block uppercase tracking-wider">Submitted Total</span>
              <span className="text-base font-bold font-mono text-white">
                ₹{totalSubmittedBill.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </span>
            </div>

            <button
              type="button"
              onClick={handleAddBillLine}
              className="px-3.5 py-2 rounded-xl bg-primary hover:bg-primary-dark text-white text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-lg shadow-primary/20"
            >
              <Plus className="w-4 h-4" />
              <span>Add Line</span>
            </button>
          </div>
        </div>

        {errors.bill && (
          <p className="text-xs text-rose-400 flex items-center gap-1">
            <AlertCircle className="w-3.5 h-3.5" /> {errors.bill}
          </p>
        )}

        {/* Bill Lines List */}
        <div className="space-y-3">
          {billLines.map((item, index) => (
            <div
              key={item.lineId || index}
              className="p-4 rounded-xl bg-slate-950/60 border border-white/10 hover:border-white/20 transition-all space-y-3"
            >
              <div className="grid grid-cols-1 md:grid-cols-12 gap-3 items-center">
                {/* Category */}
                <div className="md:col-span-3 space-y-1">
                  <label className="text-[10px] font-semibold uppercase text-slate-400">
                    Category
                  </label>
                  <select
                    value={item.category}
                    onChange={(e) => handleBillLineChange(index, "category", e.target.value)}
                    className="w-full px-2.5 py-2 rounded-lg bg-slate-900 border border-white/15 text-xs text-white focus:outline-none focus:border-primary"
                  >
                    {BILL_CATEGORIES.map((cat) => (
                      <option key={cat.value} value={cat.value}>
                        {cat.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Description */}
                <div className="md:col-span-4 space-y-1">
                  <label className="text-[10px] font-semibold uppercase text-slate-400">
                    Line Item Description
                  </label>
                  <input
                    type="text"
                    value={item.description}
                    onChange={(e) => handleBillLineChange(index, "description", e.target.value)}
                    className="w-full px-2.5 py-2 rounded-lg bg-slate-900 border border-white/15 text-xs text-white focus:outline-none focus:border-primary"
                    placeholder="e.g. Surgeon Charges, PPE Kit, Deluxe Room"
                  />
                  {errors[`line_${index}_desc`] && (
                    <p className="text-[10px] text-rose-400">
                      {errors[`line_${index}_desc`]}
                    </p>
                  )}
                </div>

                {/* Unit Price */}
                <div className="md:col-span-2 space-y-1">
                  <label className="text-[10px] font-semibold uppercase text-slate-400">
                    Unit Price (₹)
                  </label>
                  <input
                    type="number"
                    min="0"
                    step="100"
                    value={item.unitPrice}
                    onChange={(e) => handleBillLineChange(index, "unitPrice", e.target.value)}
                    className="w-full px-2.5 py-2 rounded-lg bg-slate-900 border border-white/15 text-xs font-mono text-white focus:outline-none focus:border-primary"
                  />
                </div>

                {/* Quantity */}
                <div className="md:col-span-1 space-y-1">
                  <label className="text-[10px] font-semibold uppercase text-slate-400">
                    Qty
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="365"
                    value={item.quantity}
                    onChange={(e) => handleBillLineChange(index, "quantity", e.target.value)}
                    className="w-full px-2.5 py-2 rounded-lg bg-slate-900 border border-white/15 text-xs font-mono text-white focus:outline-none focus:border-primary text-center"
                  />
                </div>

                {/* Total & Action Buttons */}
                <div className="md:col-span-2 flex items-center justify-between gap-2 pt-3 md:pt-0">
                  <div className="text-right flex-1">
                    <span className="text-[9px] text-slate-500 block uppercase">Total</span>
                    <span className="text-xs font-mono font-bold text-accent">
                      ₹{item.totalAmount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                    </span>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => handleDuplicateBillLine(index)}
                      className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
                      title="Duplicate Line Item"
                    >
                      <Copy className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => handleRemoveBillLine(index)}
                      disabled={billLines.length <= 1}
                      className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors disabled:opacity-30 disabled:hover:text-slate-400"
                      title="Remove Line Item"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 5. Submission Bar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-4 p-4 rounded-2xl glass border border-white/10">
        <button
          type="button"
          onClick={handleReset}
          className="px-4 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-semibold text-slate-300 flex items-center gap-1.5 transition-colors"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          <span>Reset to Defaults</span>
        </button>

        <div className="flex items-center gap-4 w-full sm:w-auto">
          <div className="text-right hidden sm:block">
            <span className="text-[10px] text-slate-400 block">Total Hospital Bill</span>
            <span className="text-sm font-mono font-bold text-white">
              ₹{totalSubmittedBill.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
            </span>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full sm:w-auto px-6 py-3 rounded-xl bg-gradient-to-r from-primary to-accent hover:opacity-90 disabled:opacity-50 text-white text-sm font-bold flex items-center justify-center gap-2 shadow-xl shadow-primary/25 transition-all"
          >
            {isLoading ? (
              <>
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                <span>Simulating Contractual Calculation...</span>
              </>
            ) : (
              <>
                <Calculator className="w-4 h-4" />
                <span>Simulate Claim Payout</span>
              </>
            )}
          </button>
        </div>
      </div>
    </form>
  );
}
