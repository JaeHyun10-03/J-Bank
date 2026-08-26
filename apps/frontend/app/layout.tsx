import type { Metadata } from "next";
import "./globals.css";
import { Gothic_A1 } from "next/font/google";
import { cn } from "@/lib/utils";
import { Providers } from "@/components/providers";
import { DemoDisclaimerBanner, DemoDisclaimerFooter } from "@/components/demo-disclaimer";

const gothicA1 = Gothic_A1({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-sans",
});

const SITE_URL = "https://j-bank.site";
const REPO_URL = "https://github.com/JaeHyun10-03/J-Bank";

// description 필드가 핵심 — 검색엔진 스니펫이자, 피싱 피드 스캐너가
// 도메인 성격을 판별할 때 읽는 값. "portfolio demonstration" /
// "not a real financial institution" 문구를 반드시 유지한다.
const DISCLAIMER =
  "J-Bank is an open-source portfolio demonstration project showcasing core banking architecture (double-entry ledger, transactional consistency, idempotency, event-driven design). It is NOT a real financial institution and processes no real money or personal data.";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: "J-Bank — Core Banking System (Portfolio Demo)",
    template: "%s | J-Bank (Portfolio Demo)",
  },
  description: DISCLAIMER,
  applicationName: "J-Bank (Portfolio Demo)",
  authors: [{ name: "JaeHyun Lim", url: "https://github.com/JaeHyun10-03" }],
  creator: "JaeHyun Lim",
  keywords: [
    "portfolio",
    "demo project",
    "core banking",
    "software engineering",
    "open source",
    "spring boot",
    "next.js",
  ],
  robots: {
    index: true,
    follow: true,
  },
  openGraph: {
    type: "website",
    url: SITE_URL,
    siteName: "J-Bank (Portfolio Demo)",
    title: "J-Bank — Core Banking System (Portfolio Demo)",
    description: DISCLAIMER,
  },
  twitter: {
    card: "summary",
    title: "J-Bank — Core Banking System (Portfolio Demo)",
    description: DISCLAIMER,
  },
  other: {
    "demo-project": "true",
    "source-code": REPO_URL,
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className={cn("font-sans", gothicA1.variable)}>
      <body className="antialiased">
        <DemoDisclaimerBanner />
        <Providers>{children}</Providers>
        <DemoDisclaimerFooter />
      </body>
    </html>
  );
}
