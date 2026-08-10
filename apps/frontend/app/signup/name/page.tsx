"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { MobileScreen } from "@/components/mobile-screen";
import { StepProgress } from "@/components/step-progress";
import { useSignupWizardStore } from "@/lib/signup-wizard-store";

export default function SignupNamePage() {
  const router = useRouter();
  const setName = useSignupWizardStore((s) => s.setName);
  const [name, setLocalName] = useState("");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    setName(trimmed);
    router.push("/signup/ssn");
  }

  return (
    <MobileScreen className="items-start pb-[32px]">
      <StepProgress step={1} total={6} />
      <form onSubmit={handleSubmit} className="flex w-full flex-1 flex-col px-[24px] pt-[30px]">
        <p className="w-full text-[24px] font-bold leading-[34px] text-[#191f28]">
          이름을 입력해주세요
        </p>
        <input
          autoFocus
          value={name}
          onChange={(e) => setLocalName(e.target.value)}
          placeholder="이름"
          className="mt-[32px] w-full rounded-[16px] border border-[#e5e8ef] bg-white p-[20px] text-[18px] font-medium leading-[26px] text-[#191f28] placeholder:text-[#b0b8c1] focus:outline-none"
        />
        <div className="min-h-px flex-1" />
        <button
          type="submit"
          disabled={!name.trim()}
          className="flex w-full items-center justify-center rounded-[14px] bg-[#0414a7] py-[16px] disabled:bg-[#f2f4f6]"
        >
          <p
            className={
              name.trim()
                ? "text-[17px] font-bold leading-[24px] text-white"
                : "text-[17px] font-bold leading-[24px] text-[#b0b8c1]"
            }
          >
            다음
          </p>
        </button>
      </form>
    </MobileScreen>
  );
}
