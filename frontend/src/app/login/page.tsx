"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/AuthContext";
import Link from "next/link";
import { motion } from "framer-motion";
import { Shield, Mail, Lock, Eye, EyeOff, Loader2 } from "lucide-react";

declare global {
  interface Window {
    google?: any;
  }
}

export default function LoginPage() {
  const { login, googleLogin, isAuthenticated, isLoading: authLoading } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleLogin = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await login(email, password);
    } catch (err: any) {
      setError(err.message || "Failed to sign in. Please check your credentials.");
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleResponse = async (response: any) => {
    if (!response?.credential) {
      setError("Google sign-in failed.");
      return;
    }

    setLoading(true);
    try {
      await googleLogin(response.credential);
    } catch (err: any) {
      setError(err.message || "Google sign-in failed.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const loadGoogleSdk = () => {
      if (typeof window === "undefined") return;
      if (window.google?.accounts?.id) {
        return initGoogle();
      }

      const script = document.createElement("script");
      script.src = "https://accounts.google.com/gsi/client";
      script.async = true;
      script.defer = true;
      script.onload = initGoogle;
      document.body.appendChild(script);

      return () => {
        document.body.removeChild(script);
      };
    };

    const initGoogle = () => {
      if (typeof window === "undefined" || !window.google?.accounts?.id) return;
      const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
      if (!clientId) return;

      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: handleGoogleResponse,
      });
      window.google.accounts.id.renderButton(
        document.getElementById("google-signin-button"),
        { theme: "outline", size: "large", width: "320" }
      );
    };

    loadGoogleSdk();
  }, []);

  // Show loading while auth state hydrates
  if (authLoading) {
    return (
      <main className="min-h-screen flex items-center justify-center bg-slate-950">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
      </main>
    );
  }

  // If already authenticated, AuthContext will redirect — show nothing
  if (isAuthenticated) {
    return null;
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-950 text-white px-4 py-10 relative overflow-hidden">
      {/* Background effects */}
      <div className="absolute top-20 left-1/4 w-96 h-96 bg-primary/10 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-20 right-1/4 w-80 h-80 bg-accent/8 rounded-full blur-[100px] pointer-events-none" />

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
            <h1 className="text-2xl font-bold mb-2">Welcome back</h1>
            <p className="text-sm text-slate-400">
              Sign in to your Vidhura AI account
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

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="text-xs font-medium text-slate-400 mb-1.5 block">
                Email address
              </label>
              <div className="relative">
                <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input
                  id="login-email"
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
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-medium text-slate-400 block">
                  Password
                </label>
                <Link
                  href="/forgot-password"
                  className="text-xs text-primary-light hover:text-white transition-colors"
                >
                  Forgot password?
                </Link>
              </div>
              <div className="relative">
                <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input
                  id="login-password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  placeholder="••••••••"
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
            </div>

            <button
              id="login-submit"
              type="submit"
              disabled={loading}
              className="w-full rounded-xl bg-gradient-to-r from-primary to-accent px-5 py-3 text-sm font-semibold text-white transition-all hover:opacity-90 hover:shadow-lg hover:shadow-primary/20 disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" /> Signing in...
                </>
              ) : (
                "Sign in"
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

          <div
            id="google-signin-button"
            className="mb-4 flex flex-col items-center justify-center"
          >
            {!process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID && (
              <p className="text-xs text-amber-500/80 mb-2">
                Google Sign-In is not configured. Use email to sign in.
              </p>
            )}
          </div>

          <p className="mt-5 text-center text-sm text-slate-400">
            Don&apos;t have an account?{" "}
            <Link
              href="/register"
              className="font-semibold text-primary-light hover:text-white transition-colors"
            >
              Create one
            </Link>
          </p>
        </div>
      </motion.div>
    </main>
  );
}
