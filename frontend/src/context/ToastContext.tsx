"use client";

import React, { createContext, useContext, useState, useCallback } from "react";
import { CheckCircle2, AlertCircle, AlertTriangle, Info, X } from "lucide-react";

export type ToastType = "success" | "error" | "warning" | "info";

export interface ToastItem {
  id: string;
  type: ToastType;
  message: string;
  title?: string;
  duration?: number;
}

interface ToastContextType {
  toast: {
    success: (message: string, title?: string) => void;
    error: (message: string, title?: string) => void;
    warning: (message: string, title?: string) => void;
    info: (message: string, title?: string) => void;
    show: (item: Omit<ToastItem, "id">) => void;
  };
  removeToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const show = useCallback(
    ({ type, message, title, duration = 4500 }: Omit<ToastItem, "id">) => {
      const id = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
      setToasts((prev) => [...prev.slice(-4), { id, type, message, title, duration }]);

      if (duration > 0) {
        setTimeout(() => {
          removeToast(id);
        }, duration);
      }
    },
    [removeToast]
  );

  const toast = {
    success: (message: string, title?: string) => show({ type: "success", message, title }),
    error: (message: string, title?: string) => show({ type: "error", message, title }),
    warning: (message: string, title?: string) => show({ type: "warning", message, title }),
    info: (message: string, title?: string) => show({ type: "info", message, title }),
    show,
  };

  return (
    <ToastContext.Provider value={{ toast, removeToast }}>
      {children}
      {/* Toast Notification Container */}
      <div
        aria-live="polite"
        className="fixed bottom-6 right-6 z-50 flex flex-col gap-3 max-w-md w-full pointer-events-none px-4 sm:px-0"
      >
        {toasts.map((item) => (
          <div
            key={item.id}
            role="alert"
            className={`pointer-events-auto flex items-start gap-3 p-4 rounded-xl border shadow-2xl backdrop-blur-xl transition-all duration-300 animate-in slide-in-from-bottom-5 fade-in ${
              item.type === "success"
                ? "bg-emerald-950/80 border-emerald-500/40 text-emerald-200"
                : item.type === "error"
                ? "bg-rose-950/80 border-rose-500/40 text-rose-200"
                : item.type === "warning"
                ? "bg-amber-950/80 border-amber-500/40 text-amber-200"
                : "bg-cyan-950/80 border-cyan-500/40 text-cyan-200"
            }`}
          >
            <div className="shrink-0 mt-0.5">
              {item.type === "success" && <CheckCircle2 className="w-5 h-5 text-emerald-400" />}
              {item.type === "error" && <AlertCircle className="w-5 h-5 text-rose-400" />}
              {item.type === "warning" && <AlertTriangle className="w-5 h-5 text-amber-400" />}
              {item.type === "info" && <Info className="w-5 h-5 text-cyan-400" />}
            </div>
            <div className="flex-1 min-w-0">
              {item.title && (
                <p className="font-semibold text-sm mb-0.5 text-white/95">{item.title}</p>
              )}
              <p className="text-sm leading-snug break-words text-white/85">{item.message}</p>
            </div>
            <button
              onClick={() => removeToast(item.id)}
              aria-label="Dismiss notification"
              className="shrink-0 text-white/40 hover:text-white/90 transition-colors p-1 rounded-md"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return context.toast;
}
