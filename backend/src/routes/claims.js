const express = require("express");
const router = express.Router();
const { predictClaimOutcome } = require("../services/claimEngine");
const auth = require("../middleware/auth");

// POST claim prediction
router.post("/predict", auth, (req, res) => {
  const data = req.body;

  if (!data.hospitalBill || data.hospitalBill <= 0) {
    return res.status(400).json({ error: "Valid hospital bill amount is required" });
  }

  try {
    const outcome = predictClaimOutcome(data);
    res.json({ success: true, data: outcome });
  } catch (error) {
    res.status(500).json({ error: "Claim prediction failed", message: error.message });
  }
});

module.exports = router;
