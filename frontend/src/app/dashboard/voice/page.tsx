"use client";

import React, { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Mic,
  MicOff,
  Volume2,
  VolumeX,
  Globe,
  Sparkles,
  Shield,
  Send,
  RotateCcw,
  Bot,
  User as UserIcon,
  HelpCircle,
  CheckCircle,
  AlertTriangle,
  FileText,
  Loader2,
  Radio,
  Play,
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import { api, Policy, ScenarioResult } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

interface VoiceMessage {
  id: string;
  role: "user" | "ai";
  content: string;
  audioText?: string;
  lang?: string;
  ttsLang?: string;
  resultData?: ScenarioResult;
  timestamp: Date;
}

interface LanguageOption {
  code: string;
  name: string;
  nativeName: string;
  speechCode: string;
  ttsCode: string;
  sampleGreeting: string;
  prompts: string[];
}

const LANGUAGES: LanguageOption[] = [
  {
    code: "English",
    name: "English",
    nativeName: "English (India)",
    speechCode: "en-IN",
    ttsCode: "en",
    sampleGreeting: "Hello! I am Vidhura AI. Ask any question about your insurance policy coverage, room rent limits, or claim rules.",
    prompts: [
      "Will my cashless claim be accepted if I get admitted for dengue?",
      "What are the room rent limits and proportional deductions?",
      "Is cataract surgery covered immediately or is there a waiting period?",
      "What happens if I need emergency cardiac surgery?",
    ],
  },
  {
    code: "Hindi",
    name: "Hindi",
    nativeName: "हिंदी (Hindi)",
    speechCode: "hi-IN",
    ttsCode: "hi",
    sampleGreeting: "नमस्ते! मैं विधुरा एआई हूँ। अपनी स्वास्थ्य बीमा पॉलिसी, कैशलेस क्लेम और रूम रेंट के बारे में कोई भी प्रश्न पूछें।",
    prompts: [
      "क्या डेंगू के इलाज के लिए कैशलेस क्लेम मिलेगा?",
      "रूम रेंट की सीमा और छिपी हुई शर्तें क्या हैं?",
      "क्या मोतियाबिंद की सर्जरी तुरंत कवर होती है या वेटिंग पीरियड है?",
      "हार्ट अटैक या बाईपास सर्जरी पर कितना खर्च मिलेगा?",
    ],
  },
  {
    code: "Telugu",
    name: "Telugu",
    nativeName: "తెలుగు (Telugu)",
    speechCode: "te-IN",
    ttsCode: "te",
    sampleGreeting: "నమస్కారం! నేను విధుర ఏఐ. మీ ఆరోగ్య బీమా పాలసీ కవరేజ్, క్యాష్‌లెస్ క్లెయిమ్ మరియు నిబంధనల గురించి ఏదైనా అడగండి.",
    prompts: [
      "డెంగ్యూ ఆసుపత్రి ఖర్చులకు నా పాలసీ వర్తిస్తుందా?",
      "రూమ్ రెంట్ పరిమితులు మరియు నిబంధనలు ఏమిటి?",
      "కంటి శుక్లం (క్యాటరాక్ట్) సర్జరీకి కవరేజ్ ఎంత?",
      "హార్ట్ సర్జరీ లేదా బైపాస్‌కు క్యాష్‌లెస్ క్లెయిమ్ వస్తుందా?",
    ],
  },
  {
    code: "Tamil",
    name: "Tamil",
    nativeName: "தமிழ் (Tamil)",
    speechCode: "ta-IN",
    ttsCode: "ta",
    sampleGreeting: "வணக்கம்! நான் விதுரா ஏஐ. உங்கள் மருத்துவக் காப்பீடு மற்றும் பணமில்லா சிகிச்சை விதிகள் குறித்து கேட்கலாம்.",
    prompts: [
      "டெங்கு மருத்துவமனை சிகிச்சைக்கு க்ளைம் கிடைக்குமா?",
      "அறை வாடகை வரம்புகள் மற்றும் மறைக்கப்பட்ட விதிகள் யாவை?",
      "கண்புரை அறுவை சிகிச்சைக்கு காத்திருப்பு காலம் உள்ளதா?",
      "இதய அறுவை சிகிச்சைக்கு எவ்வளவு காப்பீடு கிடைக்கும்?",
    ],
  },
  {
    code: "Bengali",
    name: "Bengali",
    nativeName: "বাংলা (Bengali)",
    speechCode: "bn-IN",
    ttsCode: "bn",
    sampleGreeting: "নমস্কার! আমি বিধুরা এআই। আপনার স্বাস্থ্য বীমা পলিসি এবং ক্যাশলেস ক্লেমের শর্তাবলী সম্পর্কে যেকোনো প্রশ্ন করুন।",
    prompts: [
      "ডেঙ্গু চিকিৎসার জন্য কি ক্যাশলেস কভার পাব?",
      "রুম ভাড়ার সীমা এবং শর্তাবলী কী কী?",
      "চোখের ছানি অপারেশনে কি কোনো অপেক্ষার সময়সীমা আছে?",
      "হার্ট বাইপাস সার্জারির ক্ষেত্রে কতটা খরচ বহন করবে?",
    ],
  },
];

export default function VoiceAiPage() {
  const toast = useToast();
  const [selectedLang, setSelectedLang] = useState<LanguageOption>(LANGUAGES[0]);
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [selectedPolicyId, setSelectedPolicyId] = useState<string>("");
  const [isListening, setIsListening] = useState<boolean>(false);
  const [isSpeaking, setIsSpeaking] = useState<boolean>(false);
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [inputText, setInputText] = useState<string>("");
  const [messages, setMessages] = useState<VoiceMessage[]>([]);
  const [micVolume, setMicVolume] = useState<number>(0);
  const [activeAudioMessageId, setActiveAudioMessageId] = useState<string | null>(null);

  const recognitionRef = useRef<any>(null);
  const chatBottomRef = useRef<HTMLDivElement>(null);
  const handleSendRef = useRef<(text: string) => Promise<void>>(null as any);
  const audioPlayerRef = useRef<HTMLAudioElement | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const animationFrameRef = useRef<number | null>(null);

  const stopSpeaking = () => {
    if (audioPlayerRef.current) {
      audioPlayerRef.current.pause();
      audioPlayerRef.current = null;
    }
    if (typeof window !== "undefined" && window.speechSynthesis) {
      window.speechSynthesis.cancel();
    }
    setIsSpeaking(false);
    setActiveAudioMessageId(null);
  };

  const stopMicStream = () => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((track) => track.stop());
      mediaStreamRef.current = null;
    }
    if (audioContextRef.current && audioContextRef.current.state !== "closed") {
      audioContextRef.current.close().catch(() => {});
      audioContextRef.current = null;
    }
    setMicVolume(0);
  };

  // Load Policies
  useEffect(() => {
    async function fetchPolicies() {
      try {
        const res = await api.getPolicies();
        if (res.data && res.data.length > 0) {
          setPolicies(res.data);
          setSelectedPolicyId(res.data[0].id);
        }
      } catch (err) {
        console.error("Failed to fetch policies for Voice AI:", err);
      }
    }
    fetchPolicies();
  }, []);

  // Preload system voices if available
  useEffect(() => {
    if (typeof window !== "undefined" && window.speechSynthesis) {
      window.speechSynthesis.getVoices();
      window.speechSynthesis.onvoiceschanged = () => {
        window.speechSynthesis.getVoices();
      };
    }
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopSpeaking();
      stopMicStream();
      if (recognitionRef.current) {
        try {
          recognitionRef.current.abort();
        } catch (_) {}
      }
    };
  }, []);

  // Initialize Speech Recognition for active language
  useEffect(() => {
    if (typeof window !== "undefined") {
      const SpeechRecognition =
        (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

      if (SpeechRecognition) {
        const recognition = new SpeechRecognition();
        recognition.continuous = false;
        recognition.interimResults = true;
        recognition.lang = selectedLang.speechCode;

        recognition.onstart = () => {
          setIsListening(true);
        };

        recognition.onresult = (event: any) => {
          let interim = "";
          let finalTranscript = "";

          for (let i = event.resultIndex; i < event.results.length; ++i) {
            const transcript = event.results[i][0].transcript;
            if (event.results[i].isFinal) {
              finalTranscript += transcript;
            } else {
              interim += transcript;
            }
          }

          if (interim) {
            setInputText(interim);
          }

          if (finalTranscript.trim().length > 0) {
            setInputText(finalTranscript.trim());
            setIsListening(false);
            stopMicStream();
            handleSendRef.current?.(finalTranscript.trim());
          }
        };

        recognition.onerror = (err: any) => {
          console.warn("Speech recognition error event:", err.error);
          setIsListening(false);
          stopMicStream();

          if (err.error === "not-allowed") {
            toast.error(
              "Microphone access was denied. Please allow microphone permissions in your browser URL bar.",
              "Microphone Denied"
            );
          } else if (err.error === "audio-capture") {
            toast.error(
              "No microphone input detected. Please connect or unmute your microphone.",
              "Microphone Not Found"
            );
          } else if (err.error === "network") {
            toast.warning(
              "Browser speech network service is unavailable. You can click any suggested question below or type to hear full spoken responses.",
              "Voice Network Notice"
            );
          } else if (err.error !== "no-speech") {
            toast.warning(
              "Could not capture voice clearly. You can speak again or type your question below.",
              "Voice Input Notice"
            );
          }
        };

        recognition.onend = () => {
          setIsListening(false);
          stopMicStream();
        };

        recognitionRef.current = recognition;
      }
    }
  }, [selectedLang]);

  // Scroll to bottom of chat
  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isProcessing]);

  // Setup real audio volume analyser from microphone
  const startMicStream = async (): Promise<boolean> => {
    try {
      if (typeof window === "undefined" || !navigator.mediaDevices?.getUserMedia) {
        return true;
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;

      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      if (AudioCtx) {
        const audioCtx = new AudioCtx();
        audioContextRef.current = audioCtx;
        const source = audioCtx.createMediaStreamSource(stream);
        const analyser = audioCtx.createAnalyser();
        analyser.fftSize = 64;
        source.connect(analyser);

        const dataArray = new Uint8Array(analyser.frequencyBinCount);
        const updateMeter = () => {
          analyser.getByteFrequencyData(dataArray);
          let sum = 0;
          for (let i = 0; i < dataArray.length; i++) {
            sum += dataArray[i];
          }
          const avg = sum / dataArray.length;
          setMicVolume(Math.min(100, Math.round(avg * 1.5)));
          animationFrameRef.current = requestAnimationFrame(updateMeter);
        };
        updateMeter();
      }
      return true;
    } catch (err: any) {
      console.warn("Microphone stream request error:", err);
      toast.warning(
        "Microphone access is needed to speak. Please check your browser microphone permission or type below.",
        "Microphone Notice"
      );
      return false;
    }
  };

  // Toggle Microphone
  const toggleListening = async () => {
    if (isSpeaking) {
      stopSpeaking();
    }

    if (isListening) {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch (_) {}
      }
      setIsListening(false);
      stopMicStream();
      return;
    }

    // Request stream permission first
    const micGranted = await startMicStream();
    if (!micGranted) {
      return;
    }

    if (recognitionRef.current) {
      try {
        recognitionRef.current.lang = selectedLang.speechCode;
        recognitionRef.current.start();
        setIsListening(true);
      } catch (e: any) {
        console.error("Speech recognition start failed:", e);
        setIsListening(false);
        stopMicStream();
      }
    } else {
      toast.warning(
        "Voice speech input is supported in Chrome, Edge, and Brave. You can also click any suggested prompt or type your query below to hear spoken AI responses.",
        "Voice Input"
      );
    }
  };

  // Dual-Engine Speech Output: Plays native human audio via backend/Google TTS stream with fallback
  const speakText = (
    text: string,
    speechCode: string = selectedLang.speechCode,
    ttsCode: string = selectedLang.ttsCode,
    messageId?: string
  ) => {
    stopSpeaking();
    if (!text || !text.trim()) return;

    // Clean text of markdown and technical symbols
    const cleanSpeech = text
      .replace(/[*#_~`>]/g, "")
      .replace(/[✅⚠️❌🏥💡⏱️📋]/gu, "")
      .replace(/₹\s*/g, "Rupees ")
      .trim();

    if (!cleanSpeech) return;

    setIsSpeaking(true);
    if (messageId) {
      setActiveAudioMessageId(messageId);
    }

    // Split into readable sentences/clauses (max 140 chars per chunk)
    const rawChunks = cleanSpeech.match(/[^.!?।\n]+[.!?।\n]+|[^.!?।\n]+/g) || [cleanSpeech];
    const chunks: string[] = [];
    rawChunks.forEach((chunk) => {
      const trimmed = chunk.trim();
      if (trimmed.length > 150) {
        const subChunks = trimmed.match(/.{1,140}(\s|$)/g) || [trimmed];
        subChunks.forEach((s) => chunks.push(s.trim()));
      } else if (trimmed.length > 0) {
        chunks.push(trimmed);
      }
    });

    if (chunks.length === 0) {
      setIsSpeaking(false);
      return;
    }

    let chunkIdx = 0;

    const playNextChunk = () => {
      if (chunkIdx >= chunks.length) {
        setIsSpeaking(false);
        setActiveAudioMessageId(null);
        return;
      }

      const currentText = chunks[chunkIdx];
      // 1. Try Backend TTS endpoint (/api/scenario/tts)
      const primaryUrl = `/api/scenario/tts?lang=${encodeURIComponent(
        ttsCode
      )}&text=${encodeURIComponent(currentText)}`;

      // 2. Direct Google TTS fallback URL
      const fallbackUrl = `https://translate.google.com/translate_tts?ie=UTF-8&tl=${encodeURIComponent(
        ttsCode
      )}&client=tw-ob&q=${encodeURIComponent(currentText)}`;

      const audio = new Audio(primaryUrl);
      audioPlayerRef.current = audio;

      audio.onended = () => {
        chunkIdx++;
        playNextChunk();
      };

      audio.onerror = () => {
        console.warn("Backend TTS stream fallback to Google direct TTS...");
        const fbAudio = new Audio(fallbackUrl);
        audioPlayerRef.current = fbAudio;
        fbAudio.onended = () => {
          chunkIdx++;
          playNextChunk();
        };
        fbAudio.onerror = () => {
          console.warn("Network TTS failed, falling back to Web Speech Synthesis...");
          fallbackWebSpeech(chunks.slice(chunkIdx).join(" "), speechCode);
        };
        fbAudio.play().catch(() => {
          fallbackWebSpeech(chunks.slice(chunkIdx).join(" "), speechCode);
        });
      };

      audio.play().catch((playErr) => {
        console.warn("Autoplay audio blocked or error:", playErr);
        // If autoplay blocked, fall back to Web Speech Synthesis
        fallbackWebSpeech(chunks.slice(chunkIdx).join(" "), speechCode);
      });
    };

    const fallbackWebSpeech = (remainingText: string, langCode: string) => {
      if (typeof window === "undefined" || !window.speechSynthesis) {
        setIsSpeaking(false);
        setActiveAudioMessageId(null);
        return;
      }

      window.speechSynthesis.cancel();
      setTimeout(() => {
        try {
          window.speechSynthesis.resume();
          const utterance = new SpeechSynthesisUtterance(remainingText);
          utterance.lang = langCode;
          utterance.rate = 0.95;
          utterance.pitch = 1.0;

          const voices = window.speechSynthesis.getVoices();
          const matchVoice =
            voices.find((v) => v.lang.replace("_", "-").startsWith(langCode.slice(0, 2))) ||
            voices.find((v) => v.lang.startsWith("en-IN")) ||
            voices[0];

          if (matchVoice) {
            utterance.voice = matchVoice;
          }

          utterance.onstart = () => setIsSpeaking(true);
          utterance.onend = () => {
            setIsSpeaking(false);
            setActiveAudioMessageId(null);
          };
          utterance.onerror = () => {
            setIsSpeaking(false);
            setActiveAudioMessageId(null);
          };

          window.speechSynthesis.speak(utterance);
        } catch (e) {
          console.error("Web Speech error:", e);
          setIsSpeaking(false);
          setActiveAudioMessageId(null);
        }
      }, 80);
    };

    playNextChunk();
  };

  // Test speaker output with active language greeting
  const testVoiceOutput = () => {
    speakText(selectedLang.sampleGreeting, selectedLang.speechCode, selectedLang.ttsCode);
  };

  // Submit Question to Backend Scenario Engine
  const handleSend = async (questionText: string) => {
    if (!questionText.trim()) return;

    const userMsg: VoiceMessage = {
      id: "u_" + Date.now(),
      role: "user",
      content: questionText,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputText("");
    setIsProcessing(true);

    try {
      const res = await api.simulateScenario(
        questionText,
        selectedPolicyId || undefined,
        selectedLang.code
      );

      const result = res.data;
      let speechSummary = result.summary || "Here is your coverage audit breakdown.";
      if (result.recommendation) {
        speechSummary += " Recommendation: " + result.recommendation;
      }

      const aiMsgId = "ai_" + Date.now();
      const aiMsg: VoiceMessage = {
        id: aiMsgId,
        role: "ai",
        content: result.summary,
        audioText: speechSummary,
        lang: selectedLang.speechCode,
        ttsLang: selectedLang.ttsCode,
        resultData: result,
        timestamp: new Date(),
      };

      setMessages((prev) => [...prev, aiMsg]);
      // Speak the answer aloud with high-fidelity human speech
      speakText(speechSummary, selectedLang.speechCode, selectedLang.ttsCode, aiMsgId);
    } catch (err: any) {
      console.error("Voice AI scenario error:", err);
      const errMsgId = "err_" + Date.now();
      const errText = "I could not complete the policy audit. Please check your network or try again.";
      const errMsg: VoiceMessage = {
        id: errMsgId,
        role: "ai",
        content: errText,
        audioText: errText,
        lang: selectedLang.speechCode,
        ttsLang: selectedLang.ttsCode,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errMsg]);
      speakText(errText, selectedLang.speechCode, selectedLang.ttsCode, errMsgId);
    } finally {
      setIsProcessing(false);
    }
  };
  handleSendRef.current = handleSend;

  const selectedPolicy = policies.find((p) => p.id === selectedPolicyId);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/voice" />

      <main className="flex-1 flex flex-col h-screen overflow-hidden bg-background">
        {/* Header */}
        <header className="glass border-b border-border px-4 sm:px-6 h-16 flex items-center justify-between flex-shrink-0 z-30">
          <div className="flex items-center gap-2.5 sm:gap-3 ml-11 lg:ml-0">
            <div className="w-8 sm:w-9 h-8 sm:h-9 rounded-xl bg-gradient-to-br from-pink-500 to-rose-500 flex items-center justify-center shadow-lg shadow-pink-500/20 flex-shrink-0">
              <Globe className="w-4 sm:w-5 h-4 sm:h-5 text-white" />
            </div>
            <div>
              <h1 className="text-sm sm:text-base font-bold flex items-center gap-1.5 sm:gap-2">
                Voice AI
                <span className="text-[9px] sm:text-[10px] uppercase font-bold tracking-wider px-1.5 sm:px-2 py-0.5 rounded-full bg-pink-500/20 text-pink-400 border border-pink-500/30">
                  Multilingual
                </span>
              </h1>
              <p className="text-[11px] sm:text-xs text-muted hidden xs:block truncate">Audits policy in your language</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Test Audio Output Button */}
            <button
              onClick={testVoiceOutput}
              className="hidden sm:flex items-center gap-1.5 glass hover:bg-white/10 px-3 py-1.5 rounded-xl text-xs text-pink-300 hover:text-white border border-pink-500/20 transition-all"
              title="Click to test voice audio output on your speakers"
            >
              <Volume2 className="w-3.5 h-3.5 text-pink-400" />
              <span>Test Audio</span>
            </button>

            {/* Language Picker */}
            <div className="flex items-center gap-1.5 glass rounded-xl px-3 py-1.5 text-xs border border-white/10">
              <Globe className="w-3.5 h-3.5 text-pink-400" />
              <select
                value={selectedLang.code}
                onChange={(e) => {
                  const lang = LANGUAGES.find((l) => l.code === e.target.value) || LANGUAGES[0];
                  setSelectedLang(lang);
                  stopSpeaking();
                }}
                className="bg-transparent outline-none font-medium text-xs cursor-pointer"
              >
                {LANGUAGES.map((l) => (
                  <option key={l.code} value={l.code} className="bg-slate-900 text-white">
                    {l.nativeName}
                  </option>
                ))}
              </select>
            </div>

            {/* Policy Selector */}
            {policies.length > 0 && (
              <select
                value={selectedPolicyId}
                onChange={(e) => setSelectedPolicyId(e.target.value)}
                className="glass rounded-xl px-3 py-1.5 text-xs outline-none bg-slate-900/80 max-w-[200px] truncate border border-white/10"
              >
                {policies.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            )}
          </div>
        </header>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-6 max-w-4xl w-full mx-auto">
          {/* Active Policy Pill */}
          {selectedPolicy && (
            <div className="glass rounded-xl px-4 py-2 flex items-center justify-between text-xs border border-primary/20">
              <div className="flex items-center gap-2 text-muted truncate">
                <Shield className="w-4 h-4 text-primary-light flex-shrink-0" />
                <span>Auditing active policy:</span>
                <strong className="text-foreground">{selectedPolicy.name}</strong>
                <span className="hidden sm:inline">({selectedPolicy.provider})</span>
              </div>
              <span className="text-emerald-400 font-semibold bg-emerald-500/10 px-2 py-0.5 rounded-lg border border-emerald-500/20">
                Sum Insured: {selectedPolicy.coverage || "₹10L"}
              </span>
            </div>
          )}

          {/* Voice Orb Hero Component */}
          <div className="glass rounded-3xl p-6 sm:p-8 text-center relative overflow-hidden glow-primary border border-pink-500/20 shadow-2xl">
            <div className="absolute top-0 right-1/4 w-40 h-40 bg-pink-500/10 rounded-full blur-[80px] pointer-events-none" />
            <div className="absolute bottom-0 left-1/4 w-40 h-40 bg-primary/10 rounded-full blur-[80px] pointer-events-none" />

            {/* Glowing Interactive Visualizer Orb */}
            <div className="relative w-36 h-36 mx-auto mb-6 flex items-center justify-center">
              {/* Outer Waves when Listening */}
              {isListening && (
                <>
                  <motion.div
                    className="absolute inset-0 rounded-full border-2 border-pink-500/40"
                    animate={{ scale: [1, 1.5 + micVolume / 100, 2], opacity: [0.8, 0.4, 0] }}
                    transition={{ duration: 1.5, repeat: Infinity, ease: "easeOut" }}
                  />
                  <motion.div
                    className="absolute inset-0 rounded-full border border-rose-400/50"
                    animate={{ scale: [1, 1.3 + micVolume / 150, 1.7], opacity: [0.7, 0.3, 0] }}
                    transition={{ duration: 1.5, delay: 0.3, repeat: Infinity, ease: "easeOut" }}
                  />
                </>
              )}

              {/* Sound Glow when Speaking */}
              {isSpeaking && (
                <>
                  <motion.div
                    className="absolute inset-0 rounded-full bg-pink-500/30 blur-md"
                    animate={{ scale: [1, 1.35, 1], opacity: [0.6, 1, 0.6] }}
                    transition={{ duration: 1.2, repeat: Infinity, ease: "easeInOut" }}
                  />
                  <motion.div
                    className="absolute -inset-4 rounded-full border border-pink-400/30"
                    animate={{ scale: [1, 1.2, 1], opacity: [0.3, 0.7, 0.3] }}
                    transition={{ duration: 1.2, delay: 0.2, repeat: Infinity, ease: "easeInOut" }}
                  />
                </>
              )}

              {/* Main Interactive Button */}
              <button
                type="button"
                onClick={toggleListening}
                style={{
                  transform: isListening ? `scale(${1 + (micVolume / 100) * 0.25})` : undefined,
                }}
                className={`w-24 h-24 rounded-full flex flex-col items-center justify-center transition-all shadow-xl relative z-10 ${
                  isListening
                    ? "bg-gradient-to-tr from-rose-600 to-pink-500 text-white shadow-rose-500/50 ring-4 ring-rose-400/30"
                    : isSpeaking
                    ? "bg-gradient-to-tr from-purple-600 to-pink-500 text-white shadow-purple-500/40"
                    : "bg-gradient-to-tr from-primary to-accent text-white hover:scale-105 shadow-primary/30"
                }`}
                title={isListening ? "Listening... click to stop" : "Click to speak"}
              >
                {isListening ? (
                  <MicOff className="w-8 h-8 animate-pulse text-white" />
                ) : isSpeaking ? (
                  <Volume2 className="w-8 h-8 animate-bounce text-white" />
                ) : (
                  <Mic className="w-8 h-8 text-white" />
                )}
              </button>
            </div>

            {/* Dynamic Status Text */}
            <h2 className="text-xl font-bold mb-1">
              {isListening
                ? `Listening in ${selectedLang.nativeName}...`
                : isSpeaking
                ? `Explaining in ${selectedLang.nativeName}...`
                : isProcessing
                ? "Analyzing policy clauses with Llama 3.3 AI..."
                : `Talk to Voice AI in ${selectedLang.nativeName}`}
            </h2>

            <p className="text-xs text-muted max-w-md mx-auto mb-4">
              {isListening
                ? micVolume > 10
                  ? "🎙️ Hearing your voice... keep speaking"
                  : "Speak your insurance question clearly into your microphone..."
                : isSpeaking
                ? "Playing authentic voice explanation. Click below to pause."
                : "Ask any question about your insurance policy, cashless eligibility, room rent, or claim rules."}
            </p>

            {/* Sound Wave Animation Bars when Speaking */}
            {isSpeaking && (
              <div className="flex items-center justify-center gap-1 my-3">
                {[0.4, 0.8, 1.2, 0.6, 1.0, 0.7, 0.3, 0.9, 0.5].map((h, i) => (
                  <motion.span
                    key={i}
                    className="w-1 bg-pink-400 rounded-full"
                    animate={{ height: ["8px", `${h * 24}px`, "8px"] }}
                    transition={{
                      duration: 0.6,
                      repeat: Infinity,
                      delay: i * 0.08,
                      ease: "easeInOut",
                    }}
                  />
                ))}
              </div>
            )}

            {/* Stop Audio Button */}
            {isSpeaking && (
              <motion.button
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                onClick={stopSpeaking}
                className="inline-flex items-center gap-2 px-4 py-1.5 text-xs font-semibold rounded-full bg-rose-500/20 text-rose-300 border border-rose-500/30 hover:bg-rose-500/30 transition-colors"
              >
                <VolumeX className="w-3.5 h-3.5" /> Stop Speaking
              </motion.button>
            )}

            {/* Quick Prompts Carousel */}
            <div className="mt-6 pt-6 border-t border-white/5">
              <p className="text-[11px] text-muted mb-3 uppercase tracking-wider font-semibold">
                Suggested voice questions in {selectedLang.name}:
              </p>
              <div className="flex flex-wrap justify-center gap-2">
                {selectedLang.prompts.map((p, idx) => (
                  <button
                    key={idx}
                    onClick={() => handleSend(p)}
                    className="text-xs glass glass-hover px-3 py-1.5 rounded-xl text-left border border-white/10 hover:border-pink-500/30 transition-all text-slate-300 hover:text-white"
                  >
                    "{p}"
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Conversation Thread */}
          {messages.length > 0 && (
            <div className="space-y-4 pt-2">
              <div className="flex items-center justify-between text-xs text-muted px-2">
                <span className="font-semibold uppercase tracking-wider">Audit Conversation</span>
                <button
                  onClick={() => {
                    stopSpeaking();
                    setMessages([]);
                  }}
                  className="flex items-center gap-1 hover:text-white transition-colors"
                >
                  <RotateCcw className="w-3 h-3" /> Clear Chat
                </button>
              </div>

              {messages.map((m) => (
                <motion.div
                  key={m.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={`flex gap-3 ${m.role === "user" ? "justify-end" : "justify-start"}`}
                >
                  {m.role === "ai" && (
                    <div className="w-8 h-8 rounded-xl bg-pink-500/20 border border-pink-500/30 flex items-center justify-center flex-shrink-0 mt-1">
                      <Bot className="w-4 h-4 text-pink-400" />
                    </div>
                  )}

                  <div
                    className={`max-w-2xl rounded-2xl p-4 text-sm leading-relaxed ${
                      m.role === "user"
                        ? "bg-gradient-to-r from-primary/80 to-accent/80 text-white rounded-tr-sm"
                        : "glass border border-white/10 text-slate-200 rounded-tl-sm space-y-3"
                    }`}
                  >
                    <p className="whitespace-pre-wrap">{m.content}</p>

                    {/* Rich scenario metadata card if AI message */}
                    {m.resultData && (
                      <div className="mt-3 pt-3 border-t border-white/10 space-y-2 text-xs">
                        <div className="flex items-center justify-between font-semibold">
                          <span>Coverage Status:</span>
                          <span
                            className={
                              m.resultData.covered === true
                                ? "text-emerald-400"
                                : m.resultData.covered === "partial"
                                ? "text-amber-400"
                                : "text-rose-400"
                            }
                          >
                            {m.resultData.coverageStatus ||
                              (m.resultData.covered === true ? "✅ Covered" : "❌ Excluded")}
                          </span>
                        </div>

                        {m.resultData.estimatedCost && (
                          <div className="grid grid-cols-3 gap-2 bg-white/5 p-2 rounded-xl text-center">
                            <div>
                              <div className="text-[10px] text-muted">Est. Bill</div>
                              <div className="font-semibold">
                                ₹{m.resultData.estimatedCost.total?.toLocaleString()}
                              </div>
                            </div>
                            <div>
                              <div className="text-[10px] text-muted">Insurer Pays</div>
                              <div className="font-semibold text-emerald-400">
                                ₹{m.resultData.estimatedCost.covered?.toLocaleString()}
                              </div>
                            </div>
                            <div>
                              <div className="text-[10px] text-muted">Out-of-Pocket</div>
                              <div className="font-semibold text-rose-400">
                                ₹{m.resultData.estimatedCost.outOfPocket?.toLocaleString()}
                              </div>
                            </div>
                          </div>
                        )}

                        {m.resultData.recommendation && (
                          <div className="bg-primary/10 p-2.5 rounded-xl border border-primary/20 text-xs">
                            <strong className="text-primary-light block mb-0.5">
                              💡 Expert Recommendation:
                            </strong>
                            {m.resultData.recommendation}
                          </div>
                        )}
                      </div>
                    )}

                    {/* Audio Listen Again / Replay Button */}
                    {m.role === "ai" && m.audioText && (
                      <div className="pt-2 flex items-center justify-between border-t border-white/5 mt-2">
                        <span className="text-[10px] text-muted flex items-center gap-1">
                          <Radio className="w-3 h-3 text-pink-400" />
                          Spoken in {selectedLang.name}
                        </span>
                        <button
                          type="button"
                          onClick={() =>
                            speakText(
                              m.audioText!,
                              m.lang || selectedLang.speechCode,
                              m.ttsLang || selectedLang.ttsCode,
                              m.id
                            )
                          }
                          className={`inline-flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-xl transition-all ${
                            isSpeaking && activeAudioMessageId === m.id
                              ? "bg-pink-500/20 text-pink-300 border border-pink-500/40"
                              : "glass hover:text-white text-pink-300 border border-white/10"
                          }`}
                        >
                          {isSpeaking && activeAudioMessageId === m.id ? (
                            <>
                              <Volume2 className="w-3.5 h-3.5 animate-bounce text-pink-400" />
                              <span>Playing...</span>
                            </>
                          ) : (
                            <>
                              <Play className="w-3.5 h-3.5" />
                              <span>Listen to Explanation</span>
                            </>
                          )}
                        </button>
                      </div>
                    )}
                  </div>

                  {m.role === "user" && (
                    <div className="w-8 h-8 rounded-xl bg-primary/20 border border-primary/30 flex items-center justify-center flex-shrink-0 mt-1">
                      <UserIcon className="w-4 h-4 text-primary-light" />
                    </div>
                  )}
                </motion.div>
              ))}

              {isProcessing && (
                <div className="flex gap-3 justify-start items-center">
                  <div className="w-8 h-8 rounded-xl bg-pink-500/20 border border-pink-500/30 flex items-center justify-center flex-shrink-0">
                    <Loader2 className="w-4 h-4 text-pink-400 animate-spin" />
                  </div>
                  <div className="glass px-4 py-2 rounded-2xl text-xs text-muted flex items-center gap-2">
                    <Sparkles className="w-3.5 h-3.5 text-pink-400 animate-pulse" />
                    Querying policy clauses in {selectedLang.name}...
                  </div>
                </div>
              )}

              <div ref={chatBottomRef} />
            </div>
          )}
        </div>

        {/* Bottom Input Bar with Mobile Bottom Nav Clearance */}
        <div className="p-3 sm:p-4 glass border-t border-border flex-shrink-0 pb-20 lg:pb-4">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSend(inputText);
            }}
            className="max-w-4xl mx-auto flex items-center gap-2"
          >
            <button
              type="button"
              onClick={toggleListening}
              className={`p-3 rounded-xl transition-all ${
                isListening
                  ? "bg-rose-500 text-white animate-pulse ring-2 ring-rose-400/40"
                  : "glass hover:text-pink-400 text-muted"
              }`}
              title={isListening ? "Stop listening" : "Start voice recording"}
            >
              <Mic className="w-5 h-5" />
            </button>

            <input
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder={`Ask in ${selectedLang.nativeName} (e.g. "${selectedLang.prompts[0]}")...`}
              className="flex-1 glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-pink-500/40 border border-transparent transition-colors"
            />

            <button
              type="submit"
              disabled={!inputText.trim() || isProcessing}
              className="p-3 rounded-xl bg-gradient-to-r from-pink-500 to-rose-500 text-white disabled:opacity-40 hover:opacity-90 transition-opacity shadow-md shadow-pink-500/20"
            >
              <Send className="w-4 h-4" />
            </button>
          </form>
        </div>
      </main>
    </div>
  );
}
