const express = require("express");
const router = express.Router();
const { simulateScenario } = require("../services/scenarioEngine");
const policiesRouter = require("./policies");

const auth = require("../middleware/auth");

// POST scenario simulation
router.post("/", auth, async (req, res) => {
  const { question, policyId } = req.body;

  if (!question) {
    return res.status(400).json({ error: "Question is required" });
  }

  try {
    const policy = policiesRouter.getPolicyById ? await policiesRouter.getPolicyById(policyId, req.user._id) : null;
    const response = await simulateScenario(question, policy);
    res.json({ success: true, data: response });
  } catch (error) {
    console.error("Scenario simulation error:", error);
    res.status(500).json({ error: "Scenario simulation failed", message: error.message });
  }
});

module.exports = router;
