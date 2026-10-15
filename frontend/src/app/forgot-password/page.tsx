"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import {
  Shield, Mail, Lock, KeyRound, ArrowLeft, CheckCircle2,
  AlertTriangle, Loader2, Eye, EyeOff, Sparkles, RefreshCw
} from "lucide-react";
import { api } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

type Step = "REQUEST_OTP" | "VERIFY_AND_RESET" | "SUCCESS";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const toast = useToast();

  const [step, setStep] = useState<Step>("REQUEST_OTP");
  const [email, setEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resendCooldown, setResendCooldown] = useState(0);

  // Countdown timer for Resend Code in production
  useEffect(() => {
    if (step !== "VERIFY_AND_RESET" || resendCooldown <= 0) return;
    const timer = setInterval(() => {
      setResendCooldown((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [step, resendCooldown]);

  // Step 1: Send OTP to email
  const handleRequestOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) {
      setError("Please enter your registered email address.");
      return;
    }

    setError(null);
    setLoading(true);

    try {
      const res = await api.forgotPassword(email.trim());
      if (res.success) {
        setOtp(""); // Ensure OTP input is always clean and requires manual entry
        setResendCooldown(30);
        toast.success(
          `We sent an email with a 6-digit verification code to ${email.trim()}.`,
          "OTP Dispatched"
        );
        setStep("VERIFY_AND_RESET");
      } else {
        setError(res.error || "Failed to generate verification code. Please check your email.");
        toast.error(res.error || "Please verify your email address.", "Account Not Found");
      }
    } catch (err: any) {
      setError(err.message || "Network error while requesting verification code.");
    } finally {
      setLoading(false);
    }
  };

  // Resend OTP
  const handleResendOtp = async () => {
    if (resendCooldown > 0 || loading) return;
    setError(null);
    setLoading(true);
    try {
      const res = await api.forgotPassword(email.trim());
      if (res.success) {
        setResendCooldown(45);
        toast.info(
          `A fresh 6-digit verification code has been dispatched to ${email.trim()}.`,
          "New Code Sent"
        );
      } else {
        setError(res.error || "Unable to resend code.");
      }
    } catch (err: any) {
      setError(err.message || "Failed to resend code.");
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Verify OTP & Reset Password
  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const cleanOtp = otp.trim();
    if (!cleanOtp || cleanOtp.length !== 6) {
      setError("Please enter the complete 6-digit code received in your email.");
      return;
    }

    if (newPassword.length < 6) {
      setError("New password must be at least 6 characters long.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("Passwords do not match. Please re-enter.");
      return;
    }

    setLoading(true);

    try {
      const res = await api.resetPassword(email.trim(), cleanOtp, newPassword);
      if (res.success) {
        toast.success("Your password has been reset. You can now log in.", "Password Updated");
        setStep("SUCCESS");
      } else {
        setError(res.error || "Invalid verification code. Please check your email.");
        toast.error(res.error || "Invalid or expired verification code.", "Reset Failed");
      }
    } catch (err: any) {
      setError(err.message || "Error processing password reset.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-950 text-white px-4 py-12 relative overflow-hidden">
      {/* Background ambient lighting */}
      <div className="absolute top-20 left-1/4 w-96 h-96 bg-primary/10 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-20 right-1/4 w-80 h-80 bg-accent/8 rounded-full blur-[100px] pointer-events-none" />

      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="max-w-md w-full relative z-10"
      >
        {/* Brand header */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-primary to-accent flex items-center justify-center glow-primary">
            <Shield className="w-7 h-7 text-white" />
          </div>
          <div>
            <span className="text-xl font-bold gradient-text">Vidhura AI</span>
            <p className="text-[10px] text-muted">Account Security & Recovery</p>
          </div>
        </div>

        <div className="glass rounded-3xl p-8 glow-primary border border-primary/10 shadow-2xl">
          {/* Back link */}
          {step !== "SUCCESS" && (
            <Link
              href="/login"
              className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors mb-6"
            >
              <ArrowLeft className="w-3.5 h-3.5" /> Back to Sign In
            </Link>
          )}

          {/* Error banner */}
          {error && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="mb-5 rounded-xl bg-red-500/10 border border-red-500/30 text-red-200 px-4 py-3 text-xs flex items-start gap-2.5"
            >
              <AlertTriangle className="w-4 h-4 text-red-400 flex-shrink-0 mt-0.5" />
              <span>{error}</span>
            </motion.div>
          )}

          {/* STEP 1: REQUEST OTP */}
          {step === "REQUEST_OTP" && (
            <div>
              <div className="mb-6 text-center">
                <div className="w-12 h-12 rounded-2xl bg-primary/10 border border-primary/20 flex items-center justify-center mx-auto mb-3 text-primary-light">
                  <KeyRound className="w-6 h-6" />
                </div>
                <h1 className="text-2xl font-bold mb-2">Forgot Password?</h1>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Enter your registered email address and we will dispatch a secure 6-digit verification code to your inbox.
                </p>
              </div>

              <form onSubmit={handleRequestOtp} className="space-y-4">
                <div>
                  <label className="text-xs font-medium text-slate-400 mb-1.5 block">
                    Email Address
                  </label>
                  <div className="relative">
                    <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                    <input
                      id="forgot-email"
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      required
                      placeholder="you@example.com"
                      className="w-full rounded-xl border border-slate-700/50 bg-slate-950/60 pl-11 pr-4 py-3 text-sm text-white outline-none transition-all focus:border-primary/50 focus:ring-1 focus:ring-primary/20 placeholder:text-slate-600"
                    />
                  </div>
                </div>

                <button
                  id="send-otp-btn"
                  type="submit"
                  disabled={loading}
                  className="w-full rounded-xl bg-gradient-to-r from-primary to-accent px-5 py-3 text-sm font-semibold text-white transition-all hover:opacity-90 hover:shadow-lg hover:shadow-primary/20 disabled:opacity-50 flex items-center justify-center gap-2 glow-primary"
                >
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" /> Sending Verification Code...
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-4 h-4" /> Send Verification Code
                    </>
                  )}
                </button>
              </form>
            </div>
          )}

          {/* STEP 2: VERIFY OTP & RESET PASSWORD */}
          {step === "VERIFY_AND_RESET" && (
            <div>
              <div className="mb-6 text-center">
                <div className="w-12 h-12 rounded-2xl bg-accent/10 border border-accent/20 flex items-center justify-center mx-auto mb-3 text-accent">
                  <Lock className="w-6 h-6" />
                </div>
                <h1 className="text-2xl font-bold mb-1.5">Reset Password</h1>
                <p className="text-xs text-slate-400">
                  Enter the verification code sent to{" "}
                  <span className="text-white font-medium">{email}</span>
                </p>
              </div>

              {/* Production Inbox Guidance Card */}
              <div className="mb-5 p-3.5 rounded-xl bg-primary/10 border border-primary/20 flex items-start gap-3 text-xs text-slate-300">
                <Mail className="w-4 h-4 text-primary-light flex-shrink-0 mt-0.5" />
                <p className="leading-relaxed">
                  We sent an email with a 6-digit verification code to <strong className="text-white">{email}</strong>. Please check your inbox and spam folder.
                </p>
              </div>

              <form onSubmit={handleResetPassword} className="space-y-4">
                {/* 6-Digit OTP Input */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="text-xs font-medium text-slate-400 block">
                      6-Digit Verification Code
                    </label>
                    <button
                      type="button"
                      onClick={handleResendOtp}
                      disabled={loading || resendCooldown > 0}
                      className="text-[11px] text-primary-light hover:underline disabled:text-slate-500 disabled:no-underline flex items-center gap-1 transition-colors"
                    >
                      <RefreshCw className={`w-3 h-3 ${loading ? "animate-spin" : ""}`} />
                      {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : "Resend Code"}
                    </button>
                  </div>
                  <input
                    id="otp-input"
                    type="text"
                    maxLength={6}
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                    required
                    autoFocus
                    placeholder="Enter 6-digit code"
                    className="w-full text-center tracking-[0.4em] font-mono text-lg font-bold rounded-xl border border-slate-700/50 bg-slate-950/60 py-3 text-white outline-none transition-all focus:border-primary/50 focus:ring-1 focus:ring-primary/20 placeholder:text-slate-600 placeholder:tracking-normal placeholder:font-sans placeholder:text-sm"
                  />
                  <span className="text-[10px] text-slate-500 mt-1 block">
                    Valid for 10 minutes.
                  </span>
                </div>

                {/* New Password */}
                <div>
                  <label className="text-xs font-medium text-slate-400 mb-1.5 block">
                    New Password
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                    <input
                      id="new-password-input"
                      type={showPassword ? "text" : "password"}
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      required
                      placeholder="Minimum 6 characters"
                      className="w-full rounded-xl border border-slate-700/50 bg-slate-950/60 pl-11 pr-11 py-3 text-sm text-white outline-none transition-all focus:border-primary/50 focus:ring-1 focus:ring-primary/20 placeholder:text-slate-600"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                {/* Confirm Password */}
                <div>
                  <label className="text-xs font-medium text-slate-400 mb-1.5 block">
                    Confirm New Password
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                    <input
                      id="confirm-password-input"
                      type={showConfirmPassword ? "text" : "password"}
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      required
                      placeholder="Re-enter password"
                      className="w-full rounded-xl border border-slate-700/50 bg-slate-950/60 pl-11 pr-11 py-3 text-sm text-white outline-none transition-all focus:border-primary/50 focus:ring-1 focus:ring-primary/20 placeholder:text-slate-600"
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                      className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
                    >
                      {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <button
                  id="reset-submit-btn"
                  type="submit"
                  disabled={loading}
                  className="w-full rounded-xl bg-gradient-to-r from-primary to-accent px-5 py-3 text-sm font-semibold text-white transition-all hover:opacity-90 hover:shadow-lg hover:shadow-primary/20 disabled:opacity-50 flex items-center justify-center gap-2 glow-primary"
                >
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" /> Updating Password...
                    </>
                  ) : (
                    "Reset & Save Password"
                  )}
                </button>

                <div className="text-center pt-2">
                  <button
                    type="button"
                    onClick={() => {
                      setStep("REQUEST_OTP");
                      setOtp("");
                      setError(null);
                    }}
                    className="text-xs text-slate-400 hover:text-slate-200 transition-colors"
                  >
                    Entered the wrong email? <span className="text-primary-light">Change email</span>
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* STEP 3: SUCCESS */}
          {step === "SUCCESS" && (
            <div className="text-center py-4">
              <motion.div
                initial={{ scale: 0.8, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                className="w-16 h-16 rounded-full bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center mx-auto mb-4 text-emerald-400"
              >
                <CheckCircle2 className="w-8 h-8" />
              </motion.div>
              <h2 className="text-2xl font-bold mb-2">Password Reset Complete!</h2>
              <p className="text-sm text-slate-300 leading-relaxed mb-6">
                Your password has been successfully updated. You can now sign in with your new password.
              </p>

              <button
                id="login-after-reset-btn"
                onClick={() => router.push("/login")}
                className="w-full rounded-xl bg-gradient-to-r from-primary to-accent px-5 py-3 text-sm font-semibold text-white transition-all hover:opacity-90 hover:shadow-lg hover:shadow-primary/20 flex items-center justify-center gap-2 glow-primary"
              >
                Sign In with New Password
              </button>
            </div>
          )}
        </div>
      </motion.div>
    </main>
  );
}
