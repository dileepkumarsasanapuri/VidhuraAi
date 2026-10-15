"use client";

import React from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  Shield, FileText, BarChart3, MessageSquare, GitCompare,
  Upload, ArrowRight, CheckCircle, Star, Zap, Globe, Brain,
  Lock, ChevronRight, Sparkles, Activity,
} from "lucide-react";
import { useAuth } from "@/lib/AuthContext";

const fadeUp = { hidden: { opacity: 1, y: 0 }, show: { opacity: 1, y: 0 } };
const stagger = { show: { transition: { staggerChildren: 0.1 } } };

const features = [
  { icon: FileText, title: "PolicyLens AI", desc: "Upload any policy — PDF, photo, or screenshot. AI extracts and simplifies every clause, exclusion, and hidden condition.", color: "from-indigo-500 to-violet-500", href: "/dashboard/upload", badge: "Audit & Extract" },
  { icon: MessageSquare, title: "ScenarioGPT", desc: "Ask real-life questions like 'What if I get dengue?' and get instant coverage answers with estimated costs.", color: "from-cyan-500 to-blue-500", href: "/dashboard/scenario", badge: "Simulate & Check" },
  { icon: Activity, title: "ClaimShield AI", desc: "Predict claim outcomes before filing. Know required documents, rejection risks, and approval probability.", color: "from-amber-500 to-orange-500", href: "/dashboard/claims", badge: "Pre-Claim Audit" },
  { icon: GitCompare, title: "CompareWise", desc: "Compare policies beyond premiums — hidden exclusions, real protection scores, and misleading similarities.", color: "from-emerald-500 to-teal-500", href: "/dashboard/compare", badge: "Head-to-Head" },
  { icon: Globe, title: "Voice AI", desc: "Multilingual voice assistant explains your policy in Hindi, Telugu, Tamil, Bengali, and more.", color: "from-pink-500 to-rose-500", href: "/dashboard/voice", badge: "Speech & Audio" },
  { icon: BarChart3, title: "TrustScore Engine", desc: "AI-generated transparency, risk, fairness, and claim reliability scores for every policy.", color: "from-purple-500 to-indigo-500", href: "/dashboard/analytics", badge: "Scores & Health" },
];

const steps = [
  { num: "01", title: "Upload Policy", desc: "PDF, photo, email, or screenshot — we handle it all." },
  { num: "02", title: "AI Analysis", desc: "Extracts clauses, exclusions, waiting periods, and hidden conditions." },
  { num: "03", title: "Personalized Insights", desc: "Based on your age, family, health, and lifestyle." },
  { num: "04", title: "Scenario Simulation", desc: "Test real-life events and see exactly what's covered." },
];

const stats = [
  { value: "60%", label: "Claim surprises reduced" },
  { value: "80+", label: "Policy understanding score" },
  { value: "40%", label: "Fewer support calls" },
  { value: "10K+", label: "Policies analyzed" },
];

export default function LandingPage() {
  const { isAuthenticated, isLoading } = useAuth();

  return (
    <div className="min-h-screen">
      {/* Navbar */}
      <nav className="fixed top-0 w-full z-50 glass border-b border-border">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center">
              <Shield className="w-5 h-5 text-white" />
            </div>
            <span className="text-lg font-bold gradient-text">Vidhura AI</span>
          </div>
          <div className="hidden md:flex items-center gap-8 text-sm text-muted">
            <a href="#features" className="hover:text-foreground transition-colors">Features</a>
            <a href="#how-it-works" className="hover:text-foreground transition-colors">How It Works</a>
            <a href="#open-source" className="hover:text-foreground transition-colors">Open & Free</a>
          </div>
          <div className="flex items-center gap-3">
            {!isLoading && isAuthenticated ? (
              <Link href="/dashboard" className="px-4 py-2 text-sm font-medium rounded-xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-opacity">
                Go to Dashboard
              </Link>
            ) : (
              <>
                <Link href="/login" className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white transition-colors">
                  Sign in
                </Link>
                <Link href="/register" className="px-4 py-2 text-sm font-medium rounded-xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-opacity">
                  Get Started
                </Link>
              </>
            )}
          </div>
        </div>
      </nav>

      {/* Hero */}
      <section className="pt-32 pb-20 px-6 relative overflow-hidden">
        <div className="absolute top-20 left-1/4 w-96 h-96 bg-primary/10 rounded-full blur-[120px] pointer-events-none" />
        <div className="absolute bottom-0 right-1/4 w-80 h-80 bg-accent/8 rounded-full blur-[100px] pointer-events-none" />
        <motion.div initial="hidden" animate="show" variants={stagger} className="max-w-4xl mx-auto text-center relative z-10">
          <motion.div variants={fadeUp} className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full glass text-xs font-medium text-primary-light mb-6">
            <Sparkles className="w-3.5 h-3.5" /> AI-Powered Insurance Intelligence
          </motion.div>
          <motion.h1 variants={fadeUp} className="text-4xl sm:text-5xl md:text-6xl lg:text-7xl font-extrabold leading-[1.1] mb-6">
            Understand Your{" "}
            <span className="gradient-text">Insurance</span>{" "}
            Before It Understands You
          </motion.h1>
          <motion.p variants={fadeUp} className="text-lg md:text-xl text-muted max-w-2xl mx-auto mb-10 leading-relaxed">
            Transform complex insurance policies into personalized, scenario-based explanations. No more hidden surprises. No more claim rejections.
          </motion.p>
          <motion.div variants={fadeUp} className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link href="/dashboard/upload" className="group px-8 py-3.5 text-sm font-semibold rounded-2xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-all flex items-center gap-2 glow-primary">
              Analyze Your Policy <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
            </Link>
            <a href="#how-it-works" className="px-8 py-3.5 text-sm font-medium rounded-2xl glass glass-hover flex items-center gap-2">
              See How It Works <ChevronRight className="w-4 h-4" />
            </a>
          </motion.div>
          <motion.div variants={fadeUp} className="mt-12 flex flex-wrap justify-center gap-6">
            {["100% Free — No Limits", "No selling bias", "100% transparent", "Multilingual", "Privacy first"].map((t) => (
              <div key={t} className="flex items-center gap-1.5 text-xs text-muted">
                <CheckCircle className="w-3.5 h-3.5 text-emerald-400" /> {t}
              </div>
            ))}
          </motion.div>
        </motion.div>
      </section>

      {/* Stats */}
      <section className="py-12 px-6 border-y border-border">
        <div className="max-w-5xl mx-auto grid grid-cols-2 md:grid-cols-4 gap-8">
          {stats.map((s, i) => (
            <motion.div key={i} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: i * 0.1 }} className="text-center">
              <div className="text-3xl md:text-4xl font-extrabold gradient-text mb-1">{s.value}</div>
              <div className="text-xs text-muted">{s.label}</div>
            </motion.div>
          ))}
        </div>
      </section>

      {/* Features */}
      <section id="features" className="py-24 px-6">
        <div className="max-w-6xl mx-auto">
          <motion.div initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-center mb-16">
            <h2 className="text-3xl md:text-4xl font-bold mb-4">
              Your Complete <span className="gradient-text">Insurance Intelligence</span> Suite
            </h2>
            <p className="text-muted max-w-xl mx-auto">Six powerful AI tools working together to make insurance transparent, understandable, and trustworthy.</p>
          </motion.div>
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            {features.map((f, i) => (
              <motion.div key={i} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: i * 0.08 }}>
                <Link
                  href={f.href}
                  className="glass glass-hover rounded-2xl p-6 group block h-full flex flex-col justify-between transition-all duration-300 hover:border-primary/40 hover:-translate-y-1 glow-primary"
                >
                  <div>
                    <div className="flex items-center justify-between mb-4">
                      <div className={`w-11 h-11 rounded-xl bg-gradient-to-br ${f.color} flex items-center justify-center group-hover:scale-110 transition-transform shadow-md`}>
                        <f.icon className="w-5 h-5 text-white" />
                      </div>
                      <span className="text-[10px] font-semibold tracking-wider uppercase px-2.5 py-0.5 rounded-full bg-white/5 border border-white/10 text-muted group-hover:text-white group-hover:border-primary/40 transition-colors">
                        {f.badge}
                      </span>
                    </div>
                    <h3 className="text-lg font-semibold mb-2 group-hover:text-primary-light transition-colors flex items-center gap-1.5">
                      {f.title}
                    </h3>
                    <p className="text-sm text-muted leading-relaxed mb-4">{f.desc}</p>
                  </div>
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-primary-light group-hover:translate-x-1 transition-transform pt-3 border-t border-white/5">
                    Launch {f.title} <ArrowRight className="w-3.5 h-3.5" />
                  </div>
                </Link>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section id="how-it-works" className="py-24 px-6 border-t border-border">
        <div className="max-w-5xl mx-auto">
          <motion.div initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-center mb-16">
            <h2 className="text-3xl md:text-4xl font-bold mb-4">How <span className="gradient-text">Vidhura AI</span> Works</h2>
            <p className="text-muted max-w-xl mx-auto">From upload to understanding in four simple steps.</p>
          </motion.div>
          <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-6">
            {steps.map((s, i) => (
              <motion.div key={i} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: i * 0.1 }}
                className="glass rounded-2xl p-6 relative group">
                <div className="text-5xl font-black text-white/5 absolute top-3 right-4">{s.num}</div>
                <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center mb-4 border border-primary/20">
                  <span className="text-sm font-bold gradient-text">{s.num}</span>
                </div>
                <h3 className="text-base font-semibold mb-2">{s.title}</h3>
                <p className="text-sm text-muted">{s.desc}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Open & Free */}
      <section id="open-source" className="py-24 px-6 border-t border-border">
        <div className="max-w-4xl mx-auto">
          <motion.div initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-center mb-12">
            <h2 className="text-3xl md:text-4xl font-bold mb-4">Every Feature, <span className="gradient-text">Completely Free</span></h2>
            <p className="text-muted max-w-xl mx-auto">We believe insurance understanding is a right, not a privilege. All features are available to everyone — no paywalls, no limits, no trials.</p>
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
            className="glass rounded-2xl p-8 glow-primary border border-primary/20">
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {[
                "Unlimited policy analysis", "Claim outcome prediction", "Scenario simulation",
                "Smart policy comparison", "Multilingual voice AI", "Risk & trust scoring",
                "Family coverage planner", "Claim filing guidance", "Real-time risk alerts",
              ].map((f) => (
                <div key={f} className="flex items-center gap-2 text-sm text-muted">
                  <CheckCircle className="w-4 h-4 text-emerald-400 flex-shrink-0" /> {f}
                </div>
              ))}
            </div>
            <div className="mt-8 text-center">
              <Link href="/dashboard" className="inline-flex items-center gap-2 px-8 py-3.5 text-sm font-semibold rounded-2xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-all glow-primary">
                Get Started — It&apos;s Free <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </motion.div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-24 px-6 border-t border-border">
        <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
          className="max-w-3xl mx-auto text-center glass rounded-3xl p-12 relative overflow-hidden">
          <div className="absolute inset-0 bg-gradient-to-br from-primary/5 to-accent/5 pointer-events-none" />
          <div className="relative z-10">
            <h2 className="text-3xl md:text-4xl font-bold mb-4">Insurance, Finally <span className="gradient-text">Explained</span></h2>
            <p className="text-muted mb-8 max-w-lg mx-auto">Join thousands who now understand their insurance. No more hidden surprises. No more claim rejections.</p>
            <Link href="/dashboard/upload" className="inline-flex items-center gap-2 px-8 py-3.5 text-sm font-semibold rounded-2xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-all glow-primary">
              Start Your Free Analysis <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </motion.div>
      </section>

      {/* Footer */}
      <footer className="py-12 px-6 border-t border-border">
        <div className="max-w-6xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-primary to-accent flex items-center justify-center">
              <Shield className="w-4 h-4 text-white" />
            </div>
            <span className="text-sm font-bold gradient-text">Vidhura AI</span>
          </div>
          <p className="text-xs text-muted">© 2026 Vidhura AI. Neutral AI interpreter — not a financial advisor.</p>
          <div className="flex gap-6 text-xs text-muted">
            <a href="#" className="hover:text-foreground transition-colors">Privacy</a>
            <a href="#" className="hover:text-foreground transition-colors">Terms</a>
            <a href="#" className="hover:text-foreground transition-colors">Contact</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
