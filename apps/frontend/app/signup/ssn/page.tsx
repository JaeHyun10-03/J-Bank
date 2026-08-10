"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { MobileScreen } from "@/components/mobile-screen";
import { StepProgress } from "@/components/step-progress";
import { useSignupWizardStore } from "@/lib/signup-wizard-store";
import { maskSsnBack } from "@/lib/resident-reg-no";

export default function SignupSsnPage() {
  const router = useRouter();
  const name = useSignupWizardStore((s) => s.name);
  const setSsn = useSignupWizardStore((s) => s.setSsn);
  const [front, setFront] = useState("");
  const [back, setBack] = useState("");
  const backRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!name) router.replace("/signup/name");
  }, [name, router]);

  const valid = /^\d{6}$/.test(front) && /^\d{7}$/.test(back);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid) return;
    setSsn(front, back);
    router.push("/signup/phone");
  }

  if (!name) return null;

  return (
    <MobileScreen className="items-start pb-[32px]">
      <StepProgress step={2} total={6} />
      <form onSubmit={handleSubmit} className="flex w-full flex-1 flex-col px-[24px] pt-[30px]">
        <p className="w-full text-[24px] font-bold leading-[34px] text-[#191f28]">
          주민등록번호를 입력해주세요
        </p>
        <div className="flex w-full flex-col gap-[10px] pt-[32px]">
          <div className="flex w-full gap-[10px]">
            <input
              autoFocus
              inputMode="numeric"
              maxLength={6}
              value={front}
              onChange={(e) => {
                const digits = e.target.value.replace(/\D/g, "").slice(0, 6);
                setFront(digits);
                if (digits.length === 6) backRef.current?.focus();
              }}
              placeholder="앞자리"
              className="min-w-0 flex-[6] rounded-[16px] border border-[#e5e8ef] bg-white px-[14px] py-[20px] text-center text-[18px] font-medium leading-[26px] text-[#191f28] placeholder:text-[#b0b8c1] focus:outline-none"
            />
            <input
              ref={backRef}
              inputMode="numeric"
              value={maskSsnBack(back)}
              onChange={() => {}}
              onKeyDown={(e) => {
                if (/^[0-9]$/.test(e.key)) {
                  e.preventDefault();
                  if (back.length < 7) setBack(back + e.key);
                } else if (e.key === "Backspace") {
                  e.preventDefault();
                  setBack(back.slice(0, -1));
                } else if (e.key.length === 1) {
                  e.preventDefault();
                }
              }}
              onPaste={(e) => e.preventDefault()}
              placeholder="뒷자리"
              className="min-w-0 flex-[7] rounded-[16px] border border-[#e5e8ef] bg-white px-[14px] py-[20px] text-center text-[18px] font-medium leading-[26px] text-[#191f28] placeholder:text-[#b0b8c1] focus:outline-none"
            />
          </div>
          <div className="flex w-full flex-col gap-[6px] rounded-[16px] border border-[#e5e8ef] bg-white px-[20px] py-[14px]">
            <p className="text-[13px] text-[#8b95a1]">이름</p>
            <p className="text-[18px] leading-[26px] text-[#191f28]">{name}</p>
          </div>
        </div>
        <div className="min-h-px flex-1" />
      </form>
      <div className="flex w-full flex-col items-start px-[24px]">
        <button
          type="submit"
          onClick={handleSubmit}
          disabled={!valid}
          className="flex w-full items-center justify-center rounded-[14px] bg-[#0414a7] py-[16px] disabled:bg-[#f2f4f6]"
        >
          <p
            className={
              valid
                ? "text-[17px] font-bold leading-[24px] text-white"
                : "text-[17px] font-bold leading-[24px] text-[#b0b8c1]"
            }
          >
            본인인증 진행하기
          </p>
        </button>
      </div>
    </MobileScreen>
  );
}
