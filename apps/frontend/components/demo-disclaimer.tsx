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
      className="sticky top-0 z-50 w-full border-b border-amber-300 bg-amber-50 text-amber-950 dark:border-amber-800/60 dark:bg-amber-950 dark:text-amber-50"
    >
      <div className="mx-auto flex max-w-5xl flex-col items-center gap-1 px-4 py-2 text-center text-xs leading-relaxed sm:text-[13px]">
        <p className="font-semibold">
          This is a portfolio demonstration project. It is not a real financial
          institution.
        </p>
        <p className="text-amber-900/90 dark:text-amber-100/90">
          No real money, payments, or personal data are processed. All balances
          and transactions are synthetic demo data.
        </p>
        <p className="text-amber-900/80 dark:text-amber-100/80">
          본 사이트는 개인 포트폴리오 데모입니다. 실제 금융기관이 아니며, 실제
          자금 이동·결제·개인정보 수집이 없습니다.
        </p>
        <Link
          href={REPO_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-0.5 font-medium underline underline-offset-2 hover:no-underline"
        >
          View the open-source code on GitHub →
        </Link>
      </div>
    </div>
  );
}

export function DemoDisclaimerFooter() {
  return (
    <footer className="border-t border-border bg-muted/30">
      <div className="mx-auto max-w-5xl space-y-2 px-4 py-8 text-center text-xs text-muted-foreground">
        <p className="font-medium text-foreground">
          J-Bank — Portfolio Demonstration Project
        </p>
        <p>
          J-Bank is an open-source software engineering portfolio project that
          demonstrates core-banking architecture patterns. It is{" "}
          <strong className="font-semibold">not</strong> a bank, a payment
          provider, or any regulated financial entity, and it does not claim or
          imply to be one. It processes no real funds and collects no real
          personal or financial data. It does not imitate the branding, name, or
          user interface of any existing financial institution.
        </p>
        <p>
          J-Bank은 코어뱅킹 아키텍처 학습을 목적으로 만든 개인 포트폴리오
          프로젝트입니다. 실제 금융기관이 아니며, 실제 결제·자금 이동·개인정보
          수집 기능이 없습니다. 모든 데이터는 테스트용 합성 데이터입니다.
        </p>
        <p>
          <Link
            href={REPO_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="underline underline-offset-2 hover:no-underline"
          >
            github.com/JaeHyun10-03/J-Bank
          </Link>
        </p>
      </div>
    </footer>
  );
}
