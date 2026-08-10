"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { MobileScreen } from "@/components/mobile-screen";
import { useSignupWizardStore } from "@/lib/signup-wizard-store";
import { maskSsnBack } from "@/lib/resident-reg-no";
import { formatPhoneNumber } from "@/lib/format";

const OTP_SECONDS = 7 * 60 - 4; // 06:56, 피그마 타이머 표기와 동일한 시작값
const RESEND_COOLDOWN_SECONDS = 10;

function formatTimer(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

export default function SignupOtpPage() {
  const router = useRouter();
  const { name, ssnFront, ssnBack, carrier, phone, setOtpVerified } = useSignupWizardStore((s) => s);
  const [otp, setOtp] = useState("");
  const [seconds, setSeconds] = useState(OTP_SECONDS);
  const [resendCooldown, setResendCooldown] = useState(0);

  useEffect(() => {
    if (!phone) router.replace("/signup/phone");
  }, [phone, router]);

  useEffect(() => {
    if (seconds <= 0) return;
    const timer = setInterval(() => setSeconds((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(timer);
  }, [seconds]);

  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setInterval(() => setResendCooldown((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(timer);
  }, [resendCooldown]);

  function handleResend() {
    if (resendCooldown > 0) return;
    setSeconds(OTP_SECONDS);
    setResendCooldown(RESEND_COOLDOWN_SECONDS);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!otp) return;
    setOtpVerified();
    router.push("/signup/device-done");
  }

  if (!phone) return null;

  return (
    <MobileScreen className="items-start pb-[32px]">
      <div className="flex w-full items-center justify-end px-[24px] py-[14px]">
        <button
          type="button"
          onClick={() => router.push("/signup/phone")}
          className="text-[16px] font-medium leading-[22px] text-[#191f28]"
        >
          취소
        </button>
      </div>
      <form onSubmit={handleSubmit} className="flex w-full flex-1 flex-col px-[24px] pt-[30px]">
        <p className="w-full text-[24px] font-bold leading-[34px] text-[#191f28]">
          인증번호를 입력해주세요
        </p>
        <div className="flex w-full flex-col gap-[10px] pt-[32px]">
          <div className="flex w-full items-center gap-[8px] rounded-[16px] border border-[#e5e8ef] bg-white py-[15px] pl-[20px] pr-[14px]">
            <input
              autoFocus
              inputMode="numeric"
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
              placeholder="인증번호"
              className="flex-1 bg-transparent text-[18px] font-medium leading-[26px] text-[#191f28] placeholder:text-[#8b95a1] focus:outline-none"
            />
            <button
              type="button"
              onClick={handleResend}
              disabled={resendCooldown > 0}
              className={
                resendCooldown > 0
                  ? "rounded-[10px] bg-[#edeff3] px-[14px] py-[8px]"
                  : "rounded-[10px] bg-[#edf3ff] px-[14px] py-[8px]"
              }
            >
              <p
                className={
                  resendCooldown > 0
                    ? "text-[13px] font-semibold leading-[18px] text-[#b0b8c1]"
                    : "text-[13px] font-semibold leading-[18px] text-[#3b5bff]"
                }
              >
                {resendCooldown > 0 ? `재요청 ${resendCooldown}s` : "재요청"}
              </p>
            </button>
          </div>
          <div className="flex w-full items-center justify-end gap-[5px] pb-[4px] pr-[6px] pt-[2px]">
            <p className="text-[13px] font-semibold leading-[18px] text-[#f04452]">
              {formatTimer(seconds)}
            </p>
            <span className="flex size-[17px] items-center justify-center rounded-full border border-[#4e5968] text-[11px] font-bold text-[#4e5968]">
              ?
            </span>
          </div>
          <div className="flex w-full flex-col gap-[6px] rounded-[16px] bg-[#f7f8fb] px-[20px] py-[14px]">
            <p className="text-[13px] font-medium text-[#8b95a1]">휴대폰번호</p>
            <p className="text-[18px] font-medium leading-[26px] text-[#191f28]">
              {carrier} {formatPhoneNumber(phone)}
            </p>
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
          disabled={!otp}
          className={
            otp
              ? "flex w-full items-center justify-center rounded-[14px] bg-[#0414a7] py-[16px]"
              : "flex w-full items-center justify-center rounded-[14px] bg-[#f2f4f6] py-[16px]"
          }
        >
          <p className={otp ? "text-[17px] font-bold leading-[24px] text-white" : "text-[17px] font-bold leading-[24px] text-[#b0b8c1]"}>
            다음
          </p>
        </button>
      </div>
    </MobileScreen>
  );
}
