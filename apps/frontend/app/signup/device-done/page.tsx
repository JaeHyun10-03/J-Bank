"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Check } from "lucide-react";
import { MobileScreen } from "@/components/mobile-screen";
import { StepProgress } from "@/components/step-progress";
import { useSignupWizardStore } from "@/lib/signup-wizard-store";

export default function SignupDeviceDonePage() {
  const router = useRouter();
  const otpVerified = useSignupWizardStore((s) => s.otpVerified);

  useEffect(() => {
    if (!otpVerified) router.replace("/signup/otp");
  }, [otpVerified, router]);

  if (!otpVerified) return null;

  return (
    <MobileScreen className="items-start pb-[28px]">
      <StepProgress step={5} total={6} />
      <div className="h-[130px] w-full" />
      <div className="flex w-full flex-col items-center px-[24px]">
        <Check className="size-[58px] text-[#2539e9]" strokeWidth={3} />
        <p className="pt-[19px] text-[20px] font-bold leading-[30px] text-[#191f28]">
          기기인증 완료
        </p>
        <p className="pt-[30px] text-center text-[14px] leading-[20px] text-[#6b7684]">
          현재 휴대폰에서만 제이뱅크를 이용할 수 있습니다.
        </p>
      </div>
      <div className="min-h-px w-full flex-1" />
      <div className="flex w-full flex-col items-start px-[24px]">
        <button
          type="button"
          onClick={() => router.push("/signup/pin")}
          className="flex w-full items-center justify-center rounded-[14px] bg-[#0414a7] py-[16px]"
        >
          <p className="text-[17px] font-bold leading-[24px] text-white">확인</p>
        </button>
      </div>
    </MobileScreen>
  );
}
