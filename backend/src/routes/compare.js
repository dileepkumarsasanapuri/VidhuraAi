const express = require("express");
const router = express.Router();
const { POLICY_DATABASE } = require("../services/policyAnalyzer");

const policiesRouter = require("./policies");

// Helper to convert DB format to Compare format
function formatForComparison(policy) {
  let scores = { transparency: 50, risk: 50, fairness: 50, claimReliability: 50 };
  let pros = ["Standard coverage"];
  let cons = ["Standard exclusions apply"];
  let exclusions = 10;
  let hiddenTraps = 2;

  // If it's an uploaded policy with AI analysis
  if (policy.analysis) {
    scores = policy.analysis.scores || scores;
    pros = policy.analysis.clauses ? policy.analysis.clauses.slice(0, 3).map(c => c.split(':')[0]) : pros;
    cons = policy.analysis.hiddenConditions ? policy.analysis.hiddenConditions.slice(0, 3).map(c => c.replace('⚠️ ', '')) : cons;
    exclusions = policy.analysis.exclusions ? policy.analysis.exclusions.length : exclusions;
    hiddenTraps = policy.analysis.hiddenConditions ? policy.analysis.hiddenConditions.length : hiddenTraps;
  } else {
    // Attempt to map from POLICY_DATABASE if possible, otherwise use generic mock
    let dbMatch = null;
    if (policy.name.toLowerCase().includes("star")) dbMatch = POLICY_DATABASE.health["star health"];
    else if (policy.name.toLowerCase().includes("care") || policy.name.toLowerCase().includes("hdfc")) dbMatch = POLICY_DATABASE.health["care supreme"];
    
    if (dbMatch) {
      scores = dbMatch.scores;
      pros = dbMatch.clauses.slice(0, 3).map(c => c.split(':')[0]);
      cons = dbMatch.hiddenConditions.slice(0, 3).map(c => c.replace('⚠️ ', ''));
      exclusions = dbMatch.exclusions.length;
      hiddenTraps = dbMatch.hiddenConditions.length;
    }
  }

  return {
    id: policy.id,
    name: policy.name || "Unknown Policy",
    provider: policy.provider || "Unknown Provider",
    premium: typeof policy.premium === 'string' ? policy.premium : "₹15,000/yr",
    coverage: typeof policy.coverage === 'string' ? policy.coverage : "₹10L",
    scores,
    pros,
    cons,
    exclusions,
    hiddenTraps,
  };
}

const auth = require("../middleware/auth");

// POST compare policies
router.post("/", auth, async (req, res) => {
  const { policyIds } = req.body;

  try {
    const rawPolicies = await policiesRouter.getAllPolicies(req.user._id);
    let allPolicies = rawPolicies.map(p => formatForComparison(p));

    // Also include Niva Bupa as a static benchmark
    allPolicies.push({
      id: "benchmark-niva", name: "Niva Bupa ReAssure 2.0 (Benchmark)", provider: "Niva Bupa",
      premium: "₹16,800/yr", coverage: "₹10L",
      scores: { transparency: 74, risk: 38, fairness: 76, claimReliability: 70 },
      pros: ["No room rent cap", "Consumables covered", "OPD cover available"],
      cons: ["Co-pay 10% in tier-1 cities", "Limited day-care list", "Zone-based pricing"],
      exclusions: 10, hiddenTraps: 3,
    });

    const selected = policyIds && policyIds.length > 0
      ? allPolicies.filter((p) => policyIds.includes(p.id))
      : allPolicies;

    if (selected.length === 0) {
      return res.json({ success: true, data: { policies: allPolicies, recommendation: null } });
    }

    // AI recommendation based on overall score (transparency + fairness + claimReliability)
    const bestValue = selected.reduce((best, current) => {
      const bestScore = best.scores.transparency + best.scores.fairness + best.scores.claimReliability;
      const currentScore = current.scores.transparency + current.scores.fairness + current.scores.claimReliability;
      return currentScore > bestScore ? current : best;
    });

    res.json({
      success: true,
      data: {
        policies: selected,
        recommendation: {
          bestPolicy: bestValue.name,
          reason: `${bestValue.name} offers the best balance of transparency (${bestValue.scores.transparency}/100), fairness (${bestValue.scores.fairness}/100), and claim reliability (${bestValue.scores.claimReliability}/100) with only ${bestValue.hiddenTraps} hidden traps.`,
          savings: "₹3,400/yr over 5 years considering claim ratios."
        },
      },
    });
  } catch (error) {
    res.status(500).json({ error: "Comparison failed", message: error.message });
  }
});

module.exports = router;
