"use client";

import { useRouter } from "next/navigation";

const LOAN_TYPES = [
  "신용대출",
  "마이너스통장",
  "비상금대출",
  "햇살론",
  "공동대출",
  "사잇돌대출",
  "새희망홀씨II",
];

export function CreditLoanDetail() {
  const router = useRouter();

  return (
    <div className="flex min-h-screen w-full flex-col items-center bg-white pb-[100px]">
      <div className="flex h-[56px] w-full items-center px-[16px]">
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="뒤로가기"
          className="block h-[28px] w-[41px]"
        >
          <img alt="" className="size-full" src="/products/credit-loan/back.svg" />
        </button>
      </div>

      <div className="flex w-full flex-col items-center gap-[8px] px-[16px] pb-[8px] pt-[20px]">
        <p className="text-[14px] text-[#8b95a5]">신용대출</p>
        <p className="text-center text-[25px] font-bold leading-[34px] text-[#191f28]">
          제이뱅크
          <br />
          신용대출 한번에 알아보기
        </p>
      </div>

      <div className="flex w-full flex-wrap items-start justify-center gap-[8px] px-[24px] py-[16px]">
        {LOAN_TYPES.map((type) => (
          <span
            key={type}
            className="inline-flex items-center rounded-full bg-[#edf1f7] px-[14px] py-[8px] text-[13px] leading-none font-medium text-[#6b7684]"
          >
            {type}
          </span>
        ))}
      </div>

      <div className="relative flex h-[130px] items-center justify-center">
        <img alt="" className="size-[90px]" src="/products/credit-loan/ellipse.svg" />
        <p className="absolute text-[34px] font-bold text-white">₩</p>
      </div>

      <div className="flex w-full items-center px-[24px] pb-[20px] pt-[16px]">
        <div className="flex flex-1 flex-col items-center gap-[6px]">
          <p className="text-[14px] text-[#8b95a5]">최대한도</p>
          <p className="text-[22px] font-bold text-[#191f28]">3억원</p>
        </div>
        <div className="h-[44px] w-px shrink-0 bg-[#e0e6f1]" />
        <div className="flex flex-1 flex-col items-center gap-[6px]">
          <p className="text-[14px] text-[#8b95a5]">금리</p>
          <p className="text-[22px] font-bold text-[#191f28]">연 5.00%~15.00%</p>
        </div>
      </div>

      <div className="w-full px-[16px] py-[8px]">
        <div className="flex flex-col gap-[4px] rounded-[14px] bg-[#f7f9fd] px-[18px] py-[16px] text-[13px]">
          <p className="font-bold text-[#191f28]">🎉 신용대출 한번에 알아보기 오픈 이벤트</p>
          <p className="text-[#6b7684]">커피 쿠폰부터 최대 5만원 캐시백까지!</p>
        </div>
      </div>

      <div className="fixed bottom-0 left-1/2 w-full max-w-[430px] -translate-x-1/2 bg-white px-[16px] pb-[16px] pt-[8px]">
        <button
          type="button"
          className="flex h-[54px] w-full items-center justify-center rounded-[14px] bg-[#0114a7]"
        >
          <p className="text-[17px] font-semibold text-white">한도 조회하기</p>
        </button>
      </div>
    </div>
  );
}
