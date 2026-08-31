// Server Component — 반드시 SSR HTML에 포함되어야 합니다.
// 'use client' 를 붙이거나 dismiss 로직으로 조건부 렌더링하지 마세요.
// 외부 피싱 피드 크롤러(Netcraft, APWG, Google Safe Browsing)는
// 최초 응답 HTML만 읽습니다.

import Link from "next/link";

const REPO_URL = "https://github.com/JaeHyun10-03/J-Bank";

export function DemoDisclaimerBanner() {
  return (
    <div
      role="note"
      aria-label="Portfolio demonstration project notice"
      data-demo-project="true"
      className="w-full border-b border-amber-200 bg-amber-50/70 text-amber-900 dark:border-amber-900/40 dark:bg-amber-950/40 dark:text-amber-100/80"
    >
      <p className="mx-auto max-w-5xl px-4 py-0.5 text-center text-[10px] leading-tight opacity-80">
        Portfolio demo, not a real financial institution — no real money or
        personal data · 포트폴리오 데모, 실제 금융기관 아님
      </p>
    </div>
  );
}

export function DemoDisclaimerFooter() {
  return (
    <footer className="border-t border-border bg-muted/30">
      <p className="mx-auto max-w-5xl px-4 py-3 text-center text-[11px] text-muted-foreground">
        J-Bank — portfolio demo, not a real financial institution. No real
        funds or personal data processed. ·{" "}
        <Link
          href={REPO_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="underline underline-offset-2 hover:no-underline"
        >
          GitHub
        </Link>
      </p>
    </footer>
  );
}
