/**
 * ClaimEngine — Predicts claim approval probability, breakdowns, and requirements.
 */

function predictClaimOutcome(data) {
  const { type, disease, hospitalBill, memberAge = 35, isPreExisting = false, hospitalType = 'network', roomType = 'single' } = data;
  
  const bill = parseFloat(hospitalBill) || 0;
  
  // Base probability
  let probability = 85;
  let rejectionReasons = [];
  let tips = [
    "File claim within 15 days of discharge",
    "Keep all original documents and receipts",
    "Ensure discharge summary matches final bill",
  ];

  // Adjustments based on inputs
  if (isPreExisting) {
    probability -= 40;
    rejectionReasons.push("Pre-existing disease waiting period not met (36 months)");
    tips.push("Verify if the 36-month waiting period for this condition has elapsed.");
  }
  
  if (hospitalType === 'non-network') {
    probability -= 10;
    tips.push("Non-network hospitals require upfront payment and reimbursement filing. Process takes 15-30 days.");
  } else {
    tips.unshift("Choose cashless authorization at least 48 hours before planned admission.");
  }

  if (memberAge >= 60) {
    probability -= 5;
    tips.push("Mandatory 20% co-payment applies for senior citizens.");
  }

  const isHighCost = bill > 300000;
  if (isHighCost) {
    probability -= 15;
    rejectionReasons.push("High-value claims undergo strict medical scrutiny");
    tips.push("Ensure a detailed breakdown of all consumables and pharmacy bills is provided.");
  }
  
  if (roomType === 'suite' || roomType === 'deluxe') {
    probability -= 10;
    rejectionReasons.push("Room rent cap exceeded — proportional deduction applies to all charges");
    tips.push("Downgrade to standard single AC room to avoid massive proportional deductions.");
  }

  // Cost breakdown
  let deductible = Math.min(bill * 0.05, 5000); // simplified deductible logic
  let coPay = memberAge >= 60 ? bill * 0.2 : 0;
  let roomRentExcess = 0;
  
  if (roomType === 'suite' || roomType === 'deluxe') {
    // Simulate 30% proportional deduction
    roomRentExcess = bill * 0.3;
  }
  
  let subLimitDeduction = 0;
  const lowerDisease = (disease || "").toLowerCase();
  if (lowerDisease.includes("cataract")) {
    subLimitDeduction = Math.max(0, bill - 40000);
    if (subLimitDeduction > 0) rejectionReasons.push("Cataract surgery capped at ₹40,000 per eye");
  }

  let totalDeductions = deductible + coPay + roomRentExcess + subLimitDeduction;
  let coveredAmount = Math.max(0, bill - totalDeductions);
  
  // Cap probability
  probability = Math.max(15, Math.min(98, probability));
  
  if (probability > 75) {
    rejectionReasons = ["Initial 30-day waiting period (if new policy)", "Non-medical consumables deduction"];
  }

  return {
    approvalProbability: probability,
    hospitalBill: bill,
    coveredAmount,
    outOfPocket: bill - coveredAmount,
    breakdown: {
      deductible,
      coPay,
      roomRentExcess,
      subLimitDeduction,
      networkDiscount: hospitalType === 'network' ? bill * 0.05 : 0 // theoretical discount
    },
    rejectionReasons,
    requiredDocuments: [
      { name: "Duly filled and signed claim form", critical: true },
      { name: "Original hospital bills with detailed breakdown", critical: true },
      { name: "Discharge summary", critical: true },
      { name: "Doctor's prescription and consultation notes", critical: true },
      { name: "Diagnostic and lab reports", critical: true },
      { name: "Pharmacy bills supported by prescriptions", critical: true },
      { name: "Photo ID proof and cancelled cheque", critical: false },
      { name: "Indoor case papers (if demanded by insurer)", critical: false },
    ],
    tips,
    timeline: hospitalType === 'network' ? "Cashless approval within 2-4 hours" : "Reimbursement takes 15-30 days",
    similarClaims: {
      approved: Math.floor(Math.random() * 80) + 10,
      rejected: Math.floor(Math.random() * 20) + 2,
      avgPayout: Math.round(coveredAmount * 0.9)
    }
  };
}

module.exports = { predictClaimOutcome };
