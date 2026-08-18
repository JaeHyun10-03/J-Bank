"use client";

import { useRouter } from "next/navigation";

const ACCORDION_ROWS = ["상품 상세", "금리안내", "이용안내", "약관 및 상품설명서"];

export function JKidsDetail() {
  const router = useRouter();

  return (
    <div className="flex w-full flex-col items-center bg-white pb-[100px]">
      <div className="flex h-[56px] w-full items-center px-[12px]">
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="뒤로가기"
          className="block size-[26px]"
        >
          <img alt="" className="size-full" src="/products/j-kids/back.svg" />
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

        <div className="relative mt-[34px] h-[190px] w-[240px]">
          <div className="absolute left-[18px] top-[22px] h-[64px] w-[96px] rounded-[12px] bg-[#7b9cff]" />
          <div className="absolute left-[30px] top-[66px] h-[7px] w-[26px] rounded-[4px] bg-[#d6e2ff]" />
          <img alt="" className="absolute left-[62px] top-[52px] h-[112px] w-[118px]" src="/products/j-kids/ellipse-1.svg" />
          <img alt="" className="absolute left-[76px] top-[44px] h-[58px] w-[90px]" src="/products/j-kids/ellipse-2.svg" />
          <img alt="" className="absolute left-[140px] top-[100px] size-[11px]" src="/products/j-kids/ellipse-3.svg" />
          <img alt="" className="absolute left-[104px] top-[118px] h-[24px] w-[30px]" src="/products/j-kids/ellipse-4.svg" />
          <img alt="" className="absolute left-[150px] top-[112px] h-[72px] w-[78px]" src="/products/j-kids/vector-1.svg" />
          <p className="absolute left-[176px] top-[126px] text-[30px] font-extrabold text-[#b57be8]">₩</p>
          <img alt="" className="absolute left-[24px] top-[118px] size-[11px]" src="/products/j-kids/ellipse-5.svg" />
          <img alt="" className="absolute left-[44px] top-[146px] size-[9px]" src="/products/j-kids/ellipse-6.svg" />
          <div className="absolute left-[198px] top-[56px] flex size-[16px] rotate-45 items-center justify-center">
            <div className="size-[11px] bg-[#f5a623]" />
          </div>
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
        <div className="relative mt-[34px] h-[210px] w-[150px]">
          <div className="absolute left-[30px] top-0 h-[52px] w-[90px] rounded-[12px] bg-[#d8e3ff] opacity-50" />
          <div className="absolute left-[42px] top-[34px] h-[6px] w-[24px] rounded-[3px] bg-white opacity-[.43]" />
          <div className="absolute left-[18px] top-[52px] h-[66px] w-[114px] rounded-[12px] bg-[#7b9cff]" />
          <div className="absolute left-[30px] top-[100px] h-[6px] w-[24px] rounded-[3px] bg-white opacity-85" />
          <div className="absolute left-[22px] top-[124px] h-[62px] w-[106px] rounded-[12px] bg-[#a8bef5]" />
          <div className="absolute left-[34px] top-[168px] h-[6px] w-[24px] rounded-[3px] bg-white opacity-85" />
          <div className="absolute left-[34px] top-[196px] h-[48px] w-[82px] rounded-[12px] bg-[#dde7ff] opacity-45" />
          <div className="absolute left-[46px] top-[226px] h-[6px] w-[24px] rounded-[3px] bg-white opacity-[.38]" />
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
        <div className="relative mt-[30px] h-[200px] w-[220px]">
          <div className="absolute left-[62px] top-[112px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <div className="absolute left-[66px] top-[129px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <div className="absolute left-[62px] top-[146px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <div className="absolute left-[66px] top-[163px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <div className="absolute left-[62px] top-[180px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <img alt="" className="absolute left-[76px] top-[40px] size-[74px]" src="/products/j-kids/ellipse-7.svg" />
          <p className="absolute left-[101px] top-[59px] text-[30px] font-extrabold text-[#e0a21b]">₩</p>
          <img alt="" className="absolute left-[24px] top-[88px] h-[52px] w-[22px]" src="/products/j-kids/vector-2.svg" />
          <img alt="" className="absolute left-0 top-[132px] h-[44px] w-[22px]" src="/products/j-kids/vector-3.svg" />
          <img alt="" className="absolute left-[186px] top-[120px] h-[44px] w-[22px]" src="/products/j-kids/vector-4.svg" />
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
        <img alt="" className="mt-[34px] h-[190px] w-[230px]" src="/products/j-kids/illo-phones.svg" />
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
              <img alt="" className="size-[22px]" src="/products/j-kids/icon-chevron-down.svg" />
            </button>
          ))}
        </div>
        <div className="mt-[22px] text-[13px] leading-[22px] text-[#8b95a1]">
          <p>2026.06.16 준법감시인 심의필 2026-1179</p>
          <p>(유효기간 2026.07.31.)</p>
        </div>
        <div className="mt-[26px] flex w-full items-start">
          <div className="flex flex-1 flex-col items-center gap-[8px] py-[6px]">
            <img alt="" className="size-[28px]" src="/products/j-kids/icon-share.svg" />
            <p className="text-[14px] font-bold text-[#191f28]">공유하기</p>
          </div>
          <div className="h-[44px] w-px shrink-0 bg-[#eff2f7]" />
          <div className="flex flex-1 flex-col items-center gap-[8px] py-[6px]">
            <img alt="" className="size-[28px]" src="/products/j-kids/icon-phone.svg" />
            <p className="text-[14px] font-bold text-[#191f28]">전화상담</p>
          </div>
          <div className="h-[44px] w-px shrink-0 bg-[#eff2f7]" />
          <div className="flex flex-1 flex-col items-center gap-[8px] py-[6px]">
            <img alt="" className="size-[28px]" src="/products/j-kids/icon-chat.svg" />
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
