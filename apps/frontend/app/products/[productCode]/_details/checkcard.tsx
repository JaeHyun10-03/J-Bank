"use client";

import { useRouter } from "next/navigation";

const BENEFITS = [
  { icon: "/products/checkcard/glyph-bus.svg", name: "대중교통 그린패스", desc: "교통비 최대 53% 환급" },
  { icon: "/products/checkcard/glyph-coffee.svg", name: "카페·편의점", desc: "건당 최대 10% 캐시백" },
  { icon: "/products/checkcard/glyph-tv.svg", name: "OTT·구독", desc: "정기결제 5% 캐시백" },
];

export function CheckCardDetail() {
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
          <img alt="" className="size-full" src="/products/checkcard/back.svg" />
        </button>
      </div>

      <div className="flex w-full flex-col items-center gap-[8px] py-[16px]">
        <p className="text-[14px] font-medium text-[#8b95a5]">내맘대로 골라받는 역대급 혜택</p>
        <p className="text-center text-[24px] font-bold text-[#191f28]">ONE 체크카드 (그린패스)</p>
        <div className="relative flex h-[258px] w-[170px] items-center justify-center overflow-hidden rounded-[16px] bg-gradient-to-br from-[#2a2a2e] to-[#050506]">
          <p className="absolute left-[18px] top-[20px] text-[12px] font-semibold text-[#9aa0a8]">
            J-Bank
          </p>
          <p className="text-[44px] font-bold text-[#2ed37f]">∞</p>
        </div>
      </div>

      <div className="flex w-full flex-col items-center pb-[16px] pt-[8px]">
        <p className="text-center text-[24px] font-bold text-[#191f28]">
          최대 <span className="text-[#4262ff]">10%</span> 무제한 캐시백
        </p>
      </div>

      <div className="flex w-full flex-col px-[24px] pb-[8px]">
        {BENEFITS.map(({ icon, name, desc }, i) => (
          <div
            key={name}
            className={i > 0 ? "flex items-center gap-[14px] border-t border-[#f2f4f6] py-[16px]" : "flex items-center gap-[14px] py-[16px]"}
          >
            <div className="flex size-[44px] shrink-0 items-center justify-center rounded-full bg-[#eef0f6]">
              <img alt="" className="size-[24px]" src={icon} />
            </div>
            <div className="flex flex-col gap-[4px]">
              <p className="text-[16px] font-bold text-[#191f28]">{name}</p>
              <p className="text-[14px] text-[#8b95a1]">{desc}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="fixed bottom-0 left-1/2 w-full max-w-[430px] -translate-x-1/2 bg-white px-[16px] pb-[16px] pt-[8px]">
        <button
          type="button"
          className="flex h-[54px] w-full items-center justify-center rounded-[14px] bg-[#0114a7]"
        >
          <p className="text-[17px] font-semibold text-white">카드 만들기</p>
        </button>
      </div>
    </div>
  );
}
