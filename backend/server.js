const express = require("express");
const cors = require("cors");
const dotenv = require("dotenv");
const mongoose = require("mongoose");

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5000;

// Middleware
app.use(cors({ origin: process.env.FRONTEND_URL || "http://localhost:3000" }));
app.use(express.json({ limit: "25mb" }));

// Auth + database
app.use("/api/auth", require("./src/routes/auth"));
app.use("/api/health", require("./src/routes/health"));
app.use("/api/policies", require("./src/routes/policies"));
app.use("/api/analyze", require("./src/routes/analyze"));
app.use("/api/scenario", require("./src/routes/scenario"));
app.use("/api/claims", require("./src/routes/claims"));
app.use("/api/compare", require("./src/routes/compare"));
app.use("/api/settings", require("./src/routes/settings"));
app.use("/api/analytics", require("./src/routes/analytics"));

// Global error handler
app.use((err, req, res, next) => {
  console.error("Server error:", err.message);
  res.status(500).json({ error: "Internal server error", message: err.message });
});

async function start() {
  try {
    if (!process.env.MONGODB_URI) {
      throw new Error("MONGODB_URI environment variable is required.");
    }

    await mongoose.connect(process.env.MONGODB_URI, {
      useNewUrlParser: true,
      useUnifiedTopology: true,
    });

    console.log("✅ Connected to MongoDB");
    app.listen(PORT, () => {
      console.log(`🛡️  ClaritySure AI Backend running on port ${PORT}`);
    });
  } catch (error) {
    console.error("Failed to start server:", error.message);
    process.exit(1);
  }
}

start();
