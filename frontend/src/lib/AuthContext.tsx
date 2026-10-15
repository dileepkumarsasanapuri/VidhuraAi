"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { useRouter, usePathname } from "next/navigation";
import api from "@/lib/api";
import { getStoredToken, getStoredUser, storeAuthSession, clearAuthSession } from "@/lib/auth";

export interface AuthUser {
  id: string;
  _id?: string;
  name: string;
  email: string;
  avatar?: string;
  googleId?: string;
  settings?: Record<string, any>;
  createdAt?: string;
  updatedAt?: string;
}

interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  googleLogin: (credential: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
  updateUser: (updates: Partial<AuthUser>) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const PUBLIC_PATHS = ["/", "/login", "/register", "/forgot-password"];

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState<AuthUser | null>(() => {
    if (typeof window !== "undefined") {
      const storedToken = getStoredToken();
      const storedUser = getStoredUser();
      if (storedToken && storedToken !== "guest-token" && storedUser) {
        return storedUser as AuthUser;
      }
    }
    return null;
  });

  const [token, setToken] = useState<string | null>(() => {
    if (typeof window !== "undefined") {
      const storedToken = getStoredToken();
      if (storedToken && storedToken !== "guest-token") {
        return storedToken;
      }
    }
    return null;
  });

  const [isLoading] = useState(false);

  // Route protection: redirect unauthenticated users away from dashboard
  useEffect(() => {
    if (isLoading) return;

    const isPublic = PUBLIC_PATHS.includes(pathname);
    const isAuthed = !!user && !!token && token !== "guest-token";

    if (!isPublic && !isAuthed) {
      router.replace("/login");
    }

    // If authenticated and on login/register/forgot-password, redirect to dashboard
    if (isAuthed && (pathname === "/login" || pathname === "/register" || pathname === "/forgot-password")) {
      router.replace("/dashboard");
    }
  }, [user, token, isLoading, pathname, router]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await api.login(email, password);
    if (response.success && response.token && response.user) {
      storeAuthSession(response.token, response.user, response.refreshToken);
      setToken(response.token);
      setUser(response.user as AuthUser);
      router.push("/dashboard");
    } else {
      throw new Error("Invalid credentials");
    }
  }, [router]);

  const register = useCallback(async (name: string, email: string, password: string) => {
    const response = await api.register(name, email, password);
    if (response.success && response.token && response.user) {
      storeAuthSession(response.token, response.user, response.refreshToken);
      setToken(response.token);
      setUser(response.user as AuthUser);
      router.push("/dashboard");
    } else {
      throw new Error("Registration failed");
    }
  }, [router]);

  const googleLogin = useCallback(async (credential: string) => {
    const response = await api.googleSignIn(credential);
    if (response.success && response.token && response.user) {
      storeAuthSession(response.token, response.user, response.refreshToken);
      setToken(response.token);
      setUser(response.user as AuthUser);
      router.push("/dashboard");
    } else {
      throw new Error("Google sign-in failed");
    }
  }, [router]);

  const logout = useCallback(() => {
    clearAuthSession();
    setUser(null);
    setToken(null);
    router.push("/login");
  }, [router]);

  const refreshUser = useCallback(async () => {
    try {
      const response = await api.fetchCurrentUser();
      if (response.success && response.data) {
        const updated = response.data as AuthUser;
        setUser(updated);
        const currentToken = getStoredToken();
        if (currentToken) {
          storeAuthSession(currentToken, updated);
        }
      }
    } catch {
      // If refresh fails silently, don't log out — could be network blip
    }
  }, []);

  const updateUser = useCallback((updates: Partial<AuthUser>) => {
    setUser((prev) => {
      if (!prev) return prev;
      const merged = { ...prev, ...updates };
      const currentToken = getStoredToken();
      if (currentToken) storeAuthSession(currentToken, merged);
      return merged;
    });
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user && !!token && token !== "guest-token",
        isLoading,
        login,
        register,
        googleLogin,
        logout,
        refreshUser,
        updateUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
