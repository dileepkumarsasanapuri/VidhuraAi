import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import Providers from "@/components/Providers";

const inter = Inter({
  variable: "--font-geist-sans",
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700", "800"],
});

export const metadata: Metadata = {
  title: "Vidhura AI — Understand Your Insurance Before It Understands You",
  description:
    "AI-powered insurance intelligence platform that transforms complex insurance policies into personalized, scenario-based, multilingual, interactive explanations.",
  keywords: [
    "insurance",
    "AI",
    "policy analysis",
    "claim prediction",
    "insurance comparison",
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col bg-mesh">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
