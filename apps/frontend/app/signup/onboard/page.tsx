import Link from "next/link";
import { MobileScreen } from "@/components/mobile-screen";

const STEPS = [
  { num: 1, label: "본인인증", active: true },
  { num: 2, label: "회원가입", active: false },
  { num: 3, label: "통장과 카드 만들기", active: false },
];

export default function SignupOnboardPage() {
  return (
    <MobileScreen className="items-start px-[24px] pb-[32px] pt-[64px]">
      <div className="w-full text-[24px] font-bold leading-[36px] text-[#191f28]">
        <p>기분 좋은 금융생활</p>
        <p>제이뱅크를 시작해볼까요?</p>
      </div>
      <div className="flex w-full flex-col items-start pt-[52px]">
        {STEPS.map((step, i) => (
          <div key={step.num} className="flex w-full flex-col items-start">
            <div className="flex w-full items-center gap-[14px]">
              <div
                className={
                  step.active
                    ? "flex size-[26px] shrink-0 items-center justify-center rounded-[13px] bg-[#4262ff]"
                    : "flex size-[26px] shrink-0 items-center justify-center rounded-[13px] border-[1.2px] border-[#b0b8c1]"
                }
              >
                <p
                  className={
                    step.active
                      ? "text-[13px] font-bold leading-[14px] text-white"
                      : "text-[13px] font-medium leading-[14px] text-[#8b95a1]"
                  }
                >
                  {step.num}
                </p>
              </div>
              <p
                className={
                  step.active
                    ? "text-[17px] font-bold leading-[24px] text-[#4262ff]"
                    : "text-[17px] font-medium leading-[24px] text-[#191f28]"
                }
              >
                {step.label}
              </p>
            </div>
            {i < STEPS.length - 1 ? (
              <div className="flex w-full items-start py-[4px] pl-[12px]">
                <div className="h-[24px] w-0 border-l-[1.2px] border-dashed border-[#d8d8d8]" />
              </div>
            ) : null}
          </div>
        ))}
      </div>
      <div className="min-h-px w-full flex-1" />
      <Link
        href="/signup/name"
        className="flex w-full items-center justify-center rounded-[14px] bg-[#0414a7] py-[16px]"
      >
        <p className="text-[17px] font-bold leading-[24px] text-white">제이뱅크 시작하기</p>
      </Link>
    </MobileScreen>
  );
}
