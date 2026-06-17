const mongoose = require("mongoose");

const PolicyAnalysisSchema = new mongoose.Schema({
  policyName: String,
  provider: String,
  type: String,
  premium: String,
  coverage: String,
  expiryDate: String,
  clauses: [String],
  exclusions: [String],
  waitingPeriods: [
    {
      condition: String,
      duration: String,
      note: String,
    }
  ],
  riders: [
    {
      name: String,
      details: String,
      additionalPremium: String,
    }
  ],
  hiddenConditions: [String],
  scores: {
    transparency: Number,
    risk: Number,
    fairness: Number,
    claimReliability: Number,
    overall: Number,
  },
  simplifiedSummary: String,
});

const PolicySchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: "User",
    required: true,
  },
  name: { type: String, required: true },
  provider: { type: String, required: true },
  type: { type: String, default: "Health" },
  premium: String,
  coverage: String,
  expiryDate: String,
  healthScore: { type: Number, default: 0 },
  riskLevel: { type: String, enum: ["high", "medium", "low"], default: "medium" },
  status: { type: String, enum: ["active", "expiring", "expired"], default: "active" },
  analysis: PolicyAnalysisSchema,
}, { timestamps: true });

// Convert _id to id for frontend compatibility
PolicySchema.set("toJSON", {
  virtuals: true,
  versionKey: false,
  transform: function (doc, ret) {
    ret.id = ret._id;
    delete ret._id;
  }
});

PolicySchema.set("toObject", {
  virtuals: true,
  versionKey: false,
});

module.exports = mongoose.model("Policy", PolicySchema);
