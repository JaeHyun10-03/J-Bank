"use client";

import { useState } from "react";
import {
  CreditCard,
  Sprout,
  Trophy,
  HelpCircle,
  Ticket,
  PieChart,
  TrendingUp,
  ChevronDown,
} from "lucide-react";
import { MobileScreen } from "@/components/mobile-screen";
import { BottomTabBar } from "@/components/bottom-tab-bar";
import { cn } from "@/lib/utils";

type Category = "생활서비스" | "투자서비스" | "투자투데이";

// ponytail: 서비스 목록은 아직 도메인·API가 없어 피그마 시안 고정 텍스트로 채운다.
const APP_TECH_SERVICES = [
  {
    key: "deposit-reward",
    name: "입출금리워드",
    desc: "매일매일 카드 열고 리워드 받기",
    Icon: CreditCard,
    color: "text-[#4262ff]",
  },
  {
    key: "money-tree",
    name: "돈나무 키우기",
    desc: "매일 돈 버는 게임",
    Icon: Sprout,
    color: "text-[#00a66c]",
  },
  {
    key: "weekly-invest",
    name: "주간 투자왕",
    desc: "주식 뽑고 상금 도전하기",
    Icon: Trophy,
    color: "text-[#f5c542]",
    tag: "추천",
  },
  {
    key: "ai-quiz",
    name: "AI 퀴즈 챌린지",
    desc: "상금과 캐시백 받기",
    Icon: HelpCircle,
    color: "text-[#4262ff]",
  },
  {
    key: "brand-coupon",
    name: "브랜드 쿠폰북",
    desc: "브랜드 찜하고 쿠폰 받기",
    Icon: Ticket,
    color: "text-[#f04452]",
  },
] as const;

const ASSET_SERVICES = [
  {
    key: "spending-check",
    name: "이달의 지출점검",
    desc: "내 소비습관 진단",
    Icon: PieChart,
    color: "text-[#4262ff]",
  },
  {
    key: "cash-flow",
    name: "나의 현금흐름",
    desc: "입출금 분석 리포트",
    Icon: TrendingUp,
    color: "text-[#00a66c]",
  },
] as const;

export default function ServicesPage() {
  const [category, setCategory] = useState<Category>("생활서비스");

  return (
    <MobileScreen>
      <div className="flex w-full items-start px-[24px] pb-[16px] pt-[12px]">
        <p className="text-[24px] font-bold text-[#191f28]">서비스</p>
      </div>

      <div className="flex w-full items-center gap-[8px] px-[24px] pb-[20px]">
        {(["생활서비스", "투자서비스", "투자투데이"] as const).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setCategory(tab)}
            className={cn(
              "inline-flex items-center justify-center rounded-full px-[16px] py-[9px] text-[14px] leading-none",
              tab === category
                ? "bg-[#4262ff] font-semibold text-white"
                : "border border-[#e5e8eb] bg-white font-medium text-[#4e5968]",
            )}
          >
            {tab}
          </button>
        ))}
      </div>

      <div className="flex w-full items-start px-[24px] py-[8px]">
        <p className="text-[20px] font-bold text-[#191f28]">앱테크</p>
      </div>
      <div className="flex w-full flex-col px-[24px]">
        {APP_TECH_SERVICES.map((item, i) => (
          <div
            key={item.key}
            className={cn(
              "flex w-full items-center gap-[14px] py-[18px]",
              i > 0 && "border-t border-[#edf1f7]",
            )}
          >
            <div className="flex size-[46px] shrink-0 items-center justify-center rounded-full bg-[#eef0f6]">
              <item.Icon className={`size-[24px] ${item.color}`} strokeWidth={1.8} />
            </div>
            <div className="flex flex-1 flex-col gap-[4px]">
              <div className="flex items-center gap-[6px]">
                <p className="text-[17px] font-bold text-[#191f28]">{item.name}</p>
                {"tag" in item ? (
                  <span className="rounded-[6px] bg-[#edf1f7] px-[7px] py-[3px] text-[14px] font-semibold text-[#4262ff]">
                    {item.tag}
                  </span>
                ) : null}
              </div>
              <p className="text-[14px] text-[#8b95a5]">{item.desc}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="flex w-full items-center justify-center gap-[5px] py-[16px]">
        <p className="text-[15px] font-medium text-[#6b7684]">더보기</p>
        <ChevronDown className="size-[14px] text-[#6b7684]" strokeWidth={2} />
      </div>

      <div className="h-[8px] w-full bg-[#edf1f7]" />

      <div className="flex w-full items-start px-[24px] pb-[8px] pt-[20px]">
        <p className="text-[20px] font-bold text-[#191f28]">자산관리</p>
      </div>
      <div className="flex w-full flex-col px-[24px]">
        {ASSET_SERVICES.map((item, i) => (
          <div
            key={item.key}
            className={cn(
              "flex w-full items-center gap-[14px] py-[18px]",
              i > 0 && "border-t border-[#edf1f7]",
            )}
          >
            <div className="flex size-[46px] shrink-0 items-center justify-center rounded-full bg-[#eef0f6]">
              <item.Icon className={`size-[24px] ${item.color}`} strokeWidth={1.8} />
            </div>
            <div className="flex flex-1 flex-col gap-[4px]">
              <p className="text-[17px] font-bold text-[#191f28]">{item.name}</p>
              <p className="text-[14px] text-[#8b95a5]">{item.desc}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="h-[20px] w-full" />

      <div className="flex-1" />
      <BottomTabBar active="서비스" />
    </MobileScreen>
  );
}
