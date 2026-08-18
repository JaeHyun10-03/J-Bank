"use client";

import { useRouter } from "next/navigation";
import { ChevronLeft, ChevronDown, Share2, Phone, MessageCircle, Play } from "lucide-react";

const ACCORDION_ROWS = ["상품상세", "금리안내", "이용안내", "약관 및 상품설명서"];

// ponytail: 피그마 원본의 농장·과일캐릭터·현금다발 일러스트는 좌표 기반 도형 수십 개로 구성돼
// 있어 이모지로 단순화했다. 실제 일러스트 에셋이 필요해지면 피그마 익스포트로 교체한다.
export function JFarmDetail() {
  const router = useRouter();

  return (
    <div className="flex w-full flex-col items-center bg-white pb-[100px]">
      <div className="flex h-[56px] w-full items-center px-[14px]">
        <button type="button" onClick={() => router.back()} aria-label="뒤로가기">
          <ChevronLeft className="size-[26px] text-[#191f28]" strokeWidth={2} />
        </button>
        <p className="flex-1 text-center text-[17px] font-bold text-[#191f28]">J팜 농장</p>
        <div className="size-[26px]" />
      </div>

      <div className="flex w-full flex-col items-center px-[20px] pt-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">
          이자 수확하고, 다시 심어 내 돈을 굴려요
        </p>
        <p className="mt-[14px] text-center text-[26px] font-extrabold leading-[36px] text-[#191f28]">
          매달 이자가 굴러오는 적금
        </p>
        <p className="text-center text-[26px] font-extrabold leading-[36px] text-[#4262ff]">
          J팜 농장
        </p>
        <div className="mt-[34px] flex h-[250px] w-[300px] items-center justify-center rounded-[24px] bg-[#eaf7ea]">
          <span className="text-[96px] leading-none">🌾</span>
        </div>
        <div className="mt-[44px] flex w-full items-start">
          <div className="flex flex-1 flex-col items-center gap-[6px] text-center">
            <p className="text-[13px] text-[#8b95a1]">금리(1년,세전)</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">기본 연 3.00%</p>
          </div>
          <div className="h-[52px] w-px shrink-0 bg-[#e9edf3]" />
          <div className="flex flex-1 flex-col items-center gap-[6px] text-center">
            <p className="text-[13px] text-[#8b95a1]">납입금</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">월 최대 1천만원</p>
            <p className="text-[13px] text-[#8b95a1]">6개월, 12개월</p>
          </div>
        </div>
      </div>

      <div className="mt-[44px] flex w-full flex-col items-center bg-[#f7f8fb] px-[20px] py-[56px]">
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          매달 들어오는 이자로
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">더 크게 불려요</p>
        <div className="mt-[16px] text-center text-[15px] leading-[24px] text-[#8b95a1]">
          <p>이자는 다시 저금 가능하고,</p>
          <p>중도해지해도 받은 이자는 차감되지 않아요</p>
        </div>
        <div className="mt-[44px] flex size-[180px] items-center justify-center rounded-full bg-[#2f58d6]">
          <span className="text-[72px] leading-none">💰</span>
        </div>
      </div>

      <div className="flex w-full flex-col items-center px-[20px] py-[60px]">
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">입금 금액에 따라</p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          귀여운 과일 캐릭터를 획득해요
        </p>
        <p className="mt-[16px] text-center text-[15px] text-[#8b95a1]">
          과일이 많을수록 더 많은 이자를 수확해요
        </p>
        <div className="mt-[40px] grid grid-cols-2 gap-[16px]">
          {["🍓", "🍇", "🍑", "🫐"].map((fruit) => (
            <div
              key={fruit}
              className="flex h-[110px] w-[105px] items-center justify-center rounded-[16px] bg-[#f7f8fb]"
            >
              <span className="text-[48px] leading-none">{fruit}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="flex w-full flex-col items-center px-[20px] py-[44px]">
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          월 최대 1천만원까지
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          자유로운 입금이 가능해요
        </p>
        <p className="mt-[16px] text-center text-[15px] text-[#8b95a1]">
          원하는 만큼, 필요할 때 마다 입금해요
        </p>
        <div className="mt-[44px] flex h-[150px] w-[200px] items-center justify-center">
          <span className="text-[72px] leading-none">💵</span>
        </div>
      </div>

      <div className="flex w-full flex-col items-center bg-[#f7f8fb] px-[20px] py-[52px]">
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">J팜 농장</p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          체험기가 궁금하다면?
        </p>
        <p className="mt-[16px] text-center text-[15px] text-[#8b95a1]">
          대학생 크리에이터들의 리뷰를 확인하세요
        </p>
        <div className="mt-[28px] flex h-[175px] w-full items-center justify-center rounded-[4px] bg-[#d5dae2]">
          <div className="flex size-[58px] items-center justify-center rounded-full bg-white/80">
            <Play className="size-[26px] fill-[#3b5bf0] text-[#3b5bf0]" />
          </div>
        </div>
      </div>

      <div className="flex w-full flex-col items-start px-[20px] pt-[36px]">
        <p className="text-[15px] font-bold text-[#191f28]">상품 안내</p>
        <div className="mt-[6px] flex w-full flex-col">
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
      </div>
      <div className="w-full px-[20px] pb-[10px] pt-[18px] text-[13px] leading-[22px] text-[#8b95a1]">
        <p>2025.11.21. 준법감시인 심의필 2025-2560</p>
        <p>(유효기간 : 2026.08.13.)</p>
      </div>
      <div className="flex w-full items-start px-[20px] pb-[24px] pt-[18px]">
        <div className="flex flex-1 flex-col items-center gap-[8px]">
          <Share2 className="size-[28px] text-[#191f28]" strokeWidth={1.8} />
          <p className="text-[13px] font-bold text-[#191f28]">공유하기</p>
        </div>
        <div className="h-[34px] w-px shrink-0 bg-[#e9edf3]" />
        <div className="flex flex-1 flex-col items-center gap-[8px]">
          <Phone className="size-[28px] text-[#191f28]" strokeWidth={1.8} />
          <p className="text-[13px] font-bold text-[#191f28]">전화상담</p>
        </div>
        <div className="h-[34px] w-px shrink-0 bg-[#e9edf3]" />
        <div className="flex flex-1 flex-col items-center gap-[8px]">
          <MessageCircle className="size-[28px] text-[#191f28]" strokeWidth={1.8} />
          <p className="text-[13px] font-bold text-[#191f28]">톡상담</p>
        </div>
      </div>

      <div className="fixed bottom-0 left-1/2 w-full max-w-[430px] -translate-x-1/2 bg-white px-[20px] pb-[24px] pt-[8px]">
        <button
          type="button"
          className="flex h-[56px] w-full items-center justify-center rounded-[14px] bg-[#0114a7]"
        >
          <p className="text-[17px] font-bold text-white">농장 만들기</p>
        </button>
      </div>
    </div>
  );
}
