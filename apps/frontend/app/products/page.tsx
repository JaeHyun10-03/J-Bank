"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { MobileScreen } from "@/components/mobile-screen";
import { BottomTabBar } from "@/components/bottom-tab-bar";
import { cn } from "@/lib/utils";
import type { components } from "@/types/api";

type ApiResponsePageResponseProductSummaryResponse =
  components["schemas"]["ApiResponsePageResponseProductSummaryResponse"];

type Category = "예적금" | "대출" | "카드";

// 마케팅 카피는 GET /api/v1/products 응답에 없어 productCode 기준으로 프론트에서만 붙인다.
const SAVINGS_COPY: Record<string, string> = {
  "j-kids": "우리 아이 든든한 미래 준비",
  "j-farm": "매달 이자가 굴러오는 적금",
};

// ponytail: 대출·카드는 아직 도메인/API가 없어(README 핵심 기능 표 참고) 피그마 시안 그대로의
// 고정 텍스트로만 채운다.

const LOAN_PRODUCT = {
  code: "credit-loan",
  name: "신용대출 한번에 알아보기",
  desc: "제이뱅크부터 제휴사까지, 내게 딱 맞는 신용대출을 한번에!",
  badge: "이벤트",
  rateRange: "연 5.00~15.00%",
  maxAmount: "최대 3억원",
};

const CARD_PRODUCT = {
  code: "one-checkcard",
  name: "ONE 체크카드(그린패스)",
  desc: "내맘대로 골라받는 역대급 혜택",
};

export default function ProductListPage() {
  const [category, setCategory] = useState<Category>("예적금");

  const productsQuery = useQuery({
    queryKey: ["products"],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponsePageResponseProductSummaryResponse>(
        "/products",
        { params: { page: 0, size: 20 } },
      );
      return response.data.data?.content ?? [];
    },
  });

  return (
    <MobileScreen>
      <div className="flex w-full items-start px-[24px] pb-[16px] pt-[12px]">
        <p className="text-[24px] font-bold text-[#191f28]">상품</p>
      </div>

      <div className="flex w-full items-center gap-[8px] px-[24px] pb-[20px]">
        {(["예적금", "대출", "카드"] as const).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setCategory(tab)}
            className={cn(
              "inline-flex items-center justify-center rounded-full px-[16px] py-[9px] text-[14px] leading-none",
              tab === category
                ? "bg-[#4262ff] font-semibold text-white"
                : "bg-[#edf1f7] font-medium text-[#6b7684]",
            )}
          >
            {tab}
          </button>
        ))}
      </div>

      {category === "예적금" ? (
        <>
          <div className="w-full px-[24px] pb-[8px] pt-[16px]">
            <p className="text-[20px] font-bold text-[#191f28]">저축</p>
          </div>
          <div className="flex w-full flex-col px-[24px]">
            {(productsQuery.data ?? []).map((product, i) => (
              <Link
                key={product.productCode}
                href={`/products/${product.productCode}`}
                className={cn(
                  "flex w-full items-start justify-between py-[20px]",
                  i > 0 && "border-t border-[#edf1f7]",
                )}
              >
                <div className="flex flex-1 flex-col gap-[6px]">
                  <p className="text-[16px] font-bold text-[#191f28]">{product.productName}</p>
                  <p className="text-[13px] text-[#8b95a5]">
                    {SAVINGS_COPY[product.productCode ?? ""] ?? ""}
                  </p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-[2px]">
                  <div className="flex items-center gap-[4px]">
                    <p className="text-[12px] text-[#8b95a5]">연</p>
                    <p className="text-[18px] font-bold text-[#4262ff]">
                      {(Number(product.interestRate ?? 0) * 100).toFixed(2)}%
                    </p>
                  </div>
                  <p className="text-[11px] text-[#b0b8c4]">
                    ({product.contractPeriodMonths}개월, 세전)
                  </p>
                </div>
              </Link>
            ))}
          </div>
        </>
      ) : category === "대출" ? (
        <>
          <div className="w-full px-[24px] pb-[8px] pt-[16px]">
            <p className="text-[20px] font-bold text-[#191f28]">신용대출</p>
          </div>
          <div className="flex w-full flex-col px-[24px]">
            <Link
              href={`/products/${LOAN_PRODUCT.code}`}
              className="flex w-full items-start justify-between py-[20px]"
            >
              <div className="flex flex-1 flex-col gap-[6px]">
                <div className="flex items-center gap-[6px]">
                  <p className="text-[16px] font-bold text-[#191f28]">{LOAN_PRODUCT.name}</p>
                  <span className="size-[6px] rounded-full bg-[#4262ff]" />
                  <span className="inline-flex items-center rounded-[4px] bg-[#eef0ff] px-[6px] py-[2px] text-[10px] leading-none font-bold text-[#4262ff]">
                    {LOAN_PRODUCT.badge}
                  </span>
                </div>
                <p className="text-[13px] text-[#8b95a5]">{LOAN_PRODUCT.desc}</p>
              </div>
              <div className="flex shrink-0 flex-col items-end gap-[2px]">
                <p className="text-[17px] font-bold text-[#4262ff]">{LOAN_PRODUCT.rateRange}</p>
                <p className="text-[13px] text-[#8b95a5]">{LOAN_PRODUCT.maxAmount}</p>
              </div>
            </Link>
          </div>
        </>
      ) : (
        <>
          <div className="w-full px-[24px] py-[8px]">
            <p className="text-[20px] font-bold text-[#191f28]">체크카드</p>
          </div>
          <div className="flex w-full flex-col px-[24px]">
            {[0, 1, 2].map((i) => (
              <Link
                key={i}
                href={`/products/${CARD_PRODUCT.code}`}
                className="flex w-full items-center gap-[14px] py-[16px]"
              >
                <div className="h-[40px] w-[56px] shrink-0 rounded-[6px] bg-gradient-to-br from-[#2a2a2e] to-[#050506]" />
                <div className="flex flex-col gap-[4px]">
                  <p className="text-[16px] font-bold text-[#191f28]">{CARD_PRODUCT.name}</p>
                  <p className="text-[13px] text-[#8b95a5]">{CARD_PRODUCT.desc}</p>
                </div>
              </Link>
            ))}
          </div>
        </>
      )}

      <div className="flex-1" />
      <BottomTabBar active="상품" />
    </MobileScreen>
  );
}
