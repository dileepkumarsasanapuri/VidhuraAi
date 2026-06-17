const express = require("express");
const router = express.Router();

// GET analytics data
router.get("/", (req, res) => {
  res.json({
    success: true,
    data: {
      kpis: {
        trustScore: { value: 64, trend: 5 },
        claimPreparedness: { value: 78, trend: 12 },
        hiddenRisks: { value: 7, trend: -2 },
        coverageGap: { value: "₹12L", trend: 0 },
      },
      trustScoreTrend: [
        { month: "Jan", score: 55 }, { month: "Feb", score: 58 }, { month: "Mar", score: 62 },
        { month: "Apr", score: 59 }, { month: "May", score: 64 }, { month: "Jun", score: 71 },
      ],
      policyHealth: {
        transparency: 68,
        riskProtection: 74,
        claimReadiness: 78,
        valueForMoney: 65,
        overall: 71,
      },
      coverageBreakdown: [
        { type: "Health", amount: "₹25L", percentage: 60 },
        { type: "Life", amount: "₹1Cr", percentage: 85 },
        { type: "Motor", amount: "₹15L", percentage: 95 },
        { type: "Travel", amount: "₹50L", percentage: 40 },
      ],
      riskDistribution: [
        { category: "Hidden Exclusions", count: 4, severity: "High" },
        { category: "Sub-limits", count: 2, severity: "Medium" },
        { category: "Waiting Periods", count: 1, severity: "Low" },
      ]
    }
  });
});

module.exports = router;
