"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { MobileScreen } from "@/components/mobile-screen";
import { StepProgress } from "@/components/step-progress";
import { useSignupWizardStore } from "@/lib/signup-wizard-store";
import { maskSsnBack } from "@/lib/resident-reg-no";
import { formatPhoneNumber } from "@/lib/format";

const CARRIERS = ["SKT", "KT", "LGU+", "알뜰폰"];

export default function SignupPhonePage() {
  const router = useRouter();
  const { name, ssnFront, ssnBack, carrier, setPhone } = useSignupWizardStore((s) => s);
  const [selectedCarrier, setSelectedCarrier] = useState(carrier);
  const [phone, setLocalPhone] = useState("");

  useEffect(() => {
    if (!ssnFront || !ssnBack) router.replace("/signup/ssn");
  }, [ssnFront, ssnBack, router]);

  const valid = /^01[0-9]{8,9}$/.test(phone);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid) return;
    setPhone(selectedCarrier, phone);
    router.push("/signup/otp");
  }

  if (!ssnFront || !ssnBack) return null;

  return (
    <MobileScreen className="items-start pb-[32px]">
      <StepProgress step={3} total={6} />
      <form onSubmit={handleSubmit} className="flex w-full flex-1 flex-col px-[24px] pt-[30px]">
        <p className="w-full text-[24px] font-bold leading-[34px] text-[#191f28]">
          휴대폰번호를 입력해주세요
        </p>
        <div className="flex w-full flex-col gap-[10px] pt-[32px]">
          <div className="flex w-full flex-col gap-[6px] rounded-[16px] bg-[#f7f8fb] px-[20px] py-[14px]">
            <p className="text-[13px] font-medium text-[#8b95a1]">휴대폰번호</p>
            <div className="flex w-full items-center gap-[10px]">
              <select
                value={selectedCarrier}
                onChange={(e) => setSelectedCarrier(e.target.value)}
                className="bg-transparent text-[18px] font-medium leading-[26px] text-[#191f28] focus:outline-none"
              >
                {CARRIERS.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
              <input
                autoFocus
                inputMode="numeric"
                value={formatPhoneNumber(phone)}
                onChange={(e) => setLocalPhone(e.target.value.replace(/\D/g, "").slice(0, 11))}
                placeholder="010-1234-5678"
                className="flex-1 bg-transparent text-[18px] font-medium leading-[26px] text-[#191f28] placeholder:text-[#b0b8c1] focus:outline-none"
              />
            </div>
          </div>
          <div className="flex w-full gap-[10px]">
            <div className="flex flex-1 flex-col gap-[6px] rounded-[16px] border border-[#e5e8ef] bg-white px-[20px] py-[14px]">
              <p className="text-[13px] text-[#8b95a1]">앞자리</p>
              <p className="text-[18px] leading-[26px] text-[#191f28]">{ssnFront}</p>
            </div>
            <div className="flex flex-1 flex-col gap-[6px] rounded-[16px] border border-[#e5e8ef] bg-white px-[20px] py-[14px]">
              <p className="text-[13px] text-[#8b95a1]">뒷자리</p>
              <p className="text-[18px] leading-[26px] text-[#191f28]">{maskSsnBack(ssnBack)}</p>
            </div>
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
          type="button"
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
