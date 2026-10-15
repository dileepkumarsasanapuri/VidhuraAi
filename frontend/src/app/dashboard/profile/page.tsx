"use client";

import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";
import {
  User, Mail, Phone, Briefcase, Calendar, Shield, Camera,
  Save, CheckCircle, Loader2, Heart, Users, Droplet,
  Edit3, X, AlertCircle
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import { useAuth, AuthUser } from "@/lib/AuthContext";
import { api, UserSettings } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

export default function ProfilePage() {
  const { user, updateUser } = useAuth();
  const toast = useToast();
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [editMode, setEditMode] = useState(false);
  const [passwordSection, setPasswordSection] = useState(false);
  const [passwords, setPasswords] = useState({ current: "", new: "", confirm: "" });
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);

  useEffect(() => {
    async function loadSettings() {
      try {
        const response = await api.getSettings();
        if (response.data) {
          setSettings(response.data);
        }
      } catch (error) {
        console.error("Failed to load settings:", error);
      } finally {
        setIsLoading(false);
      }
    }
    loadSettings();
  }, []);

  const handleSave = async () => {
    if (!settings) return;
    setIsSaving(true);
    try {
      await api.saveSettings(settings);
      // Update the auth context with the new name
      if (settings.profile.name) {
        updateUser({ name: settings.profile.name });
      }
      setSaved(true);
      setEditMode(false);
      toast.success("Profile information updated successfully!", "Profile Saved");
      setTimeout(() => setSaved(false), 3000);
    } catch (error: any) {
      console.error("Failed to save profile:", error);
      toast.error(error.message || "Failed to update profile.", "Save Failed");
    } finally {
      setIsSaving(false);
    }
  };

  const updateProfile = (
    field: keyof UserSettings["profile"],
    value: string | number
  ) => {
    if (!settings) return;
    setSettings({
      ...settings,
      profile: { ...settings.profile, [field]: value },
    });
  };

  const updateHealth = (
    field: keyof UserSettings["healthProfile"],
    value: string | string[]
  ) => {
    if (!settings) return;
    setSettings({
      ...settings,
      healthProfile: { ...settings.healthProfile, [field]: value },
    });
  };

  if (isLoading) {
    return (
      <div className="flex h-screen overflow-hidden">
        <Sidebar activeTab="/dashboard/profile" />
        <main className="flex-1 flex items-center justify-center">
          <Loader2 className="w-8 h-8 text-primary animate-spin" />
        </main>
      </div>
    );
  }

  const initials = user?.name
    ? user.name.split(" ").map((n) => n[0]).slice(0, 2).join("").toUpperCase()
    : "?";

  const memberSince = user?.createdAt
    ? new Date(user.createdAt).toLocaleDateString("en-US", {
        year: "numeric",
        month: "long",
      })
    : "Recently joined";

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar activeTab="/dashboard/profile" />
      <main className="flex-1 overflow-y-auto">
        <header className="sticky top-0 z-30 glass border-b border-border px-6 h-16 flex items-center justify-between">
          <div className="ml-12 lg:ml-0">
            <h1 className="text-lg font-bold">My Profile</h1>
            <p className="text-xs text-muted">
              View and manage your personal information
            </p>
          </div>
          <div className="flex items-center gap-3">
            {saved && (
              <motion.div
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                className="flex items-center gap-1 text-emerald-400 text-sm"
              >
                <CheckCircle className="w-4 h-4" /> Saved
              </motion.div>
            )}
            {editMode ? (
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setEditMode(false)}
                  className="px-4 py-2 text-sm font-medium rounded-xl glass glass-hover"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSave}
                  disabled={isSaving}
                  className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-opacity disabled:opacity-50"
                >
                  {isSaving ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Save className="w-4 h-4" />
                  )}
                  Save
                </button>
              </div>
            ) : (
              <button
                onClick={() => setEditMode(true)}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-xl glass glass-hover"
              >
                <Edit3 className="w-4 h-4" /> Edit Profile
              </button>
            )}
          </div>
        </header>

        <div className="p-6 max-w-4xl mx-auto space-y-6">
          {/* Profile Card */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="glass rounded-2xl p-8 relative overflow-hidden"
          >
            <div className="absolute inset-0 bg-gradient-to-r from-primary/5 to-accent/5 pointer-events-none" />
            <div className="relative z-10 flex flex-col sm:flex-row items-center sm:items-start gap-6">
              {/* Avatar */}
              <div className="relative group">
                {user?.avatar ? (
                  <img
                    src={user.avatar}
                    alt={user?.name || "User"}
                    className="w-24 h-24 rounded-2xl object-cover ring-2 ring-primary/30 shadow-lg shadow-primary/10"
                  />
                ) : (
                  <div className="w-24 h-24 rounded-2xl bg-gradient-to-br from-primary to-accent flex items-center justify-center text-2xl font-bold text-white shadow-lg shadow-primary/10">
                    {initials}
                  </div>
                )}
                <div className="absolute inset-0 rounded-2xl bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center cursor-pointer">
                  <Camera className="w-6 h-6 text-white" />
                </div>
              </div>

              {/* Basic Info */}
              <div className="flex-1 text-center sm:text-left">
                <h2 className="text-2xl font-bold mb-1">
                  {user?.name || "User"}
                </h2>
                <p className="text-sm text-muted mb-3">{user?.email}</p>
                <div className="flex flex-wrap gap-3 justify-center sm:justify-start">
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-full bg-primary/10 text-primary-light border border-primary/20">
                    <Shield className="w-3 h-3" /> Verified Account
                  </span>
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    <Calendar className="w-3 h-3" /> Member since {memberSince}
                  </span>
                  {user?.googleId && (
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20">
                      Google Connected
                    </span>
                  )}
                </div>
              </div>
            </div>
          </motion.div>

          {/* Profile Details */}
          <div className="grid lg:grid-cols-2 gap-6">
            {/* Personal Info */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1 }}
              className="glass rounded-2xl p-6"
            >
              <div className="flex items-center gap-2 mb-5">
                <User className="w-5 h-5 text-primary-light" />
                <h3 className="text-base font-semibold">Personal Information</h3>
              </div>
              <div className="space-y-4">
                <div>
                  <label className="text-xs text-muted mb-1.5 block">
                    Full Name
                  </label>
                  {editMode ? (
                    <input
                      type="text"
                      value={settings?.profile.name || ""}
                      onChange={(e) => updateProfile("name", e.target.value)}
                      className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                    />
                  ) : (
                    <div className="flex items-center gap-2 px-4 py-2.5">
                      <User className="w-4 h-4 text-slate-500" />
                      <span className="text-sm">{settings?.profile.name || "—"}</span>
                    </div>
                  )}
                </div>
                <div>
                  <label className="text-xs text-muted mb-1.5 block">
                    Email Address
                  </label>
                  <div className="flex items-center gap-2 px-4 py-2.5">
                    <Mail className="w-4 h-4 text-slate-500" />
                    <span className="text-sm opacity-70">
                      {settings?.profile.email || user?.email || "—"}
                    </span>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-xs text-muted mb-1.5 block">Age</label>
                    {editMode ? (
                      <input
                        type="number"
                        value={settings?.profile.age || ""}
                        onChange={(e) =>
                          updateProfile("age", parseInt(e.target.value) || 0)
                        }
                        className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                      />
                    ) : (
                      <div className="flex items-center gap-2 px-4 py-2.5">
                        <Calendar className="w-4 h-4 text-slate-500" />
                        <span className="text-sm">
                          {settings?.profile.age || "—"}
                        </span>
                      </div>
                    )}
                  </div>
                  <div>
                    <label className="text-xs text-muted mb-1.5 block">Phone</label>
                    {editMode ? (
                      <input
                        type="text"
                        value={settings?.profile.phone || ""}
                        onChange={(e) => updateProfile("phone", e.target.value)}
                        className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                      />
                    ) : (
                      <div className="flex items-center gap-2 px-4 py-2.5">
                        <Phone className="w-4 h-4 text-slate-500" />
                        <span className="text-sm">
                          {settings?.profile.phone || "—"}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
                <div>
                  <label className="text-xs text-muted mb-1.5 block">
                    Occupation
                  </label>
                  {editMode ? (
                    <input
                      type="text"
                      value={settings?.profile.occupation || ""}
                      onChange={(e) => updateProfile("occupation", e.target.value)}
                      className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                    />
                  ) : (
                    <div className="flex items-center gap-2 px-4 py-2.5">
                      <Briefcase className="w-4 h-4 text-slate-500" />
                      <span className="text-sm">
                        {settings?.profile.occupation || "—"}
                      </span>
                    </div>
                  )}
                </div>
              </div>
            </motion.div>

            {/* Health Profile */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }}
              className="glass rounded-2xl p-6"
            >
              <div className="flex items-center gap-2 mb-5">
                <Heart className="w-5 h-5 text-pink-400" />
                <h3 className="text-base font-semibold">Health Profile</h3>
              </div>
              <div className="space-y-4">
                <div>
                  <label className="text-xs text-muted mb-1.5 block">
                    Family Members Covered
                  </label>
                  {editMode ? (
                    <input
                      type="text"
                      value={settings?.healthProfile.familyMembers || ""}
                      onChange={(e) =>
                        updateHealth("familyMembers", e.target.value)
                      }
                      placeholder="e.g., Spouse + 2 children"
                      className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                    />
                  ) : (
                    <div className="flex items-center gap-2 px-4 py-2.5">
                      <Users className="w-4 h-4 text-slate-500" />
                      <span className="text-sm">
                        {settings?.healthProfile.familyMembers || "—"}
                      </span>
                    </div>
                  )}
                </div>
                <div>
                  <label className="text-xs text-muted mb-1.5 block">
                    Blood Group
                  </label>
                  {editMode ? (
                    <select
                      value={settings?.healthProfile.bloodGroup || ""}
                      onChange={(e) =>
                        updateHealth("bloodGroup", e.target.value)
                      }
                      className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-background border border-transparent focus:border-primary/30 transition-colors"
                    >
                      <option value="">Select</option>
                      {["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"].map(
                        (bg) => (
                          <option key={bg} value={bg}>
                            {bg}
                          </option>
                        )
                      )}
                    </select>
                  ) : (
                    <div className="flex items-center gap-2 px-4 py-2.5">
                      <Droplet className="w-4 h-4 text-red-400" />
                      <span className="text-sm">
                        {settings?.healthProfile.bloodGroup || "—"}
                      </span>
                    </div>
                  )}
                </div>
                <div>
                  <label className="text-xs text-muted mb-1.5 block">
                    Pre-existing Conditions
                  </label>
                  {editMode ? (
                    <input
                      type="text"
                      value={
                        settings?.healthProfile.preExistingConditions?.join(", ") ||
                        ""
                      }
                      onChange={(e) =>
                        updateHealth(
                          "preExistingConditions",
                          e.target.value.split(",").map((s) => s.trim())
                        )
                      }
                      placeholder="Comma separated conditions"
                      className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                    />
                  ) : (
                    <div className="px-4 py-2.5">
                      {settings?.healthProfile.preExistingConditions?.filter(
                        (c) => c
                      ).length ? (
                        <div className="flex flex-wrap gap-1.5">
                          {settings.healthProfile.preExistingConditions
                            .filter((c) => c)
                            .map((c) => (
                              <span
                                key={c}
                                className="px-2 py-0.5 text-xs rounded-full bg-amber-500/10 text-amber-400 border border-amber-500/20"
                              >
                                {c}
                              </span>
                            ))}
                        </div>
                      ) : (
                        <span className="text-sm text-muted">None listed</span>
                      )}
                    </div>
                  )}
                </div>
                <div>
                  <label className="text-xs text-muted mb-1.5 block">
                    Family Medical History
                  </label>
                  {editMode ? (
                    <input
                      type="text"
                      value={
                        settings?.healthProfile.familyHistory?.join(", ") || ""
                      }
                      onChange={(e) =>
                        updateHealth(
                          "familyHistory",
                          e.target.value.split(",").map((s) => s.trim())
                        )
                      }
                      placeholder="Comma separated conditions"
                      className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                    />
                  ) : (
                    <div className="px-4 py-2.5">
                      {settings?.healthProfile.familyHistory?.filter((c) => c)
                        .length ? (
                        <div className="flex flex-wrap gap-1.5">
                          {settings.healthProfile.familyHistory
                            .filter((c) => c)
                            .map((c) => (
                              <span
                                key={c}
                                className="px-2 py-0.5 text-xs rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20"
                              >
                                {c}
                              </span>
                            ))}
                        </div>
                      ) : (
                        <span className="text-sm text-muted">None listed</span>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </motion.div>
          </div>

          {/* Security Section */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3 }}
            className="glass rounded-2xl p-6"
          >
            <div className="flex items-center justify-between mb-5">
              <div className="flex items-center gap-2">
                <Shield className="w-5 h-5 text-emerald-400" />
                <h3 className="text-base font-semibold">Account Security</h3>
              </div>
              {!user?.googleId && (
                <button
                  onClick={() => setPasswordSection(!passwordSection)}
                  className="text-xs text-primary-light hover:underline"
                >
                  {passwordSection ? "Cancel" : "Change Password"}
                </button>
              )}
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between py-3 px-4 glass rounded-xl">
                <div>
                  <p className="text-sm font-medium">Authentication method</p>
                  <p className="text-xs text-muted mt-0.5">
                    {user?.googleId
                      ? "Google Single Sign-On"
                      : "Email & Password"}
                  </p>
                </div>
                <span className="text-xs px-2 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  Active
                </span>
              </div>

              {user?.googleId && (
                <div className="flex items-center justify-between py-3 px-4 glass rounded-xl">
                  <div>
                    <p className="text-sm font-medium">Google Account</p>
                    <p className="text-xs text-muted mt-0.5">
                      Connected via Google OAuth
                    </p>
                  </div>
                  <span className="text-xs px-2 py-1 rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20">
                    Linked
                  </span>
                </div>
              )}

              {passwordSection && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: "auto" }}
                  className="space-y-3 pt-3 border-t border-border"
                >
                  <div>
                    <label className="text-xs text-muted mb-1.5 block">
                      Current Password
                    </label>
                    <input
                      type="password"
                      value={passwords.current}
                      onChange={(e) =>
                        setPasswords({ ...passwords, current: e.target.value })
                      }
                      className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-muted mb-1.5 block">
                      New Password
                    </label>
                    <input
                      type="password"
                      value={passwords.new}
                      onChange={(e) =>
                        setPasswords({ ...passwords, new: e.target.value })
                      }
                      className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-muted mb-1.5 block">
                      Confirm New Password
                    </label>
                    <input
                      type="password"
                      value={passwords.confirm}
                      onChange={(e) =>
                        setPasswords({ ...passwords, confirm: e.target.value })
                      }
                      className="w-full glass rounded-xl px-4 py-2.5 text-sm outline-none bg-transparent focus:border-primary/30 border border-transparent transition-colors"
                    />
                  </div>
                  {passwordError && (
                    <div className="flex items-center gap-2 px-3 py-2 text-xs text-red-300 bg-red-500/10 rounded-lg border border-red-500/20">
                      <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                      {passwordError}
                    </div>
                  )}
                  {passwordSuccess && (
                    <div className="flex items-center gap-2 px-3 py-2 text-xs text-emerald-300 bg-emerald-500/10 rounded-lg border border-emerald-500/20">
                      <CheckCircle className="w-3.5 h-3.5 flex-shrink-0" />
                      Password updated successfully!
                    </div>
                  )}
                  <button
                    onClick={async () => {
                      setPasswordError(null);
                      setPasswordSuccess(false);
                      if (passwords.new !== passwords.confirm) {
                        setPasswordError("New passwords do not match.");
                        return;
                      }
                      if (passwords.new.length < 8) {
                        setPasswordError("New password must be at least 8 characters.");
                        return;
                      }
                      setPasswordSaving(true);
                      try {
                        await api.changePassword(passwords.current, passwords.new);
                        setPasswordSuccess(true);
                        toast.success("Your password has been changed successfully!", "Password Updated");
                        setPasswords({ current: "", new: "", confirm: "" });
                        setTimeout(() => {
                          setPasswordSection(false);
                          setPasswordSuccess(false);
                        }, 2000);
                      } catch (err: any) {
                        setPasswordError(err.message || "Failed to change password.");
                        toast.error(err.message || "Failed to change password.", "Update Failed");
                      } finally {
                        setPasswordSaving(false);
                      }
                    }}
                    disabled={passwordSaving}
                    className="flex items-center gap-2 px-5 py-2.5 text-sm font-medium rounded-xl bg-gradient-to-r from-primary to-accent text-white hover:opacity-90 transition-opacity disabled:opacity-50"
                  >
                    {passwordSaving && <Loader2 className="w-4 h-4 animate-spin" />}
                    Update Password
                  </button>
                </motion.div>
              )}
            </div>
          </motion.div>
        </div>
      </main>
    </div>
  );
}
