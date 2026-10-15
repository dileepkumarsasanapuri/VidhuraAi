"use client";

import React, { useState, useRef, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Send,
  Bot,
  User,
  Sparkles,
  RotateCcw,
  ChevronRight,
  Volume2,
  VolumeX,
  Globe,
  Shield,
  AlertTriangle,
  CheckCircle,
  HelpCircle,
  FileText,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import { api, ScenarioResult } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

interface Message {
  id: string;
  role: "user" | "ai";
  content: string;
  rawResult?: ScenarioResult;
  policyName?: string;
  speechText?: string;
  timestamp: Date;
}

const scenarioSuggestions = [
  { label: "🦟 Dengue Fever", query: "What if I get hospitalized for dengue next month with a bill of ₹95,000?" },
  { label: "🫀 Heart Bypass", query: "Is my father's bypass surgery covered and what is the deductible?" },
  { label: "🦵 Knee Replacement", query: "Will robotic knee replacement surgery be covered or is there a sub-limit?" },
  { label: "🩺 Kidney Dialysis", query: "Does my health policy cover regular kidney dialysis sessions?" },
  { label: "🤰 Maternity Care", query: "Will maternity expenses be covered if I conceive now?" },
  { label: "🤖 Modern Surgery", query: "Are modern robotic and stem cell surgeries payable under this plan?" },
  { label: "🌿 AYUSH Treatment", query: "Can I claim hospital expenses for Ayurvedic or Homeopathic treatment?" },
  { label: "🚗 Road Accident", query: "What if I meet with a car accident tomorrow? Does the 30-day waiting period apply?" },
];

const LANGUAGES = [
  { code: "English", label: "English", speechCode: "en-IN" },
  { code: "Hindi", label: "हिंदी (Hindi)", speechCode: "hi-IN" },
  { code: "Telugu", label: "తెలుగు (Telugu)", speechCode: "te-IN" },
  { code: "Tamil", label: "தமிழ் (Tamil)", speechCode: "ta-IN" },
  { code: "Bengali", label: "বাংলা (Bengali)", speechCode: "bn-IN" },
];

export default function ScenarioPage() {
  const toast = useToast();
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [isTyping, setIsTyping] = useState(false);
  const [availablePolicies, setAvailablePolicies] = useState<any[]>([]);
  const [selectedPolicyId, setSelectedPolicyId] = useState<string>("");
  const [selectedLanguage, setSelectedLanguage] = useState(LANGUAGES[0]);
  const [speakingMessageId, setSpeakingMessageId] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    async function loadPolicies() {
      try {
        const response = await api.getPolicies();
        if (response.data) {
          setAvailablePolicies(response.data);
          if (response.data.length > 0) setSelectedPolicyId(response.data[0].id);
        }
      } catch (error) {
        console.error("Failed to load policies", error);
      }
    }
    loadPolicies();
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // Clean up any ongoing TTS on unmount
  useEffect(() => {
    return () => {
      if (typeof window !== "undefined" && "speechSynthesis" in window) {
        window.speechSynthesis.cancel();
      }
    };
  }, []);

  const handleSpeak = (msgId: string, textToSpeak: string) => {
    if (typeof window === "undefined" || !("speechSynthesis" in window)) {
      toast.warning("Text-to-speech is not supported on this browser.", "Speech Unavailable");
      return;
    }

    if (speakingMessageId === msgId) {
      window.speechSynthesis.cancel();
      setSpeakingMessageId(null);
      return;
    }

    window.speechSynthesis.cancel();
    setSpeakingMessageId(msgId);

    const cleanText = textToSpeak.replace(/[#*`~|]/g, "").trim();
    const utterance = new SpeechSynthesisUtterance(cleanText);
    utterance.lang = selectedLanguage.speechCode;
    utterance.rate = 0.95;

    utterance.onend = () => {
      setSpeakingMessageId(null);
    };

    utterance.onerror = () => {
      setSpeakingMessageId(null);
    };

    window.speechSynthesis.speak(utterance);
  };

  const sendMessage = async (text: string) => {
    if (!text.trim()) return;
    const userMsg: Message = {
      id: Date.now().toString(),
      role: "user",
      content: text.trim(),
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    setIsTyping(true);

    try {
      const response = await api.simulateScenario(text, selectedPolicyId, selectedLanguage.code);
      const pName = availablePolicies.find((p) => p.id === selectedPolicyId)?.name || "Active Health Policy";
      const resultData = response.data;

      const speechScript = `${resultData.coverageStatus || "Scenario Analysis"}. ${resultData.summary}. ${
        resultData.recommendation ? `Recommendation: ${resultData.recommendation}` : ""
      }`;

      const aiMsg: Message = {
        id: (Date.now() + 1).toString(),
        role: "ai",
        content: resultData.summary,
        rawResult: resultData,
        policyName: pName,
        speechText: speechScript,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, aiMsg]);
    } catch (error) {
      console.error(error);
      const errorMsg: Message = {
        id: (Date.now() + 1).toString(),
        role: "ai",
        content: "Sorry, I encountered an error analyzing that scenario. Please verify your selected policy and try again.",
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setIsTyping(false);
    }
  };

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/scenario" />
      <main className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <header className="sticky top-0 z-30 glass border-b border-border px-6 h-16 flex items-center justify-between">
          <div className="ml-12 lg:ml-0 flex items-center gap-3">
            <div>
              <h1 className="text-lg font-bold">ScenarioGPT</h1>
              <p className="text-xs text-muted">Real-life medical scenario simulation with policy audit</p>
            </div>

            <div className="hidden md:block h-6 w-px bg-border mx-1"></div>

            {/* Policy Selector */}
            <select
              value={selectedPolicyId}
              onChange={(e) => setSelectedPolicyId(e.target.value)}
              className="hidden sm:block text-xs bg-background border border-border rounded-xl px-3 py-1.5 outline-none focus:border-primary-light max-w-[200px] truncate"
            >
              {availablePolicies.length > 0 ? (
                availablePolicies.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))
              ) : (
                <option value="sample">HDFC ERGO Optima Secure (Sample Policy)</option>
              )}
            </select>
          </div>

          <div className="flex items-center gap-2">
            {/* Language Selector */}
            <div className="flex items-center gap-1.5 glass rounded-xl px-2.5 py-1 text-xs">
              <Globe className="w-3.5 h-3.5 text-primary-light" />
              <select
                value={selectedLanguage.code}
                onChange={(e) => {
                  const lang = LANGUAGES.find((l) => l.code === e.target.value) || LANGUAGES[0];
                  setSelectedLanguage(lang);
                }}
                className="bg-transparent outline-none cursor-pointer text-xs font-medium"
              >
                {LANGUAGES.map((l) => (
                  <option key={l.code} value={l.code} className="bg-background text-foreground">
                    {l.label}
                  </option>
                ))}
              </select>
            </div>

            <button
              onClick={() => {
                if (typeof window !== "undefined" && "speechSynthesis" in window) {
                  window.speechSynthesis.cancel();
                }
                setSpeakingMessageId(null);
                setMessages([]);
              }}
              className="p-2 glass rounded-xl hover:bg-white/5 transition-colors"
              title="Clear chat session"
            >
              <RotateCcw className="w-4 h-4" />
            </button>
          </div>
        </header>

        {/* Active Policy Extracted Contract Terms Bar */}
        {(() => {
          const activePolicy = availablePolicies.find((p) => p.id === selectedPolicyId);
          if (!activePolicy || !activePolicy.analysis) return null;
          const a = activePolicy.analysis;
          return (
            <div className="bg-emerald-950/20 border-b border-emerald-500/20 px-6 py-2 text-xs flex flex-wrap items-center gap-x-4 gap-y-1">
              <span className="font-semibold text-emerald-400 flex items-center gap-1.5">
                <Shield className="w-3.5 h-3.5" /> {activePolicy.name} Contract:
              </span>
              <span className="text-muted">
                Room Rent: <strong className="text-foreground">{a.roomRentLimit || "Single Standard Private AC Room"}</strong>
              </span>
              <span className="text-muted">
                Co-Pay: <strong className="text-foreground">{a.copay || "0% (Nil)"}</strong>
              </span>
              <span className="text-muted">
                Deductible: <strong className="text-emerald-400">{a.deductible || "₹0 (Nil)"}</strong>
              </span>
              <span className="text-muted">
                Sub-Limits: <strong className="text-foreground">{a.diseaseSubLimits || "None"}</strong>
              </span>
            </div>
          );
        })()}

        {/* Chat area */}
        <div className="flex-1 overflow-y-auto p-6">
          {messages.length === 0 ? (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="max-w-3xl mx-auto mt-6">
              <div className="text-center mb-8">
                <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center border border-primary/20">
                  <Bot className="w-8 h-8 text-primary-light" />
                </div>
                <h2 className="text-2xl font-bold mb-2">Ask Any Real-Life Medical Scenario</h2>
                <p className="text-sm text-muted max-w-lg mx-auto">
                  Describe what happens, and ScenarioGPT audits your policy to tell you exactly if it is covered, how much you will pay out-of-pocket, and the fine-print conditions.
                </p>
              </div>

              {/* Quick Scenario Pills */}
              <div className="mb-4">
                <p className="text-xs text-muted mb-3 font-medium flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-primary-light" /> Popular Real-Life Scenarios:
                </p>
                <div className="grid sm:grid-cols-2 gap-3">
                  {scenarioSuggestions.map((s, i) => (
                    <motion.button
                      key={i}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: i * 0.04 }}
                      onClick={() => sendMessage(s.query)}
                      className="glass glass-hover rounded-xl p-3.5 text-left text-xs transition-all group flex items-start gap-2.5 border border-white/5"
                    >
                      <ChevronRight className="w-4 h-4 mt-0.5 text-primary-light opacity-60 group-hover:opacity-100 group-hover:translate-x-0.5 transition-all flex-shrink-0" />
                      <div>
                        <span className="font-semibold text-foreground block mb-0.5">{s.label}</span>
                        <span className="text-muted line-clamp-1">{s.query}</span>
                      </div>
                    </motion.button>
                  ))}
                </div>
              </div>
            </motion.div>
          ) : (
            <div className="max-w-3xl mx-auto space-y-6">
              <AnimatePresence>
                {messages.map((msg) => (
                  <motion.div
                    key={msg.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className={`flex gap-3 ${msg.role === "user" ? "justify-end" : ""}`}
                  >
                    {msg.role === "ai" && (
                      <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center flex-shrink-0 mt-1 shadow-sm shadow-primary/20">
                        <Bot className="w-4 h-4 text-white" />
                      </div>
                    )}

                    <div
                      className={`max-w-[88%] rounded-2xl p-5 ${
                        msg.role === "user"
                          ? "chat-user text-sm"
                          : "chat-ai text-sm space-y-4 border border-white/10"
                      }`}
                    >
                      {msg.role === "user" ? (
                        <div className="whitespace-pre-wrap leading-relaxed">{msg.content}</div>
                      ) : (
                        <>
                          {/* AI Response Header with Status Badge & Speech button */}
                          <div className="flex items-center justify-between gap-2 pb-3 border-b border-white/5">
                            <div className="flex items-center gap-2">
                              {msg.rawResult?.coverageStatus ? (
                                <span
                                  className={`px-3 py-1 rounded-full text-xs font-bold ${
                                    msg.rawResult.covered === true
                                      ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                                      : msg.rawResult.covered === "partial"
                                      ? "bg-amber-500/20 text-amber-400 border border-amber-500/30"
                                      : "bg-rose-500/20 text-rose-400 border border-rose-500/30"
                                  }`}
                                >
                                  {msg.rawResult.coverageStatus}
                                </span>
                              ) : (
                                <span className="px-3 py-1 rounded-full text-xs font-bold bg-primary/20 text-primary-light">
                                  Analysis Result
                                </span>
                              )}

                              {msg.policyName && (
                                <span className="text-[11px] text-muted hidden sm:inline-block">
                                  Policy: <strong className="text-foreground">{msg.policyName}</strong>
                                </span>
                              )}
                            </div>

                            {/* Audio TTS button */}
                            <button
                              onClick={() => handleSpeak(msg.id, msg.speechText || msg.content)}
                              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-xl text-xs font-medium transition-all ${
                                speakingMessageId === msg.id
                                  ? "bg-amber-500/20 text-amber-300 border border-amber-500/40 animate-pulse"
                                  : "glass hover:bg-white/10 text-muted hover:text-white"
                              }`}
                              title={speakingMessageId === msg.id ? "Stop voice playback" : "Listen aloud"}
                            >
                              {speakingMessageId === msg.id ? (
                                <>
                                  <VolumeX className="w-3.5 h-3.5" />
                                  <span>Stop</span>
                                </>
                              ) : (
                                <>
                                  <Volume2 className="w-3.5 h-3.5" />
                                  <span>Listen</span>
                                </>
                              )}
                            </button>
                          </div>

                          {/* Summary Paragraph */}
                          <p className="text-sm leading-relaxed text-foreground/90">{msg.content}</p>

                          {/* Cost Estimate Grid if present */}
                          {msg.rawResult?.estimatedCost && (
                            <div className="glass rounded-xl p-4 border border-white/5 space-y-2">
                              <h4 className="text-xs font-semibold text-muted uppercase tracking-wider flex items-center gap-1.5">
                                <Shield className="w-3.5 h-3.5 text-primary-light" /> Estimated Cost Breakdown
                              </h4>
                              <div className="grid grid-cols-3 gap-2 pt-1 text-center">
                                <div className="glass rounded-lg p-2.5">
                                  <span className="text-[10px] text-muted block mb-0.5">Total Hospital Bill</span>
                                  <span className="text-sm font-bold text-foreground">
                                    ₹{Math.round(msg.rawResult.estimatedCost.total).toLocaleString()}
                                  </span>
                                </div>
                                <div className="glass rounded-lg p-2.5 border border-emerald-500/20 bg-emerald-500/5">
                                  <span className="text-[10px] text-emerald-400 block mb-0.5">Insurance Pays</span>
                                  <span className="text-sm font-bold text-emerald-400">
                                    ₹{Math.round(msg.rawResult.estimatedCost.covered).toLocaleString()}
                                  </span>
                                </div>
                                <div className="glass rounded-lg p-2.5 border border-rose-500/20 bg-rose-500/5">
                                  <span className="text-[10px] text-rose-400 block mb-0.5">Your Out-Of-Pocket</span>
                                  <span className="text-sm font-bold text-rose-400">
                                    ₹{Math.round(msg.rawResult.estimatedCost.outOfPocket).toLocaleString()}
                                  </span>
                                </div>
                              </div>
                            </div>
                          )}

                          {/* Important Conditions */}
                          {msg.rawResult?.conditions && msg.rawResult.conditions.length > 0 && (
                            <div className="glass rounded-xl p-4 border border-white/5 space-y-2">
                              <h4 className="text-xs font-semibold text-muted uppercase tracking-wider flex items-center gap-1.5">
                                <AlertTriangle className="w-3.5 h-3.5 text-amber-400" /> Important Conditions & Caveats
                              </h4>
                              <ul className="space-y-1.5 pt-1">
                                {msg.rawResult.conditions.map((cond, idx) => (
                                  <li key={idx} className="text-xs text-muted flex items-start gap-2">
                                    <span className="w-1.5 h-1.5 rounded-full bg-amber-400 mt-1.5 flex-shrink-0" />
                                    <span>{cond}</span>
                                  </li>
                                ))}
                              </ul>
                            </div>
                          )}

                          {/* Recommendation */}
                          {msg.rawResult?.recommendation && (
                            <div className="glass rounded-xl p-3.5 border border-primary/20 bg-primary/5 flex items-start gap-2.5">
                              <Sparkles className="w-4 h-4 text-primary-light mt-0.5 flex-shrink-0" />
                              <div className="text-xs text-foreground/90 leading-relaxed">
                                <strong className="font-semibold text-primary-light block mb-0.5">Actuarial Advice:</strong>
                                {msg.rawResult.recommendation}
                              </div>
                            </div>
                          )}
                        </>
                      )}
                    </div>

                    {msg.role === "user" && (
                      <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-500 flex items-center justify-center flex-shrink-0 mt-1 shadow-sm shadow-indigo-500/20">
                        <User className="w-4 h-4 text-white" />
                      </div>
                    )}
                  </motion.div>
                ))}
              </AnimatePresence>

              {isTyping && (
                <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex gap-3">
                  <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center flex-shrink-0">
                    <Bot className="w-4 h-4 text-white" />
                  </div>
                  <div className="chat-ai rounded-2xl px-4 py-3 flex items-center gap-1.5 border border-white/10">
                    <span className="w-2 h-2 rounded-full bg-primary-light animate-bounce" style={{ animationDelay: "0ms" }} />
                    <span className="w-2 h-2 rounded-full bg-primary-light animate-bounce" style={{ animationDelay: "150ms" }} />
                    <span className="w-2 h-2 rounded-full bg-primary-light animate-bounce" style={{ animationDelay: "300ms" }} />
                    <span className="text-xs text-muted ml-2">Auditing policy clauses...</span>
                  </div>
                </motion.div>
              )}
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* Input Bar */}
        <div className="border-t border-border p-4 bg-background/50 backdrop-blur-md">
          <div className="max-w-3xl mx-auto flex gap-3">
            <div className="flex-1 glass rounded-2xl flex items-center px-4 gap-3 focus-within:border-primary-light/50 transition-colors">
              <Sparkles className="w-4 h-4 text-primary-light flex-shrink-0" />
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && sendMessage(input)}
                placeholder={`Describe a situation... e.g., 'What if I need surgery for kidney stones?'`}
                className="flex-1 bg-transparent py-3 text-sm outline-none placeholder:text-muted/50"
              />
            </div>
            <button
              onClick={() => sendMessage(input)}
              disabled={!input.trim() || isTyping}
              className="px-5 rounded-2xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 disabled:opacity-40 transition-all glow-primary flex items-center justify-center flex-shrink-0"
              title="Simulate Scenario"
            >
              <Send className="w-4 h-4" />
            </button>
          </div>
        </div>
      </main>
    </div>
  );
}
