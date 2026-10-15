"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  Shield,
  FileText,
  BarChart3,
  MessageSquare,
  GitCompare,
  Upload,
  Settings,
  Menu,
  X,
  ChevronRight,
  Sparkles,
  Home,
  AlertTriangle,
  LogOut,
  User,
  Globe,
  Plus,
} from "lucide-react";
import { useAuth } from "@/lib/AuthContext";

const navItems = [
  { href: "/dashboard", label: "Dashboard", icon: Home },
  { href: "/dashboard/upload", label: "Upload Policy", icon: Upload },
  { href: "/dashboard/policies", label: "My Policies", icon: FileText },
  { href: "/dashboard/scenario", label: "ScenarioGPT", icon: MessageSquare },
  { href: "/dashboard/compare", label: "CompareWise", icon: GitCompare },
  { href: "/dashboard/claims", label: "ClaimShield", icon: AlertTriangle },
  { href: "/dashboard/voice", label: "Voice AI", icon: Globe },
  { href: "/dashboard/analytics", label: "TrustScore", icon: BarChart3 },
  { href: "/dashboard/profile", label: "My Profile", icon: User },
  { href: "/dashboard/settings", label: "Settings", icon: Settings },
];

export default function Sidebar({ activeTab }: { activeTab: string }) {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const { user, logout } = useAuth();

  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth >= 1024) setMobileOpen(false);
    };
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  // Lock body scroll when mobile drawer is open
  useEffect(() => {
    if (mobileOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [mobileOpen]);

  const initials = user?.name
    ? user.name
        .split(" ")
        .map((n) => n[0])
        .slice(0, 2)
        .join("")
        .toUpperCase()
    : "?";

  return (
    <>
      {/* Mobile Top Toggle (only when drawer is closed) */}
      {!mobileOpen && (
        <button
          onClick={() => setMobileOpen(true)}
          className="lg:hidden fixed top-3.5 left-3.5 z-40 p-2 glass rounded-xl border border-white/10 hover:bg-white/10 transition-colors shadow-lg shadow-black/20"
          aria-label="Open navigation menu"
        >
          <Menu className="w-5 h-5 text-foreground" />
        </button>
      )}

      {/* Mobile Overlay */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 lg:hidden"
            onClick={() => setMobileOpen(false)}
          />
        )}
      </AnimatePresence>

      {/* Mobile Drawer & Desktop Sidebar */}
      <motion.aside
        className={`fixed lg:relative z-50 flex flex-col h-screen glass border-r border-border transition-all duration-300 max-w-[85vw] ${
          collapsed ? "lg:w-[72px]" : "lg:w-64"
        } w-72 ${
          mobileOpen ? "left-0 shadow-2xl" : "-left-full lg:left-0"
        }`}
      >
        {/* Logo / Header */}
        <div className="flex items-center gap-3 px-4 h-16 border-b border-border flex-shrink-0">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center flex-shrink-0 shadow-md shadow-primary/20">
            <Shield className="w-5 h-5 text-white" />
          </div>
          {(!collapsed || mobileOpen) && (
            <div className="overflow-hidden flex-1">
              <h1 className="text-sm font-bold gradient-text leading-tight">
                Vidhura AI
              </h1>
              <p className="text-[10px] text-muted truncate">Insurance Intelligence</p>
            </div>
          )}
          {/* Close button for mobile */}
          <button
            onClick={() => setMobileOpen(false)}
            className="lg:hidden p-1.5 hover:bg-white/10 rounded-lg text-muted hover:text-white transition-colors"
            aria-label="Close menu"
          >
            <X className="w-5 h-5" />
          </button>
          {/* Collapse toggle for desktop */}
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="hidden lg:block ml-auto p-1 hover:bg-white/5 rounded-lg transition-colors text-muted hover:text-white"
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          >
            <ChevronRight
              className={`w-4 h-4 transition-transform ${
                collapsed ? "" : "rotate-180"
              }`}
            />
          </button>
        </div>

        {/* Navigation list */}
        <nav className="flex-1 py-3 px-2 space-y-1 overflow-y-auto overscroll-contain">
          {navItems.map((item) => {
            const isActive = activeTab === item.href;
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMobileOpen(false)}
              >
                <div
                  className={`sidebar-nav-item min-h-[44px] flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 ${
                    isActive
                      ? "bg-primary/15 text-primary-light font-semibold shadow-sm"
                      : "text-muted hover:text-foreground hover:bg-white/5"
                  } ${collapsed && !mobileOpen ? "justify-center px-2" : ""}`}
                >
                  <Icon className="w-[18px] h-[18px] flex-shrink-0" />
                  {(!collapsed || mobileOpen) && <span>{item.label}</span>}
                </div>
              </Link>
            );
          })}
        </nav>

        {/* 100% Free badge */}
        {(!collapsed || mobileOpen) && (
          <div className="p-3 m-3 rounded-xl bg-gradient-to-br from-emerald-500/10 to-cyan-500/10 border border-emerald-500/20 flex-shrink-0">
            <div className="flex items-center gap-2 mb-1">
              <Sparkles className="w-4 h-4 text-emerald-400 flex-shrink-0" />
              <span className="text-xs font-semibold text-emerald-400">100% Free & Open</span>
            </div>
            <p className="text-[11px] text-muted leading-tight">
              All features unlocked. No limits, no payment required.
            </p>
          </div>
        )}

        {/* User Profile & Sign Out Footer */}
        <div className="p-3 border-t border-border flex-shrink-0 bg-background/40">
          <Link href="/dashboard/profile" onClick={() => setMobileOpen(false)}>
            <div
              className={`flex items-center gap-3 p-2 rounded-xl hover:bg-white/5 transition-colors cursor-pointer ${
                collapsed && !mobileOpen ? "justify-center" : ""
              }`}
            >
              {user?.avatar ? (
                <img
                  src={user.avatar}
                  alt={user.name || "User"}
                  className="w-8 h-8 rounded-full object-cover flex-shrink-0 ring-2 ring-primary/30"
                />
              ) : (
                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center text-xs font-bold text-white flex-shrink-0 shadow-sm">
                  {initials}
                </div>
              )}
              {(!collapsed || mobileOpen) && (
                <div className="overflow-hidden flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">
                    {user?.name || "User"}
                  </p>
                  <p className="text-[11px] text-muted truncate">
                    {user?.email || ""}
                  </p>
                </div>
              )}
            </div>
          </Link>

          {(!collapsed || mobileOpen) && (
            <button
              onClick={logout}
              className="mt-2 w-full flex items-center gap-2 px-3 py-2 text-xs font-medium text-red-400 hover:bg-red-500/10 rounded-lg transition-colors"
            >
              <LogOut className="w-3.5 h-3.5" />
              Sign out
            </button>
          )}
          {collapsed && !mobileOpen && (
            <button
              onClick={logout}
              className="mt-2 w-full flex items-center justify-center p-2 text-red-400 hover:bg-red-500/10 rounded-lg transition-colors"
              title="Sign out"
            >
              <LogOut className="w-4 h-4" />
            </button>
          )}
        </div>
      </motion.aside>

      {/* ========================================================================= */}
      {/* Mobile Bottom Navigation Bar (Visible only on mobile/tablet < lg)        */}
      {/* Thumb-friendly, accessible quick links to the most common actions         */}
      {/* ========================================================================= */}
      <nav
        aria-label="Mobile Bottom Navigation"
        className="lg:hidden fixed bottom-0 left-0 right-0 z-40 bg-slate-950/95 backdrop-blur-xl border-t border-white/10 px-3 py-1 flex items-center justify-around safe-bottom"
      >
        {/* Tab 1: Home */}
        <Link
          href="/dashboard"
          className={`flex flex-col items-center justify-center py-1.5 px-2 rounded-xl transition-colors ${
            activeTab === "/dashboard"
              ? "text-primary-light font-semibold"
              : "text-muted hover:text-foreground"
          }`}
        >
          <Home className="w-5 h-5 mb-0.5" />
          <span className="text-[10px] tracking-tight">Home</span>
        </Link>

        {/* Tab 2: Policies */}
        <Link
          href="/dashboard/policies"
          className={`flex flex-col items-center justify-center py-1.5 px-2 rounded-xl transition-colors ${
            activeTab === "/dashboard/policies"
              ? "text-primary-light font-semibold"
              : "text-muted hover:text-foreground"
          }`}
        >
          <FileText className="w-5 h-5 mb-0.5" />
          <span className="text-[10px] tracking-tight">Policies</span>
        </Link>

        {/* Tab 3: Raised Upload Action Button */}
        <Link
          href="/dashboard/upload"
          className="flex flex-col items-center justify-center -mt-5 group"
          aria-label="Upload New Policy"
        >
          <div className="w-12 h-12 rounded-full bg-gradient-to-br from-primary to-accent shadow-lg shadow-primary/40 flex items-center justify-center text-white border-2 border-slate-950 group-active:scale-95 transition-transform">
            <Plus className="w-6 h-6 stroke-[2.5]" />
          </div>
          <span className="text-[10px] font-medium text-foreground mt-0.5">Upload</span>
        </Link>

        {/* Tab 4: Voice AI */}
        <Link
          href="/dashboard/voice"
          className={`flex flex-col items-center justify-center py-1.5 px-2 rounded-xl transition-colors ${
            activeTab === "/dashboard/voice"
              ? "text-primary-light font-semibold"
              : "text-muted hover:text-foreground"
          }`}
        >
          <Globe className="w-5 h-5 mb-0.5" />
          <span className="text-[10px] tracking-tight">Voice AI</span>
        </Link>

        {/* Tab 5: More / Menu Drawer Trigger */}
        <button
          onClick={() => setMobileOpen(true)}
          className={`flex flex-col items-center justify-center py-1.5 px-2 rounded-xl transition-colors ${
            mobileOpen ? "text-primary-light font-semibold" : "text-muted hover:text-foreground"
          }`}
          aria-label="Open full menu"
        >
          <Menu className="w-5 h-5 mb-0.5" />
          <span className="text-[10px] tracking-tight">More</span>
        </button>
      </nav>
    </>
  );
}
