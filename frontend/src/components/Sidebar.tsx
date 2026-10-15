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
  Activity,
  AlertTriangle,
  LogOut,
  User,
  Globe,
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
      {/* Mobile toggle */}
      <button
        onClick={() => setMobileOpen(true)}
        className="lg:hidden fixed top-4 left-4 z-50 p-2 glass rounded-xl"
        aria-label="Open menu"
      >
        <Menu className="w-5 h-5" />
      </button>

      {/* Mobile overlay */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/60 backdrop-blur-sm z-40 lg:hidden"
            onClick={() => setMobileOpen(false)}
          />
        )}
      </AnimatePresence>

      {/* Sidebar */}
      <motion.aside
        className={`fixed lg:relative z-50 lg:z-auto flex flex-col h-screen glass border-r border-border transition-all duration-300 ${
          collapsed ? "w-[72px]" : "w-64"
        } ${mobileOpen ? "left-0" : "-left-full lg:left-0"}`}
      >
        {/* Logo */}
        <div className="flex items-center gap-3 px-4 h-16 border-b border-border">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center flex-shrink-0">
            <Shield className="w-5 h-5 text-white" />
          </div>
          {!collapsed && (
            <motion.div
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              className="overflow-hidden"
            >
              <h1 className="text-sm font-bold gradient-text leading-tight">
                Vidhura AI
              </h1>
              <p className="text-[10px] text-muted">AI Insurance Intelligence</p>
            </motion.div>
          )}
          {/* Close button for mobile */}
          <button
            onClick={() => setMobileOpen(false)}
            className="lg:hidden ml-auto p-1 hover:bg-white/5 rounded-lg"
            aria-label="Close menu"
          >
            <X className="w-4 h-4" />
          </button>
          {/* Collapse for desktop */}
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="hidden lg:block ml-auto p-1 hover:bg-white/5 rounded-lg transition-colors"
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          >
            <ChevronRight
              className={`w-4 h-4 transition-transform ${
                collapsed ? "" : "rotate-180"
              }`}
            />
          </button>
        </div>

        {/* Navigation */}
        <nav className="flex-1 py-4 px-2 space-y-1 overflow-y-auto">
          {navItems.map((item) => {
            const isActive = activeTab === item.href;
            const Icon = item.icon;
            return (
              <Link key={item.href} href={item.href} onClick={() => setMobileOpen(false)}>
                <div
                  className={`sidebar-nav-item ${
                    isActive ? "active" : "text-muted hover:text-foreground"
                  } ${collapsed ? "justify-center px-2" : ""}`}
                >
                  <Icon className="w-[18px] h-[18px] flex-shrink-0" />
                  {!collapsed && <span>{item.label}</span>}
                </div>
              </Link>
            );
          })}
        </nav>

        {/* Free badge */}
        {!collapsed && (
          <div className="p-3 m-3 rounded-xl bg-gradient-to-br from-emerald-500/10 to-cyan-500/10 border border-emerald-500/20">
            <div className="flex items-center gap-2 mb-1">
              <Sparkles className="w-4 h-4 text-emerald-400" />
              <span className="text-xs font-semibold text-emerald-400">100% Free</span>
            </div>
            <p className="text-[11px] text-muted">
              All features unlocked. No limits, no payments.
            </p>
          </div>
        )}

        {/* Profile + Logout */}
        <div className="p-3 border-t border-border">
          <Link href="/dashboard/profile" onClick={() => setMobileOpen(false)}>
            <div
              className={`flex items-center gap-3 p-2 rounded-xl hover:bg-white/5 transition-colors cursor-pointer ${
                collapsed ? "justify-center" : ""
              }`}
            >
              {user?.avatar ? (
                <img
                  src={user.avatar}
                  alt={user.name || "User"}
                  className="w-8 h-8 rounded-full object-cover flex-shrink-0 ring-2 ring-primary/30"
                />
              ) : (
                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center text-xs font-bold text-white flex-shrink-0">
                  {initials}
                </div>
              )}
              {!collapsed && (
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

          {/* Logout button */}
          {!collapsed && (
            <button
              onClick={logout}
              className="mt-2 w-full flex items-center gap-2 px-3 py-2 text-xs font-medium text-red-400 hover:bg-red-500/10 rounded-lg transition-colors"
            >
              <LogOut className="w-3.5 h-3.5" />
              Sign out
            </button>
          )}
          {collapsed && (
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
    </>
  );
}
