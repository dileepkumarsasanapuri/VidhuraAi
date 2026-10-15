"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import { Settings as SettingsIcon, User, Bell, Globe, Shield, Key, Palette, LogOut, Moon, Sun, Loader2, ArrowRight } from "lucide-react";
import Sidebar from "@/components/Sidebar";
import { api, UserSettings } from "@/lib/api";
import { useAuth } from "@/lib/AuthContext";
import { useToast } from "@/context/ToastContext";
import { useTheme } from "@/context/ThemeContext";

export default function SettingsPage() {
  const { logout } = useAuth();
  const toast = useToast();
  const { theme, setTheme } = useTheme();
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [initialSettings, setInitialSettings] = useState<UserSettings | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    async function loadSettings() {
      try {
        const response = await api.getSettings();
        if (response.data) {
          setSettings(response.data);
          setInitialSettings(JSON.parse(JSON.stringify(response.data)));
        }
      } catch (error) {
        console.error("Failed to load settings:", error);
      } finally {
        setIsLoading(false);
      }
    }
    loadSettings();
  }, []);

  const handleLogout = () => {
    logout();
  };

  const handleSave = async () => {
    if (!settings) return;
    setIsSaving(true);
    try {
      await api.saveSettings(settings);
      setInitialSettings(JSON.parse(JSON.stringify(settings)));
      toast.success("Settings saved successfully!", "Preferences Updated");
    } catch (error) {
      console.error("Failed to save settings:", error);
      toast.error("Failed to save settings. Please try again.", "Save Failed");
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancel = () => {
    if (initialSettings) {
      setSettings(JSON.parse(JSON.stringify(initialSettings)));
      toast.info("Unsaved changes were reverted.", "Reset");
    }
  };

  const updateProfile = (field: keyof UserSettings["profile"], value: string | number) => {
    if (!settings) return;
    setSettings({ ...settings, profile: { ...settings.profile, [field]: value } });
  };

  const updateHealth = (field: keyof UserSettings["healthProfile"], value: string | string[]) => {
    if (!settings) return;
    setSettings({ ...settings, healthProfile: { ...settings.healthProfile, [field]: value } });
  };

  const updatePref = (field: keyof UserSettings["preferences"], value: boolean | string) => {
    if (!settings) return;
    setSettings({ ...settings, preferences: { ...settings.preferences, [field]: value } });
  };

  if (isLoading) return <div className="flex h-screen items-center justify-center text-white">Loading...</div>;
  if (!settings) return <div className="flex h-screen items-center justify-center text-white">Failed to load settings</div>;

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/settings" />
      <main className="flex-1 overflow-y-auto">
        <header className="sticky top-0 z-30 glass border-b border-border px-6 h-16 flex items-center justify-between">
          <div className="ml-12 lg:ml-0">
            <h1 className="text-lg font-bold">Profile & Settings</h1>
            <p className="text-xs text-muted">Manage your profile & preferences</p>
          </div>
          <button onClick={handleLogout} className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-xl border border-red-500/50 text-red-400 hover:bg-red-500/10 transition-colors">
            <LogOut className="w-4 h-4" /> Log Out
          </button>
        </header>

        <div className="p-6 max-w-3xl mx-auto space-y-6">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="glass rounded-2xl p-6">
            <div className="flex items-center gap-2 mb-4">
              <User className="w-5 h-5 text-primary-light" />
              <h3 className="text-base font-semibold">Profile</h3>
            </div>
            <div className="space-y-4">
              <div>
                <label className="text-xs text-muted mb-1 block">Full Name</label>
                <input type="text" value={settings.profile.name} onChange={(e) => updateProfile('name', e.target.value)}
                  className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors" />
              </div>
              <div>
                <label className="text-xs text-muted mb-1 block">Email</label>
                <input type="email" value={settings.profile.email} onChange={(e) => updateProfile('email', e.target.value)} disabled
                  className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent opacity-70" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs text-muted mb-1 block">Age</label>
                  <input type="number" value={settings.profile.age} onChange={(e) => updateProfile('age', parseInt(e.target.value) || 0)}
                    className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors" />
                </div>
                <div>
                  <label className="text-xs text-muted mb-1 block">Phone</label>
                  <input type="text" value={settings.profile.phone} onChange={(e) => updateProfile('phone', e.target.value)}
                    className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors" />
                </div>
              </div>
              <div>
                <label className="text-xs text-muted mb-1 block">Occupation</label>
                <input type="text" value={settings.profile.occupation} onChange={(e) => updateProfile('occupation', e.target.value)}
                  className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors" />
              </div>
            </div>
          </motion.div>

          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="glass rounded-2xl p-6">
            <div className="flex items-center gap-2 mb-4">
              <Shield className="w-5 h-5 text-primary-light" />
              <h3 className="text-base font-semibold">Health Profile</h3>
            </div>
            <div className="space-y-4">
              <div>
                <label className="text-xs text-muted mb-1 block">Family Members Covered</label>
                <input type="text" value={settings.healthProfile.familyMembers} onChange={(e) => updateHealth('familyMembers', e.target.value)} placeholder="e.g., Spouse + 2 children"
                  className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors" />
              </div>
              <div>
                <label className="text-xs text-muted mb-1 block">Pre-existing Conditions (comma separated)</label>
                <input type="text" value={settings.healthProfile.preExistingConditions?.join(', ')} onChange={(e) => updateHealth('preExistingConditions', e.target.value.split(',').map(s => s.trim()))}
                  className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors" />
              </div>
              <div>
                <label className="text-xs text-muted mb-1 block">Family History (comma separated)</label>
                <input type="text" value={settings.healthProfile.familyHistory?.join(', ')} onChange={(e) => updateHealth('familyHistory', e.target.value.split(',').map(s => s.trim()))}
                  className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors" />
              </div>
            </div>
          </motion.div>

          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }} className="glass rounded-2xl p-6">
            <div className="flex items-center gap-2 mb-4">
              <SettingsIcon className="w-5 h-5 text-primary-light" />
              <h3 className="text-base font-semibold">Preferences</h3>
            </div>
            <div className="space-y-4">
              <div className="flex items-center justify-between py-2">
                <div className="flex items-center gap-3">
                  <Bell className="w-4 h-4 text-muted" />
                  <div>
                    <p className="text-sm font-medium">Email Notifications</p>
                    <p className="text-xs text-muted">Policy expiry & risk alerts</p>
                  </div>
                </div>
                <div onClick={() => updatePref('notifications', !settings.preferences.notifications)} className={`w-11 h-6 rounded-full relative cursor-pointer transition-colors ${settings.preferences.notifications ? "bg-primary" : "bg-white/10"}`}>
                  <div className={`w-5 h-5 rounded-full bg-white absolute top-0.5 transition-all ${settings.preferences.notifications ? "left-[22px]" : "left-0.5"}`} />
                </div>
              </div>
              <div className="flex items-center justify-between py-2">
                <div className="flex items-center gap-3">
                  <Globe className="w-4 h-4 text-muted" />
                  <div>
                    <p className="text-sm font-medium">Voice AI Language</p>
                    <p className="text-xs text-muted">Default language for voice assistant</p>
                  </div>
                </div>
                <select value={settings.preferences.language} onChange={(e) => updatePref('language', e.target.value)} className="glass rounded-lg px-3 py-1.5 text-xs outline-none bg-background">
                  <option>English</option><option>Hindi</option><option>Telugu</option><option>Tamil</option><option>Bengali</option>
                </select>
              </div>
              <div className="flex items-center justify-between py-2">
                <div className="flex items-center gap-3">
                  <Key className="w-4 h-4 text-muted" />
                  <div>
                    <p className="text-sm font-medium">Two-Factor Auth</p>
                    <p className="text-xs text-muted">Extra security for your account</p>
                  </div>
                </div>
                <div onClick={() => updatePref('twoFactorAuth', !settings.preferences.twoFactorAuth)} className={`w-11 h-6 rounded-full relative cursor-pointer transition-colors ${settings.preferences.twoFactorAuth ? "bg-primary" : "bg-white/10"}`}>
                  <div className={`w-5 h-5 rounded-full bg-white absolute top-0.5 transition-all ${settings.preferences.twoFactorAuth ? "left-[22px]" : "left-0.5"}`} />
                </div>
              </div>
              <div className="flex items-center justify-between py-2 border-t border-white/5">
                <div className="flex items-center gap-3">
                  {theme === "dark" ? <Moon className="w-4 h-4 text-primary-light" /> : <Sun className="w-4 h-4 text-amber-400" />}
                  <div>
                    <p className="text-sm font-medium">Dark Theme</p>
                    <p className="text-xs text-muted">Switch between dark obsidian and sleek light theme</p>
                  </div>
                </div>
                <div onClick={() => {
                  const nextTheme = theme === "dark" ? "light" : "dark";
                  setTheme(nextTheme);
                  updatePref('darkMode', nextTheme === "dark");
                }} className={`w-11 h-6 rounded-full relative cursor-pointer transition-colors ${theme === "dark" ? "bg-primary" : "bg-white/10"}`}>
                  <div className={`w-5 h-5 rounded-full bg-white absolute top-0.5 transition-all ${theme === "dark" ? "left-[22px]" : "left-0.5"}`} />
                </div>
              </div>
            </div>
          </motion.div>

          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }} className="flex flex-col sm:flex-row items-center justify-between gap-4 pb-8">
            <Link
              href="/dashboard/profile"
              className="text-xs text-primary-light hover:underline flex items-center gap-1.5"
            >
              <User className="w-4 h-4" /> Go to Full Profile & Security Settings <ArrowRight className="w-3.5 h-3.5" />
            </Link>

            <div className="flex items-center gap-3 w-full sm:w-auto justify-end">
              <button
                type="button"
                onClick={handleCancel}
                disabled={isSaving}
                className="px-6 py-2.5 text-sm font-medium rounded-xl glass glass-hover disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleSave}
                disabled={isSaving}
                className="inline-flex items-center gap-2 px-6 py-2.5 text-sm font-medium rounded-xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-opacity disabled:opacity-50"
              >
                {isSaving && <Loader2 className="w-4 h-4 animate-spin" />}
                Save Changes
              </button>
            </div>
          </motion.div>
        </div>
      </main>
    </div>
  );
}
