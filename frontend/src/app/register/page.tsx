"use client";

import { useState, useEffect, useRef } from "react";
import { useAuth } from "@/lib/AuthContext";
import Link from "next/link";
import { motion } from "framer-motion";
import { Shield, Mail, Lock, User, Eye, EyeOff, Loader2, CheckCircle } from "lucide-react";

declare global {
  interface Window {
    google?: any;
  }
}

export default function RegisterPage() {
  const { register, googleLogin, isAuthenticated, isLoading: authLoading } = useAuth();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [isGoogleSdkReady, setIsGoogleSdkReady] = useState(false);
  const googleButtonRef = useRef<HTMLDivElement>(null);

  const handleGoogleResponse = async (response: any) => {
    if (!response?.credential) {
      setError("Google sign-in failed: No credential received.");
      return;
    }

    setLoading(true);
    try {
      await googleLogin(response.credential);
    } catch (err: any) {
      setError(err.message || "Google registration failed.");
    } finally {
      setLoading(false);
    }
  };

  const handleDirectGoogleClick = () => {
    if (typeof window !== "undefined" && window.google?.accounts?.id) {
      try {
        window.google.accounts.id.prompt();
      } catch (err) {
        console.warn("Direct Google prompt notice:", err);
      }
    }
  };

  useEffect(() => {
    if (authLoading || isAuthenticated) return;

    let isMounted = true;
    const clientId =
      process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ||
      "158843357869-qo6elt7qsgg2sctcrq6luce708qtejk6.apps.googleusercontent.com";

    const initGoogle = () => {
      if (!isMounted) return;
      if (typeof window === "undefined" || !window.google?.accounts?.id) return;
      if (!clientId) return;

      try {
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: handleGoogleResponse,
          auto_select: false,
          cancel_on_tap_outside: true,
        });

        if (googleButtonRef.current) {
          googleButtonRef.current.innerHTML = "";
          window.google.accounts.id.renderButton(googleButtonRef.current, {
            theme: "outline",
            size: "large",
            width: 320,
            text: "signup_with",
            shape: "rectangular",
            logo_alignment: "left",
          });
          setIsGoogleSdkReady(true);
        }
      } catch (err) {
        console.warn("Google sign-in initialization notice:", err);
      }
    };

    if (window.google?.accounts?.id) {
      initGoogle();
    } else {
      const existingScript = document.querySelector('script[src="https://accounts.google.com/gsi/client"]');
      if (!existingScript) {
        const script = document.createElement("script");
        script.src = "https://accounts.google.com/gsi/client";
        script.async = true;
        script.defer = true;
        script.onload = initGoogle;
        document.body.appendChild(script);
      } else {
        existingScript.addEventListener("load", initGoogle);
      }
    }

    return () => {
      isMounted = false;
    };
  }, [authLoading, isAuthenticated]);

  const passwordStrength = (() => {
    if (password.length === 0) return { level: 0, label: "", color: "" };
    if (password.length < 6) return { level: 1, label: "Weak", color: "bg-red-500" };
    const hasUpper = /[A-Z]/.test(password);
    const hasNumber = /[0-9]/.test(password);
    const hasSpecial = /[!@#$%^&*]/.test(password);
    const score = [password.length >= 8, hasUpper, hasNumber, hasSpecial].filter(Boolean).length;
    if (score <= 1) return { level: 1, label: "Weak", color: "bg-red-500" };
    if (score === 2) return { level: 2, label: "Fair", color: "bg-amber-500" };
    if (score === 3) return { level: 3, label: "Good", color: "bg-blue-500" };
    return { level: 4, label: "Strong", color: "bg-emerald-500" };
  })();

  const handleRegister = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);

    if (password.length < 8) {
      setError("Password must be at least 8 characters long.");
      return;
    }

    setLoading(true);
    try {
      await register(name, email, password);
    } catch (err: any) {
      setError(err.message || "Failed to create account.");
    } finally {
      setLoading(false);
    }
  };

  if (authLoading) {
    return (
      <main className="min-h-screen flex items-center justify-center bg-slate-950">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
      </main>
    );
  }

  if (isAuthenticated) {
    return null;
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-950 text-white px-4 py-10 relative overflow-hidden">
      {/* Background effects */}
      <div className="absolute top-20 right-1/4 w-96 h-96 bg-accent/10 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-20 left-1/4 w-80 h-80 bg-primary/8 rounded-full blur-[100px] pointer-events-none" />

      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6 }}
        className="max-w-md w-full relative z-10"
      >
        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-primary to-accent flex items-center justify-center glow-primary">
            <Shield className="w-7 h-7 text-white" />
          </div>
          <div>
            <span className="text-xl font-bold gradient-text">Vidhura AI</span>
            <p className="text-[10px] text-muted">AI Insurance Intelligence</p>
          </div>
        </div>

        <div className="glass rounded-3xl p-8 glow-primary border border-primary/10">
          <div className="mb-8 text-center">
            <h1 className="text-2xl font-bold mb-2">Create your account</h1>
            <p className="text-sm text-slate-400">
              Join Vidhura AI to unlock AI insurance intelligence
            </p>
          </div>

          {error && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="mb-5 rounded-xl bg-red-500/10 border border-red-500/30 text-red-200 px-4 py-3 text-sm"
            >
              {error}
            </motion.div>
          )}

          <form onSubmit={handleRegister} className="space-y-4">
            <div>
              <label className="text-xs font-medium text-slate-400 mb-1.5 block">
                Full name
              </label>
              <div className="relative">
                <User className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input
                  id="register-name"
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  placeholder="John Doe"
                  className="w-full rounded-xl border border-slate-700/50 bg-slate-950/60 pl-11 pr-4 py-3 text-sm text-white outline-none transition-all focus:border-primary/50 focus:ring-1 focus:ring-primary/20 placeholder:text-slate-600"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-medium text-slate-400 mb-1.5 block">
                Email address
              </label>
              <div className="relative">
                <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input
                  id="register-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  placeholder="you@example.com"
                  className="w-full rounded-xl border border-slate-700/50 bg-slate-950/60 pl-11 pr-4 py-3 text-sm text-white outline-none transition-all focus:border-primary/50 focus:ring-1 focus:ring-primary/20 placeholder:text-slate-600"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-medium text-slate-400 mb-1.5 block">
                Password
              </label>
              <div className="relative">
                <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input
                  id="register-password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  minLength={8}
                  placeholder="Min. 8 characters"
                  className="w-full rounded-xl border border-slate-700/50 bg-slate-950/60 pl-11 pr-11 py-3 text-sm text-white outline-none transition-all focus:border-primary/50 focus:ring-1 focus:ring-primary/20 placeholder:text-slate-600"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
                >
                  {showPassword ? (
                    <EyeOff className="w-4 h-4" />
                  ) : (
                    <Eye className="w-4 h-4" />
                  )}
                </button>
              </div>

              {/* Password strength meter */}
              {password.length > 0 && (
                <div className="mt-2">
                  <div className="flex gap-1">
                    {[1, 2, 3, 4].map((level) => (
                      <div
                        key={level}
                        className={`h-1 flex-1 rounded-full transition-all duration-300 ${
                          level <= passwordStrength.level
                            ? passwordStrength.color
                            : "bg-slate-700/50"
                        }`}
                      />
                    ))}
                  </div>
                  <p className="text-[10px] text-slate-500 mt-1">
                    Password strength:{" "}
                    <span
                      className={
                        passwordStrength.level >= 3
                          ? "text-emerald-400"
                          : passwordStrength.level >= 2
                          ? "text-amber-400"
                          : "text-red-400"
                      }
                    >
                      {passwordStrength.label}
                    </span>
                  </p>
                </div>
              )}
            </div>

            <button
              id="register-submit"
              type="submit"
              disabled={loading}
              className="w-full rounded-xl bg-gradient-to-r from-primary to-accent px-5 py-3 text-sm font-semibold text-white transition-all hover:opacity-90 hover:shadow-lg hover:shadow-primary/20 disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" /> Creating
                  account...
                </>
              ) : (
                "Create account"
              )}
            </button>
          </form>

          <div className="my-6 flex items-center gap-3 text-slate-500">
            <span className="flex-1 h-px bg-slate-700/50" />
            <span className="text-[10px] uppercase tracking-[0.25em] font-medium">
              or continue with
            </span>
            <span className="flex-1 h-px bg-slate-700/50" />
          </div>

          <div className="mb-4 flex flex-col items-center justify-center min-h-[44px]">
            {/* Google Identity Services official button render target */}
            <div
              ref={googleButtonRef}
              id="google-signup-button"
              className="flex justify-center w-full"
            />

            {/* Fallback branded button if GSI is loading or prompt trigger */}
            {!isGoogleSdkReady && (
              <button
                type="button"
                onClick={handleDirectGoogleClick}
                disabled={loading}
                className="w-full flex items-center justify-center gap-3 px-4 py-3 rounded-xl border border-slate-700/60 bg-slate-900/80 hover:bg-slate-800 text-white text-sm font-medium transition-all shadow-sm hover:shadow"
              >
                <svg className="w-4 h-4 flex-shrink-0" viewBox="0 0 24 24">
                  <path
                    fill="#4285F4"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                  />
                </svg>
                Sign up with Google
              </button>
            )}
          </div>

          {/* Benefits list */}
          <div className="mt-6 space-y-2">
            {[
              "Unlimited AI policy analysis",
              "Personalized risk insights",
              "All features — 100% free",
            ].map((benefit) => (
              <div
                key={benefit}
                className="flex items-center gap-2 text-xs text-slate-500"
              >
                <CheckCircle className="w-3.5 h-3.5 text-emerald-500/60" />
                {benefit}
              </div>
            ))}
          </div>

          <p className="mt-5 text-center text-sm text-slate-400">
            Already have an account?{" "}
            <Link
              href="/login"
              className="font-semibold text-primary-light hover:text-white transition-colors"
            >
              Sign in
            </Link>
          </p>
        </div>
      </motion.div>
    </main>
  );
}
