"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { MobileScreen } from "@/components/mobile-screen";
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
      <div className="flex w-full items-center justify-end px-[24px] py-[14px]">
        <button
          type="button"
          onClick={() => router.push("/welcome")}
          className="text-[16px] font-medium leading-[22px] text-[#191f28]"
        >
          취소
        </button>
      </div>
      <form onSubmit={handleSubmit} className="flex w-full flex-col gap-[32px] px-[24px] pt-[30px]">
        <p className="w-full text-[24px] font-bold leading-[34px] text-[#191f28]">
          이름을 입력해주세요
        </p>
        <input
          autoFocus
          value={name}
          onChange={(e) => setLocalName(e.target.value)}
          placeholder="이름"
          className="w-full rounded-[16px] border border-[#e5e8ef] bg-white p-[20px] text-[18px] font-medium leading-[26px] text-[#191f28] placeholder:text-[#b0b8c1] focus:outline-none"
        />
      </form>
    </MobileScreen>
  );
}
