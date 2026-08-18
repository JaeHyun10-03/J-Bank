"use client";

import { useRouter } from "next/navigation";
import { ChevronLeft, ChevronDown, Share2, Phone, MessageCircle } from "lucide-react";

const ACCORDION_ROWS = ["상품 상세", "금리안내", "이용안내", "약관 및 상품설명서"];

// ponytail: 피그마 원본의 손그림풍 일러스트(아기·카드탑·동전·휴대폰)는 좌표 기반 도형 수십 개로
// 구성돼 있어 이모지로 단순화했다. 실제 일러스트 에셋이 필요해지면 피그마 익스포트로 교체한다.
export function JKidsDetail() {
  const router = useRouter();

  return (
    <div className="flex w-full flex-col items-center bg-white pb-[100px]">
      <div className="flex h-[56px] w-full items-center px-[12px]">
        <button type="button" onClick={() => router.back()} aria-label="뒤로가기">
          <ChevronLeft className="size-[26px] text-[#191f28]" strokeWidth={2} />
        </button>
      </div>

      <div className="flex w-full flex-col items-center px-[20px] pt-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">우리 아이 미래 준비</p>
        <p className="mt-[14px] text-center text-[26px] font-extrabold leading-[36px] text-[#191f28]">
          든든하게 모아주는
        </p>
        <p className="text-center text-[26px] font-extrabold leading-[36px] text-[#4262ff]">
          J키즈 적금
        </p>
        <div className="mt-[34px] flex h-[190px] w-[240px] items-center justify-center rounded-full bg-[#eef2ff]">
          <span className="text-[80px] leading-none">👶</span>
        </div>
        <div className="mt-[40px] flex w-full items-start">
          <div className="flex flex-1 flex-col items-center gap-[6px] text-center">
            <p className="text-[13px] text-[#8b95a1]">금리(5년,세전)</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">최고 연 8.50%</p>
            <p className="text-[13px] text-[#8b95a1]">기본 연 3.50%</p>
          </div>
          <div className="h-[58px] w-px shrink-0 bg-[#e9edf3]" />
          <div className="flex flex-1 flex-col items-center gap-[6px] text-center">
            <p className="text-[13px] text-[#8b95a1]">납입금</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">월 최대 30만원</p>
            <p className="text-[13px] text-[#8b95a1]">1~5년</p>
          </div>
        </div>
      </div>

      <div className="mt-[44px] flex w-full flex-col items-center bg-[#f7f8fb] px-[20px] py-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">만기 때마다 고민하지 않아도</p>
        <p className="mt-[12px] text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          만 17세 전까지
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          자동으로 재가입할 수 있어요
        </p>
        <div className="mt-[34px] flex h-[210px] w-[150px] items-center justify-center rounded-[12px] bg-[#dde7ff]">
          <span className="text-[64px] leading-none">🗂️</span>
        </div>
      </div>

      <div className="flex w-full flex-col items-center px-[20px] py-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">쉽고 간편한 우대금리</p>
        <p className="mt-[12px] text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          매월 입금만
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          성공하면 우대금리 드려요
        </p>
        <div className="mt-[30px] flex h-[200px] w-[220px] items-center justify-center">
          <span className="text-[80px] leading-none">🪙</span>
        </div>
      </div>

      <div className="flex w-full flex-col items-center bg-[#f7f8fb] px-[20px] py-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">함께 관리하는 자녀 적금</p>
        <p className="mt-[12px] text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          엄마/아빠 한 명이 만들어도
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          같이 볼 수 있어요
        </p>
        <div className="mt-[34px] flex h-[190px] w-[230px] items-center justify-center">
          <span className="text-[72px] leading-none">📱👨‍👩‍👧</span>
        </div>
      </div>

      <div className="flex w-full flex-col items-start px-[20px] pt-[28px]">
        <p className="text-[14px] font-bold text-[#191f28]">상품 안내</p>
        <div className="mt-[10px] flex w-full flex-col">
          {ACCORDION_ROWS.map((row) => (
            <button
              key={row}
              type="button"
              className="flex h-[56px] w-full items-center justify-between"
            >
              <p className="text-[16px] font-bold text-[#191f28]">{row}</p>
              <ChevronDown className="size-[22px] text-[#191f28]" strokeWidth={1.8} />
            </button>
          ))}
        </div>
        <div className="mt-[22px] text-[13px] leading-[22px] text-[#8b95a1]">
          <p>2026.06.16 준법감시인 심의필 2026-1179</p>
          <p>(유효기간 2026.07.31.)</p>
        </div>
        <div className="mt-[26px] flex w-full items-start">
          <div className="flex flex-1 flex-col items-center gap-[8px] py-[6px]">
            <Share2 className="size-[28px] text-[#191f28]" strokeWidth={1.8} />
            <p className="text-[14px] font-bold text-[#191f28]">공유하기</p>
          </div>
          <div className="h-[44px] w-px shrink-0 bg-[#eff2f7]" />
          <div className="flex flex-1 flex-col items-center gap-[8px] py-[6px]">
            <Phone className="size-[28px] text-[#191f28]" strokeWidth={1.8} />
            <p className="text-[14px] font-bold text-[#191f28]">전화상담</p>
          </div>
          <div className="h-[44px] w-px shrink-0 bg-[#eff2f7]" />
          <div className="flex flex-1 flex-col items-center gap-[8px] py-[6px]">
            <MessageCircle className="size-[28px] text-[#191f28]" strokeWidth={1.8} />
            <p className="text-[14px] font-bold text-[#191f28]">톡상담</p>
          </div>
        </div>
      </div>

      <div className="fixed bottom-0 left-1/2 w-full max-w-[430px] -translate-x-1/2 bg-white px-[20px] pb-[24px] pt-[8px]">
        <button
          type="button"
          className="flex h-[56px] w-full items-center justify-center rounded-[14px] bg-[#0114a7]"
        >
          <p className="text-[17px] font-bold text-white">적금 만들기</p>
        </button>
      </div>
    </div>
  );
}
