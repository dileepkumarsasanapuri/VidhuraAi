/**
 * PolicyAnalyzer Service — Comprehensive insurance policy analysis engine.
 * Integrates with Hugging Face Free Inference API to extract structured JSON
 * from uploaded PDF policies. Falls back to deterministic DB if API fails.
 */
const pdfParse = require("pdf-parse");
const { HfInference } = require("@huggingface/inference");

const hf = new HfInference(process.env.HF_TOKEN); // Optional, but recommended to avoid rate limits

const POLICY_DATABASE = {
  health: {
    "star health": {
      policyName: "Star Health Comprehensive Insurance",
      provider: "Star Health & Allied Insurance Co. Ltd",
      type: "Health Insurance — Family Floater",
      premium: "₹18,500/yr",
      coverage: "₹10L",
      expiryDate: "2027-03-15",
      clauses: [
        "Sum insured: ₹10,00,000 (Family Floater) with automatic rebalancing",
        "Room rent: Single private AC room capped at ₹5,000/day or 1% of sum insured",
        "Pre & post hospitalization: 60 days before / 90 days after discharge",
        "Day care procedures: 541 listed procedures covered including dialysis, chemotherapy",
        "Ambulance charges: Up to ₹2,500 per hospitalization (road ambulance only)",
        "Organ donor expenses: Covered up to ₹1,00,000",
        "AYUSH treatment: Covered up to sum insured in network hospitals",
        "Annual health check-up: Free after claim-free year",
        "Automatic restoration: 100% sum insured restored once per policy year",
        "Domiciliary hospitalization: Covered after 3 days of treatment at home",
      ],
      exclusions: [
        "Pre-existing diseases — 36-month continuous coverage waiting period",
        "Cosmetic or plastic surgery unless medically necessary post-accident",
        "Self-inflicted injuries and suicide attempts",
        "Dental treatment unless requiring hospitalization (>24 hours)",
        "Infertility treatment, assisted reproduction (IVF/IUI), surrogacy",
        "War, nuclear contamination, military service-related injuries",
        "Hazardous activities (bungee jumping, sky diving, racing)",
        "Obesity treatment including bariatric surgery unless BMI > 40",
        "Experimental or unproven treatments",
        "Congenital diseases unless manifested after 18 years of age",
        "Sexually transmitted diseases (first 2 years)",
        "Non-allopathic medicines beyond AYUSH framework",
      ],
      waitingPeriods: [
        { condition: "Initial waiting period", duration: "30 days", note: "No coverage except accidental injuries. Policy must be active for 30 days before any claim." },
        { condition: "Specific diseases", duration: "2 years", note: "Hernia, cataract, joint replacement, benign prostatic hypertrophy, sinusitis, kidney/gallbladder stones, piles, fistula." },
        { condition: "Pre-existing diseases", duration: "3 years (36 months)", note: "Diabetes, hypertension, thyroid disorders, asthma, cardiac conditions. Must declare at policy inception." },
        { condition: "Maternity benefits", duration: "9 months", note: "Only if maternity rider is opted. Normal delivery ₹25,000, C-section ₹50,000 sub-limit." },
      ],
      riders: [
        { name: "Critical Illness Rider", details: "Covers 30 critical conditions including cancer, stroke, organ failure. Lump sum payout.", additionalPremium: "₹2,400/year" },
        { name: "Personal Accident Rider", details: "₹10 lakh accidental death + disability cover", additionalPremium: "₹800/year" },
        { name: "Hospital Daily Cash", details: "₹2,000/day during hospitalization (max 30 days)", additionalPremium: "₹1,200/year" },
        { name: "Maternity Benefit", details: "Normal delivery ₹25,000, C-section ₹50,000 after 9-month wait", additionalPremium: "₹3,500/year" },
      ],
      hiddenConditions: [
        "⚠️ Room rent cap of ₹5,000/day triggers PROPORTIONAL DEDUCTION on ALL charges — surgeon fees, anesthesia, ICU, consumables all get reduced proportionally if room exceeds limit",
        "⚠️ Co-payment of 20% mandatory for all members aged 60+ years, applied after deductible",
        "⚠️ Sub-limits on specific treatments: Cataract surgery ₹40,000, knee replacement ₹2,50,000 per knee",
        "⚠️ Network hospital MANDATORY for cashless — non-network hospitals get only 80% reimbursement after verification",
        "⚠️ No-claim bonus accumulates at 10%/year up to 50% — filing ANY claim resets entire bonus to 0%",
        "⚠️ Disease-specific sub-limits not clearly mentioned in policy summary — buried in clause 4.2.7",
        "⚠️ 'Reasonable and customary charges' clause allows insurer to reject charges they deem excessive",
      ],
      simplifiedSummary: "This is a family health insurance policy covering hospitalization, day-care procedures, and pre/post hospital expenses up to ₹10 lakhs. It has a room rent cap that can significantly reduce your actual payout. Members above 60 pay 20% co-payment. Pre-existing diseases have a 3-year waiting period. The policy includes automatic restoration of sum insured once per year.",
      scores: { transparency: 68, risk: 42, fairness: 71, claimReliability: 65, overall: 72 },
    },
    "care supreme": {
      policyName: "Care Supreme Health Insurance",
      provider: "Care Health Insurance (formerly Religare)",
      type: "Health Insurance — Individual/Family Floater",
      premium: "₹14,200/yr",
      coverage: "₹15L",
      expiryDate: "2027-06-20",
      clauses: [
        "Sum insured: ₹10,00,000 to ₹6,00,00,000 (no capping)",
        "No room rent capping — any room type covered",
        "No co-payment at any age — full coverage regardless of member age",
        "Unlimited automatic restoration of sum insured",
        "Pre & post hospitalization: 60 days / 180 days",
        "Day care procedures: 588 procedures covered",
        "Ambulance: Up to ₹5,000 per hospitalization",
        "AYUSH treatment: Fully covered",
        "Domiciliary treatment: Covered",
        "Second opinion from specialist: Covered",
      ],
      exclusions: [
        "Pre-existing diseases — 24-month waiting period",
        "Cosmetic surgery unless post-accident",
        "Self-inflicted injuries",
        "Dental unless hospitalization required",
        "Infertility/IVF treatment",
        "War and nuclear risks",
        "Experimental treatments",
        "Obesity surgery unless BMI > 40",
      ],
      waitingPeriods: [
        { condition: "Initial waiting", duration: "30 days", note: "Standard initial waiting period" },
        { condition: "Specific diseases", duration: "2 years", note: "Similar list to industry standard" },
        { condition: "Pre-existing diseases", duration: "2 years (24 months)", note: "Shorter than industry standard of 3-4 years" },
      ],
      riders: [
        { name: "Air Ambulance Cover", details: "Up to ₹5 lakh for air ambulance emergencies", additionalPremium: "₹1,800/year" },
        { name: "Global Coverage", details: "Worldwide emergency hospitalization", additionalPremium: "₹4,500/year" },
      ],
      hiddenConditions: [
        "⚠️ Premium increases can be steep — 15-25% yearly hike common after age 45",
        "⚠️ Smaller hospital network (6,000+) compared to Star Health (14,000+)",
        "⚠️ Claim settlement ratio varies by branch — metro branches faster",
        "⚠️ Policy portability rules may reset some benefits if switching from competitor",
      ],
      simplifiedSummary: "This is one of the most customer-friendly health policies in India. No room rent cap means you won't face proportional deductions. No co-payment at any age is a huge advantage for families with elderly members. The 2-year PED waiting period is shorter than most competitors. Main downside is a smaller hospital network.",
      scores: { transparency: 78, risk: 35, fairness: 82, claimReliability: 74, overall: 81 },
    },
  },
  motor: {
    default: {
      policyName: "Comprehensive Motor Insurance",
      provider: "General Insurance Provider",
      type: "Motor Insurance — Comprehensive",
      premium: "₹8,200/yr",
      coverage: "₹15L",
      expiryDate: "2027-01-20",
      clauses: [
        "Own damage cover: Full IDV (Insured Declared Value)",
        "Third-party liability: Unlimited for death/injury, ₹7.5L for property",
        "Personal accident cover: ₹15 lakh for owner-driver",
        "NCB protection: Up to 50% no-claim bonus",
        "Roadside assistance: 24/7 towing and on-road repair",
        "Consumables cover: Engine oil, nuts, bolts covered",
      ],
      exclusions: [
        "Normal wear and tear",
        "Mechanical/electrical breakdown",
        "Driving without valid license",
        "Driving under influence of alcohol/drugs",
        "Use for racing or speed testing",
        "Consequential loss",
        "War and nuclear risks",
        "Depreciation on parts (unless zero-dep add-on)",
      ],
      waitingPeriods: [],
      riders: [
        { name: "Zero Depreciation", details: "Full claim without depreciation deduction on parts", additionalPremium: "₹1,200/year" },
        { name: "Engine Protection", details: "Covers engine damage due to waterlogging", additionalPremium: "₹800/year" },
        { name: "Return to Invoice", details: "Get full invoice value in case of total loss", additionalPremium: "₹600/year" },
      ],
      hiddenConditions: [
        "⚠️ Depreciation reduces claim payout significantly — rubber/plastic parts 50%, metal 30% deduction",
        "⚠️ IDV decreases each year — your coverage drops even if car value doesn't",
        "⚠️ Cashless available only at network garages — reimbursement takes 15-30 days elsewhere",
      ],
      simplifiedSummary: "Comprehensive motor insurance covering own damage and third-party liability. Zero depreciation add-on is essential to avoid significant deductions on claims. NCB up to 50% saves premium — but filing any claim resets it.",
      scores: { transparency: 75, risk: 30, fairness: 78, claimReliability: 80, overall: 78 },
    },
  },
  life: {
    default: {
      policyName: "Term Life Insurance",
      provider: "Life Insurance Provider",
      type: "Term Life Insurance — Pure Protection",
      premium: "₹12,000/yr",
      coverage: "₹1Cr",
      expiryDate: "2026-12-31",
      clauses: [
        "Death benefit: ₹1 crore lump sum to nominee",
        "Accidental death: Additional ₹50 lakh benefit",
        "Terminal illness: Advance payment of 50% sum assured",
        "Premium waiver: On critical illness diagnosis",
        "Tax benefit: Under Section 80C (premium) and 10(10D) (payout)",
        "Coverage tenure: Up to age 75",
      ],
      exclusions: [
        "Suicide within first year of policy",
        "Death due to participation in criminal activity",
        "Death due to hazardous activities (unless declared)",
        "Death due to drug/alcohol abuse",
        "War and nuclear risks",
        "Pre-existing conditions not disclosed (fraud)",
      ],
      waitingPeriods: [
        { condition: "Suicide clause", duration: "1 year", note: "No death benefit if suicide within first policy year" },
      ],
      riders: [
        { name: "Critical Illness", details: "Lump sum on diagnosis of 34 critical conditions", additionalPremium: "₹2,500/year" },
        { name: "Accidental Death", details: "Additional ₹50 lakh on accidental death", additionalPremium: "₹500/year" },
        { name: "Premium Waiver", details: "Future premiums waived on disability", additionalPremium: "₹300/year" },
      ],
      hiddenConditions: [
        "⚠️ Non-disclosure of any health condition can void the entire policy — be thorough in declaration",
        "⚠️ Premium increases significantly at renewal if you develop health conditions",
        "⚠️ Claim can be contested within first 3 years — insurer can investigate and reject",
      ],
      simplifiedSummary: "Pure protection term insurance providing death benefit to your family. No maturity value — you only get coverage, not savings. This is the most cost-effective way to protect your family financially. Be extremely thorough in health declarations to avoid claim rejection.",
      scores: { transparency: 80, risk: 25, fairness: 85, claimReliability: 70, overall: 78 },
    },
  },
};

function detectPolicyType(fileName) {
  const lower = (fileName || "").toLowerCase();
  if (/\b(health|star|care|medical|hospital|ihip)\b/.test(lower)) return "health";
  if (/\b(motor|car|vehicle|auto)\b/.test(lower)) return "motor";
  if (/\b(life|term|icici|lic)\b/.test(lower)) return "life";
  return "health"; // default
}

function detectProvider(fileName) {
  const lower = (fileName || "").toLowerCase();
  if (/\bstar\b/.test(lower)) return "star health";
  if (/\b(care|religare)\b/.test(lower)) return "care supreme";
  return null;
}

async function analyzePolicy(fileName, fileBuffer) {
  // If we have a file buffer and it's a PDF, try extracting text and querying HF
  if (fileBuffer && (fileName || "").toLowerCase().endsWith(".pdf")) {
    try {
      console.log(`[AI Analyzer] Parsing PDF: ${fileName}`);
      const pdfData = await pdfParse(fileBuffer);
      let text = pdfData.text || "";

      // Limit to 20000 chars to fit safely within free inference API context limits while capturing more content
      if (text.length > 20000) {
        text = text.substring(0, 20000);
      }

      console.log(`[AI Analyzer] Querying Hugging Face Mistral model for: ${fileName}`);
      const prompt = `You are an expert insurance analyst. Read the following policy excerpt and extract the details in valid JSON format ONLY. Do not write anything outside the JSON.
      
Schema:
{
  "policyName": "string",
  "provider": "string",
  "type": "string",
  "premium": "string",
  "coverage": "string",
  "expiryDate": "string",
  "clauses": ["string"],
  "exclusions": ["string"],
  "waitingPeriods": [{"condition": "string", "duration": "string", "note": "string"}],
  "riders": [{"name": "string", "details": "string"}],
  "hiddenConditions": ["string"],
  "simplifiedSummary": "string",
  "scores": {"transparency": 70, "risk": 40, "fairness": 75, "claimReliability": 80, "overall": 72}
}

Excerpt:
${text}

JSON Output:
`;

      const response = await hf.chatCompletion({
        model: "Qwen/Qwen2.5-72B-Instruct",
        messages: [
          { role: "system", content: "You are an expert insurance analyst." },
          { role: "user", content: prompt }
        ],
        max_tokens: 4000,
        temperature: 0.1,
      });

      const generatedText = response.choices[0].message.content.trim();
      console.log(`[AI Analyzer] HF Response received. Parsing JSON...`);
      
      try {
        // Try to safely extract JSON from the output, handling potential markdown code blocks
        let cleanText = generatedText.replace(/```json/g, '').replace(/```/g, '').trim();
        const jsonMatch = cleanText.match(/\{[\s\S]*\}/);
        let jsonStr = jsonMatch ? jsonMatch[0] : cleanText;
        
        // Basic attempt to close truncated JSON
        if ((jsonStr.match(/\{/g) || []).length > (jsonStr.match(/\}/g) || []).length) {
            jsonStr += "}";
        }
        
        const parsedData = JSON.parse(jsonStr);
        
        return {
          ...parsedData,
          analyzedAt: new Date().toISOString(),
          sourceFile: fileName,
          confidence: 0.85,
          isAiGenerated: true
        };
      } catch (parseErr) {
        console.error("[AI Analyzer] Failed to parse JSON from AI output.");
        console.log("Raw Output:", generatedText);
        throw new Error("AI successfully generated text but failed to format as JSON. Please try again.");
      }
    } catch (err) {
      console.error("[AI Analyzer] HF API Processing Error:", err.message);
      throw new Error(`Hugging Face API Error: ${err.message}`);
    }
  } else {
      throw new Error("Invalid file uploaded. Please upload a valid PDF.");
  }

  // Deterministic fallback disabled based on user request.
  // We strictly rely on the Hugging Face API now.
}

module.exports = { analyzePolicy, POLICY_DATABASE };
