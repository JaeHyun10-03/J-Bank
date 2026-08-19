"use client";

import { useRouter } from "next/navigation";

export function JKidsAccountDetail() {
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
          <img alt="" className="size-full" src="/products/j-kids-account/back.svg" />
        </button>
      </div>

      <div className="flex w-full flex-col items-center px-[20px] pt-[20px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">우리 아이 첫 금융생활</p>
        <p className="mt-[12px] text-center text-[26px] font-extrabold leading-[37px] text-[#191f28]">
          차곡차곡 시작하는
        </p>
        <p className="text-center text-[26px] font-extrabold leading-[37px] text-[#4262ff]">J키즈 통장</p>

        <div className="relative mt-[35px] h-[190px] w-full">
          <div className="absolute left-[104px] top-[12px] h-[50px] w-[76px] rounded-[9px] bg-[#c3d2fd]" />
          <div className="absolute left-[99px] top-[2px] h-[50px] w-[76px] rounded-[9px] bg-[#93acf7]" />
          <div className="absolute left-[108px] top-[26px] h-[7px] w-[20px] rounded-[3.5px] bg-[#e8eefe]" />
          <img alt="" className="absolute left-[126px] top-[28px] h-[60px] w-[84px]" src="/products/j-kids-account/ellipse-0.svg" />
          <img alt="" className="absolute left-[127px] top-[44px] h-[80px] w-[82px]" src="/products/j-kids-account/ellipse-1.svg" />
          <img alt="" className="absolute left-[126px] top-[26px] h-[39px] w-[82px]" src="/products/j-kids-account/vector-0.svg" />
          <img alt="" className="absolute left-[121px] top-[78px] h-[16px] w-[12px]" src="/products/j-kids-account/ellipse-2.svg" />
          <img alt="" className="absolute left-[203px] top-[78px] h-[16px] w-[12px]" src="/products/j-kids-account/ellipse-2.svg" />
          <img alt="" className="absolute left-[133px] top-[92px] h-[12px] w-[20px]" src="/products/j-kids-account/ellipse-3.svg" />
          <img alt="" className="absolute left-[184px] top-[92px] h-[12px] w-[20px]" src="/products/j-kids-account/ellipse-3.svg" />
          <img alt="" className="absolute left-[141px] top-[80px] h-[4px] w-[14px]" src="/products/j-kids-account/vector-1.svg" />
          <img alt="" className="absolute left-[180px] top-[76px] h-[10px] w-[9px]" src="/products/j-kids-account/ellipse-4.svg" />
          <img alt="" className="absolute left-[152px] top-[96px] h-[23px] w-[32px]" src="/products/j-kids-account/vector-2.svg" />
          <img alt="" className="absolute left-[158px] top-[108px] h-[8px] w-[14px]" src="/products/j-kids-account/ellipse-5.svg" />
          <img alt="" className="absolute left-[193px] top-[96px] h-[7px] w-[11px]" src="/products/j-kids-account/ellipse-6.svg" />
          <img alt="" className="absolute left-[221px] top-[108px] h-[67px] w-[88px]" src="/products/j-kids-account/vector-3.svg" />
          <p className="absolute left-[221px] top-[127px] w-[88px] -translate-x-1/2 text-center text-[30px] font-extrabold leading-[34px] text-[#c583f0]">
            ₩
          </p>
          <img alt="" className="absolute left-[264px] top-[42px] size-[13px]" src="/products/j-kids-account/vector-4.svg" />
          <img alt="" className="absolute left-[108px] top-[116px] size-[8px]" src="/products/j-kids-account/ellipse-7.svg" />
          <img alt="" className="absolute left-[126px] top-[140px] size-[8px]" src="/products/j-kids-account/ellipse-8.svg" />
        </div>

        <div className="mt-[45px] flex w-full items-center">
          <div className="flex flex-1 flex-col items-center gap-[8px] text-center">
            <p className="text-[13px] text-[#8b95a1]">금리(1년,세전)</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">기본 연 0.10%</p>
          </div>
          <div className="h-[38px] w-px shrink-0 bg-[#e9edf3]" />
          <div className="flex flex-1 flex-col items-center gap-[8px] text-center">
            <p className="text-[13px] text-[#8b95a1]">수수료(이체)</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">무료</p>
          </div>
        </div>
      </div>

      <div className="mt-[21px] h-[171px] w-full bg-[#f7f8fb]" />

      <div className="fixed bottom-0 left-1/2 w-full max-w-[430px] -translate-x-1/2 bg-white px-[20px] pb-[24px] pt-[8px]">
        <button
          type="button"
          className="flex h-[56px] w-full items-center justify-center rounded-[14px] bg-[#0114a7]"
        >
          <p className="text-[17px] font-bold text-white">통장 만들기</p>
        </button>
      </div>
    </div>
  );
}
