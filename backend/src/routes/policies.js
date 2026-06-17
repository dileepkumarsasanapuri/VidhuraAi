const express = require("express");
const router = express.Router();
const Policy = require("../models/Policy");
const auth = require("../middleware/auth");

// Apply auth middleware to all policy routes
router.use(auth);

// GET all policies for the logged-in user
router.get("/", async (req, res) => {
  try {
    const policies = await Policy.find({ userId: req.user._id }).sort({ createdAt: -1 });
    res.json({ success: true, data: policies, count: policies.length });
  } catch (error) {
    console.error("Error fetching policies:", error);
    res.status(500).json({ error: "Failed to fetch policies" });
  }
});

// GET single policy
router.get("/:id", async (req, res) => {
  try {
    const policy = await Policy.findOne({ _id: req.params.id, userId: req.user._id });
    if (!policy) return res.status(404).json({ error: "Policy not found" });
    res.json({ success: true, data: policy });
  } catch (error) {
    console.error("Error fetching policy:", error);
    res.status(500).json({ error: "Failed to fetch policy" });
  }
});

// POST create policy
router.post("/", async (req, res) => {
  try {
    const policyData = {
      ...req.body,
      userId: req.user._id,
    };
    
    // Convert generic objects back properly if needed, but Mongoose handles most
    const policy = await Policy.create(policyData);
    res.status(201).json({ success: true, data: policy });
  } catch (error) {
    console.error("Error creating policy:", error);
    res.status(500).json({ error: "Failed to create policy" });
  }
});

// DELETE policy
router.delete("/:id", async (req, res) => {
  try {
    const deleted = await Policy.findOneAndDelete({ _id: req.params.id, userId: req.user._id });
    if (!deleted) return res.status(404).json({ error: "Policy not found" });
    res.json({ success: true, message: "Policy deleted" });
  } catch (error) {
    console.error("Error deleting policy:", error);
    res.status(500).json({ error: "Failed to delete policy" });
  }
});

// Async Helpers for other routes
router.getPolicyById = async (id, userId) => {
  try {
    return await Policy.findOne({ _id: id, userId });
  } catch (e) {
    return null;
  }
};

router.getAllPolicies = async (userId) => {
  try {
    return await Policy.find({ userId }).sort({ createdAt: -1 });
  } catch (e) {
    return [];
  }
};

module.exports = router;
