package com.vidhura.ai.pattern.strategy;

import com.vidhura.ai.dto.ScenarioDto;
import com.vidhura.ai.model.Policy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Concrete strategy utilizing deterministic rules, regex keyword matching,
 * and exact contract parameters (deductibles, co-pays, room rent limits) to simulate scenarios.
 */
@Slf4j
@Component
@Order(2)
public class DeterministicScenarioSimulationStrategy implements ScenarioSimulationStrategy {

    @Override
    public boolean supports(Policy policy) {
        return true; // Can handle any policy or null policy
    }

    @Override
    public ScenarioDto.ScenarioResponse simulate(String question, Policy policy, String language) {
        String targetLang = (language != null && !language.isBlank()) ? language.trim() : "English";

        if (policy == null) {
            log.info("[Strategy: DeterministicScenario] No policy provided, returning generic fallback.");
            return getGenericFallback(question, targetLang);
        }

        log.info("[Strategy: DeterministicScenario] Evaluating scenario via deterministic rule engine for policy: {}", policy.getName());
        return buildContextualEvaluation(question, policy, targetLang);
    }

    private ScenarioDto.ScenarioResponse buildContextualEvaluation(String question, Policy policy, String language) {
        String q = question.toLowerCase();
        Policy.PolicyAnalysis analysis = policy.getAnalysis();

        String category = detectCategory(q);
        int totalCost = extractBillFromQuestion(question, category);

        List<String> matchedExclusions = new ArrayList<>();
        List<String> matchedClauses = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        boolean hasWaitingPeriod = false;

        if (analysis != null) {
            if (analysis.getExclusions() != null) {
                for (String excl : analysis.getExclusions()) {
                    String lower = excl.toLowerCase();
                    if (q.contains("dental") && lower.contains("dental")) matchedExclusions.add(excl);
                    else if (q.contains("cosmetic") && lower.contains("cosmetic")) matchedExclusions.add(excl);
                    else if (q.contains("adventure") && lower.contains("adventure")) matchedExclusions.add(excl);
                    else if (q.contains("self") && lower.contains("self")) matchedExclusions.add(excl);
                    else if (q.contains("ayurved") && lower.contains("ayurved")) matchedExclusions.add(excl);
                    else if (containsAny(lower, category)) matchedExclusions.add(excl);
                }
            }

            if (analysis.getClauses() != null) {
                for (String clause : analysis.getClauses()) {
                    String lower = clause.toLowerCase();
                    if (containsAny(lower, category) || containsAny(lower, q)) {
                        matchedClauses.add(clause);
                    }
                }
            }

            if (analysis.getWaitingPeriods() != null) {
                for (Policy.WaitingPeriod wp : analysis.getWaitingPeriods()) {
                    String wpLower = (wp.getCondition() + " " + wp.getNote()).toLowerCase();
                    if (containsAny(wpLower, q) || containsAny(wpLower, category)) {
                        hasWaitingPeriod = true;
                        conditions.add("⏳ Waiting period: " + wp.getCondition() + " — " + wp.getDuration() + ". " + wp.getNote());
                    }
                }
            }
        }

        boolean isExcluded = !matchedExclusions.isEmpty();
        Object covered;
        String coverageStatus;
        String summary;
        int coveredAmount;
        int outOfPocket;

        double deductibleAmt = (analysis != null && analysis.getDeductibleAmount() != null) ? analysis.getDeductibleAmount() : 0.0;
        double copayPct = (analysis != null && analysis.getCopayPercentage() != null) ? analysis.getCopayPercentage() : 0.0;
        String roomLimit = (analysis != null && analysis.getRoomRentLimit() != null) ? analysis.getRoomRentLimit() : "Single Standard AC Room";

        if (isExcluded) {
            covered = false;
            coverageStatus = getLocalizedStatus(false, language);
            summary = getLocalizedSummary(false, policy.getName(), category, language);
            coveredAmount = 0;
            outOfPocket = totalCost;
        } else if (hasWaitingPeriod) {
            covered = "partial";
            coverageStatus = getLocalizedStatus("partial", language);
            summary = getLocalizedSummary("partial", policy.getName(), category, language);
            coveredAmount = 0;
            outOfPocket = totalCost;
            conditions.add("⏳ Active waiting period condition applies. Full coverage unlocks after continuous tenure.");
        } else {
            covered = true;
            coverageStatus = getLocalizedStatus(true, language);
            summary = getLocalizedSummary(true, policy.getName(), category, language);

            double copayDeduction = totalCost * (copayPct / 100.0);
            coveredAmount = (int) Math.max(0.0, totalCost - (deductibleAmt + copayDeduction));
            outOfPocket = totalCost - coveredAmount;

            if (deductibleAmt > 0) {
                conditions.add("Policy deductible of ₹" + (long) deductibleAmt + " applies");
            } else {
                conditions.add("Zero deductible: 100% Base Sum Insured available under " + policy.getName());
            }

            if (copayPct > 0) {
                conditions.add("Mandatory co-payment of " + (int) copayPct + "% applies");
            } else {
                conditions.add("0% Co-Payment: No cost sharing across member ages");
            }

            conditions.add("Room rent entitlement: " + roomLimit);
        }

        conditions.add("Pre-authorization required at network hospital for planned procedures");
        conditions.add("Claim intimation within 24 hours of emergency admission");

        String recommendation = getLocalizedRecommendation(isExcluded, hasWaitingPeriod, category, language);

        return ScenarioDto.ScenarioResponse.builder()
                .covered(covered)
                .coverageStatus(coverageStatus)
                .summary(summary)
                .estimatedCost(ScenarioDto.CostEstimate.builder()
                        .total((double) totalCost)
                        .covered((double) coveredAmount)
                        .outOfPocket((double) outOfPocket)
                        .build())
                .conditions(conditions)
                .recommendation(recommendation)
                .relevantClauses(matchedClauses.isEmpty() ? List.of("In-Patient Hospitalization Benefit", "Standard Inpatient Medical Coverage") : matchedClauses)
                .build();
    }

    private int extractBillFromQuestion(String question, String category) {
        Matcher m = Pattern.compile("(?i)(?:₹|rs\\.?|inr)?\\s*([\\d,]+(?:\\s*(?:lakhs?|lac|l))?)").matcher(question);
        while (m.find()) {
            String val = m.group(1).replace(",", "").trim().toLowerCase();
            if (val.matches("\\d+\\s*(?:lakhs?|lac|l)")) {
                String numStr = val.replaceAll("[^0-9.]", "").trim();
                try {
                    double l = Double.parseDouble(numStr);
                    if (l > 0) return (int) (l * 100000);
                } catch (Exception ignored) {}
            } else if (val.matches("\\d+")) {
                try {
                    int n = Integer.parseInt(val);
                    if (n >= 1000) return n;
                } catch (Exception ignored) {}
            }
        }
        return getCostEstimate(category);
    }

    private String getLocalizedStatus(Object covered, String language) {
        if ("Hindi".equalsIgnoreCase(language)) {
            if (Boolean.TRUE.equals(covered)) return "✅ कवर्ड — पॉलिसी के तहत मान्य";
            if ("partial".equals(covered)) return "⚠️ आंशिक कवर्ड — वेटिंग पीरियड लागू";
            return "❌ कवर नहीं है — अपवाद लागू";
        }
        if ("Telugu".equalsIgnoreCase(language)) {
            if (Boolean.TRUE.equals(covered)) return "✅ కవర్ అవుతుంది — పాలసీ పరిధిలో ఉంది";
            if ("partial".equals(covered)) return "⚠️ పాక్షిక కవరేజ్ — వెయిటింగ్ పీరియడ్ వర్తిస్తుంది";
            return "❌ కవర్ కాదు — మినహాయింపు";
        }
        if ("Tamil".equalsIgnoreCase(language)) {
            if (Boolean.TRUE.equals(covered)) return "✅ காப்பீடு உண்டு — திட்டத்தின் கீழ் பொருந்தும்";
            if ("partial".equals(covered)) return "⚠️ பகுதி கவரேஜ் — காத்திருப்பு காலம் பொருந்தும்";
            return "❌ காப்பீடு இல்லை — விலக்கு";
        }
        if ("Bengali".equalsIgnoreCase(language)) {
            if (Boolean.TRUE.equals(covered)) return "✅ কভার রয়েছে — পলিসির আওতাভুক্ত";
            if ("partial".equals(covered)) return "⚠️ আংশিক কভার — অপেক্ষার সময়সীমা প্রযোজ্য";
            return "❌ কভার নেই — পলিসি বর্জন প্রযোজ্য";
        }
        if (Boolean.TRUE.equals(covered)) return "✅ Covered — Standard Coverage Applies";
        if ("partial".equals(covered)) return "⚠️ Partially Covered — Waiting Period May Apply";
        return "❌ Not Covered — Exclusion Applies";
    }

    private String getLocalizedSummary(Object covered, String policyName, String category, String language) {
        if ("Hindi".equalsIgnoreCase(language)) {
            if (Boolean.TRUE.equals(covered)) {
                return "आपकी पॉलिसी (" + policyName + ") के तहत " + category + " का इलाज पूरी तरह से कवर्ड है। नेटवर्क अस्पताल में कैशलेस क्लेम उपलब्ध है।";
            } else if ("partial".equals(covered)) {
                return "आपकी पॉलिसी (" + policyName + ") में " + category + " कवर्ड है, लेकिन विशिष्ट वेटिंग पीरियड पूरा होना आवश्यक है।";
            } else {
                return "आपकी पॉलिसी (" + policyName + ") में " + category + " अपवाद सूची में आता है। इसका खर्च आपको स्वयं वहन करना होगा।";
            }
        }
        if ("Telugu".equalsIgnoreCase(language)) {
            if (Boolean.TRUE.equals(covered)) {
                return "మీ పాలసీ (" + policyName + ") ప్రకారం " + category + " చికిత్స కవర్ అవుతుంది. నెట్‌వర్క్ ఆసుపత్రులలో క్యాష్‌లెస్ ప్రయోజనం పొందవచ్చు.";
            } else if ("partial".equals(covered)) {
                return "మీ పాలసీ (" + policyName + ") లో " + category + " కవర్ అవుతుంది, కానీ నిబంధిత వెయిటింగ్ పీరియడ్ పూర్తి కావాలి.";
            } else {
                return "మీ పాలసీ (" + policyName + ") ప్రకారం " + category + " మినహాయింపు జాబితాలో ఉంది. దీని పూర్తి ఖర్చు మీరే భరించాల్సి ఉంటుంది.";
            }
        }
        if ("Tamil".equalsIgnoreCase(language)) {
            if (Boolean.TRUE.equals(covered)) {
                return "உங்கள் பாலிசியின்படி (" + policyName + ") " + category + " சிகிச்சை முழுமையாக காப்பீடு செய்யப்படுகிறது. நெட்வொர்க் மருத்துவமனைகளில் ரொக்கமில்லா கிளைம் பெறலாம்.";
            } else if ("partial".equals(covered)) {
                return "உங்கள் பாலிசியில் (" + policyName + ") " + category + " காப்பீடு உண்டு, ஆனால் காத்திருப்பு காலம் பூர்த்தியாக வேண்டும்.";
            } else {
                return "உங்கள் பாலிசி (" + policyName + ") விலக்கு பட்டியலில் " + category + " உள்ளது. இதற்கு ஆகும் செலவை நீங்களே ஏற்க வேண்டும்.";
            }
        }
        if ("Bengali".equalsIgnoreCase(language)) {
            if (Boolean.TRUE.equals(covered)) {
                return "আপনার পলিসি (" + policyName + ") অনুযায়ী " + category + " চিকিৎসার জন্য সম্পূর্ণ কভারেজ উপলব্ধ রয়েছে। নেটওয়ার্ক হাসপাতালে ক্যাশলেস সুবিধা মিলবে।";
            } else if ("partial".equals(covered)) {
                return "আপনার পলিসিতে (" + policyName + ") " + category + " কভার করা আছে, তবে নির্দিষ্ট অপেক্ষার সময়সীমা প্রযোজ্য হতে পারে।";
            } else {
                return "আপনার পলিসি (" + policyName + ") বর্জন তালিকার আওতায় " + category + " পড়ে। এই খরচ আপনাকে নিজেকেই বহন করতে হবে।";
            }
        }
        if (Boolean.TRUE.equals(covered)) {
            return "Based on your policy (" + policyName + "), " + category + " is covered under standard in-patient hospitalization. Standard deductibles apply.";
        } else if ("partial".equals(covered)) {
            return "Your policy (" + policyName + ") covers " + category + " in principle, but a waiting period condition may apply. Verify your policy age.";
        } else {
            return "Based on your policy (" + policyName + "), this " + category + " scenario falls under a specific contract exclusion. You would need to bear the out-of-pocket cost.";
        }
    }

    private String getLocalizedRecommendation(boolean isExcluded, boolean hasWaitingPeriod, String category, String language) {
        if ("Hindi".equalsIgnoreCase(language)) {
            if (isExcluded) return "इस श्रेणी (" + category + ") के लिए अतिरिक्त राइडर लेने पर विचार करें।";
            if (hasWaitingPeriod) return "सुनिश्चित करें कि वेटिंग पीरियड पूरा हो चुका है और भर्ती से 48 घंटे पहले सूचित करें।";
            return "कैशलेस क्लेम के लिए हमेशा नेटवर्क अस्पताल चुनें और डिस्चार्ज समरी सुरक्षित रखें।";
        }
        if ("Telugu".equalsIgnoreCase(language)) {
            if (isExcluded) return "ఈ చికిత్స (" + category + ") కోసం ప్రత్యేక యాడ్-ఆన్ రైడర్ తీసుకోవడం మంచిది.";
            if (hasWaitingPeriod) return "వెయిటింగ్ పీరియడ్ గడిచిందో లేదో సరిచూసుకోండి మరియు ఆసుపత్రి బిల్లులు భద్రపరచండి.";
            return "పూర్తి క్యాష్‌లెస్ కోసం నెట్‌వర్క్ ఆసుపత్రిని ఎంచుకోండి.";
        }
        if ("Tamil".equalsIgnoreCase(language)) {
            if (isExcluded) return "இந்த சிகிச்சைக்காக (" + category + ") கூடுதல் ரைடர் காப்பீடு எடுக்க பரிசீலிக்கவும்.";
            if (hasWaitingPeriod) return "காத்திருப்பு காலம் முடிந்துவிட்டதா என்பதை சரிபார்த்து ஆவணங்களை தயாராக வைக்கவும்.";
            return "முழு ரொக்கமில்லா அனுமதிக்காக நெட்வொர்க் மருத்துவமனையைத் தேர்ந்தெடுக்கவும்.";
        }
        if ("Bengali".equalsIgnoreCase(language)) {
            if (isExcluded) return "এই চিকিৎসার (" + category + ") জন্য অতিরিক্ত রাইডার নেওয়ার কথা বিবেচনা করুন।";
            if (hasWaitingPeriod) return "অপেক্ষার সময়কাল অতিক্রান্ত হয়েছে কিনা তা নিশ্চিত করুন এবং রেকর্ড প্রস্তুত রাখুন।";
            return "ক্যাশলেস অনুমোদনের জন্য নেটওয়ার্ক হাসপাতাল নির্বাচন করুন।";
        }
        if (isExcluded) {
            return "Consider purchasing an add-on rider that covers " + category + " scenarios, or look for a policy with fewer exclusions.";
        } else if (hasWaitingPeriod) {
            return "Check if your waiting period has elapsed. Keep all medical records ready and pre-authorize with the TPA.";
        } else {
            return "Ensure you use an empanelled network hospital for 100% cashless approval. Keep all original bills, prescriptions, and discharge summary ready.";
        }
    }

    private String detectCategory(String question) {
        if (containsAny(question, "dengue fever malaria typhoid infection viral pneumonia")) return "hospitalization";
        if (containsAny(question, "bypass heart cardiac angioplasty stent myocardial attack valve")) return "cardiac surgery";
        if (containsAny(question, "dialysis kidney renal transplant nephrology creatinine")) return "kidney dialysis";
        if (containsAny(question, "cancer tumor chemotherapy radiation oncology leukemia lymphoma")) return "cancer treatment";
        if (containsAny(question, "surgery knee hip joint replacement appendix hernia gall gallbladder stone")) return "surgery";
        if (containsAny(question, "accident car bike road injury fracture trauma collision")) return "accident";
        if (containsAny(question, "maternity pregnancy childbirth delivery caesarean c-section newborn")) return "maternity";
        if (containsAny(question, "ayurveda ayush homeopathy unani siddha naturopathy")) return "ayush treatment";
        if (containsAny(question, "mental psychiatric depression anxiety bipolar therapy")) return "mental health";
        if (containsAny(question, "robotic cyberknife stem cell immunotherapy modern advanced")) return "robotic surgery";
        if (containsAny(question, "diabetes sugar hypertension bp stroke chronic insulin")) return "chronic illness";
        if (containsAny(question, "eye cataract lasik vision glaucoma cornea")) return "eye surgery";
        if (containsAny(question, "dental tooth teeth root canal extraction braces")) return "dental";
        if (containsAny(question, "covid corona virus pandemic sars")) return "covid treatment";
        if (containsAny(question, "stolen theft phone laptop baggage loss")) return "theft";
        if (containsAny(question, "travel abroad international foreign flight baggage passport")) return "travel";
        return "general medical";
    }

    private int getCostEstimate(String category) {
        return switch (category) {
            case "hospitalization" -> 95000;
            case "cardiac surgery" -> 450000;
            case "kidney dialysis" -> 350000;
            case "cancer treatment" -> 500000;
            case "surgery" -> 200000;
            case "accident" -> 150000;
            case "maternity" -> 85000;
            case "ayush treatment" -> 45000;
            case "mental health" -> 60000;
            case "robotic surgery" -> 300000;
            case "chronic illness" -> 120000;
            case "covid treatment" -> 250000;
            case "dental" -> 25000;
            case "eye surgery" -> 45000;
            case "theft" -> 60000;
            case "travel" -> 150000;
            default -> 100000;
        };
    }

    private boolean containsAny(String text, String keywords) {
        for (String keyword : keywords.split("\\s+")) {
            if (keyword.length() > 2 && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private ScenarioDto.ScenarioResponse getGenericFallback(String question, String language) {
        String category = detectCategory(question.toLowerCase());
        int cost = extractBillFromQuestion(question, category);

        String status = "📋 " + ("Hindi".equalsIgnoreCase(language) ? "कोई पॉलिसी चयनित नहीं"
                : "Telugu".equalsIgnoreCase(language) ? "ఏ పాలసీ ఎంపిక చేయలేదు"
                : "Tamil".equalsIgnoreCase(language) ? "எந்த பாலிசியும் தேர்ந்தெடுக்கப்படவில்லை"
                : "Bengali".equalsIgnoreCase(language) ? "কোন পলিসি নির্বাচন করা হয়নি"
                : "No Policy Selected");

        String summary;
        if ("Hindi".equalsIgnoreCase(language)) {
            summary = "सटीक और वास्तविक पॉलिसी विश्लेषण के लिए कृपया ऊपर दिए गए ड्रॉपडाउन से अपनी अपलोड की गई पॉलिसी चुनें।";
        } else if ("Telugu".equalsIgnoreCase(language)) {
            summary = "ఖచ్చితమైన పాలసీ కవరేజ్, రూమ్ రెంట్ నిబంధనలు మరియు డిడక్టబుల్స్ పరిశీలించడానికి దయచేసి పైన ఉన్న డ్రాప్‌డౌన్ నుండి మీ పాలసీని ఎంచుకోండి.";
        } else if ("Tamil".equalsIgnoreCase(language)) {
            summary = "சரியான பாலிசி கவரேஜ், அறை வாடகை வரம்புகள் மற்றும் விலக்குகளை அறிய மேலே உள்ள பட்டியலில் இருந்து உங்கள் பாலிசியைத் தேர்ந்தெடுக்கவும்.";
        } else if ("Bengali".equalsIgnoreCase(language)) {
            summary = "সঠিক পলিসি কভারেজ, রুম ভাড়ার সীমা এবং ছাড় দেখতে অনুগ্রহ করে উপরের ড্রপডাউন থেকে আপনার পলিসি নির্বাচন করুন।";
        } else {
            summary = "To see exact extracted coverage, room rent entitlements, 0% co-pay, and deductibles from your contract, please select an uploaded policy in the dropdown above.";
        }

        String rec = "Hindi".equalsIgnoreCase(language)
                ? "सटीक गणना के लिए ऊपर से पॉलिसी चुनें या अपना पॉलिसी दस्तावेज़ अपलोड करें।"
                : "Telugu".equalsIgnoreCase(language)
                ? "ఖచ్చితమైన ఖర్చు గణన కోసం పైన పాలసీని ఎంచుకోండి లేదా కొత్త పాలసీ PDF అప్‌లోడ్ చేయండి."
                : "Tamil".equalsIgnoreCase(language)
                ? "துல்லியமான செலவுக் கணக்கீட்டிற்கு மேலே பாலிசியைத் தேர்ந்தெடுக்கவும் அல்லது புதிய பாலிசி PDF ஐப் பதிவேற்றவும்."
                : "Bengali".equalsIgnoreCase(language)
                ? "সঠিক খরচের হিসেবের জন্য উপরে পলিসি নির্বাচন করুন বা আপনার পলিসি PDF আপলোড করুন।"
                : "Select an uploaded policy above or upload your policy PDF to calculate exact out-of-pocket costs.";

        return ScenarioDto.ScenarioResponse.builder()
                .covered("unknown")
                .coverageStatus(status)
                .summary(summary)
                .estimatedCost(ScenarioDto.CostEstimate.builder()
                        .total((double) cost)
                        .covered(0.0)
                        .outOfPocket(0.0)
                        .build())
                .conditions(List.of(
                        "Please select your uploaded policy document above to evaluate exact coverage",
                        "Room rent limits and co-pays depend on your specific policy terms",
                        "Waiting periods will be evaluated against your policy terms"
                ))
                .recommendation(rec)
                .relevantClauses(List.of())
                .build();
    }
}
