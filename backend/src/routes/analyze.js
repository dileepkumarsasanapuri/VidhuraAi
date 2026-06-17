const express = require("express");
const router = express.Router();
const multer = require("multer");
const { analyzePolicy } = require("../services/policyAnalyzer");
const auth = require("../middleware/auth");

// Configure multer to store files in memory
const upload = multer({ 
  storage: multer.memoryStorage(),
  limits: { fileSize: 25 * 1024 * 1024 }, // 25MB max
});

// POST analyze uploaded policy
router.post("/", auth, upload.single("policyFile"), async (req, res) => {
  const file = req.file;

  if (!file) {
    return res.status(400).json({ error: "File is required" });
  }

  try {
    // Pass the file buffer and original name to the analyzer service
    const analysis = await analyzePolicy(file.originalname, file.buffer);
    res.json({ success: true, data: analysis });
  } catch (error) {
    console.error("Analysis route error:", error);
    res.status(500).json({ error: "Analysis failed", message: error.message });
  }
});

module.exports = router;
