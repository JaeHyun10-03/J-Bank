"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { X } from "lucide-react";
import { MobileScreen } from "@/components/mobile-screen";
import { PinDots, PinKeypad } from "@/components/pin-keypad";
import { useSignupWizardStore } from "@/lib/signup-wizard-store";
import { useAuthStore } from "@/lib/auth-store";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { deriveBirthDate } from "@/lib/resident-reg-no";
import type { components } from "@/types/api";

type ApiResponseCustomerRegisterResponse = components["schemas"]["ApiResponseCustomerRegisterResponse"];
type ApiResponseLoginResponse = components["schemas"]["ApiResponseLoginResponse"];

const LAST_LOGIN_ID_KEY = "jbank_last_login_id";

export default function SignupPinPage() {
  const router = useRouter();
  const { name, ssnFront, ssnBack, phone, otpVerified } = useSignupWizardStore((s) => s);
  const login = useAuthStore((s) => s.login);
  const [pin, setPin] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [duplicate, setDuplicate] = useState(false);

  const ready = name && ssnFront && ssnBack && phone && otpVerified;

  useEffect(() => {
    if (!ready) router.replace("/signup/otp");
  }, [ready, router]);

  useEffect(() => {
    if (pin.length !== 6 || submitting) return;
    submit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pin]);

  async function submit() {
    setSubmitting(true);
    setError(null);
    try {
      const birthDate = deriveBirthDate(ssnFront, ssnBack);
      const registerResponse = await apiClient.post<ApiResponseCustomerRegisterResponse>("/customers", {
        name,
        loginId: phone,
        password: pin,
        residentRegNo: `${ssnFront}-${ssnBack}`,
        birthDate,
        phone,
        identityVerificationMethod: "NON_FACE_TO_FACE",
      });
      const registered = registerResponse.data.data;

      const loginResponse = await apiClient.post<ApiResponseLoginResponse>("/auth/login", {
        loginId: phone,
        password: pin,
      });
      const loggedIn = loginResponse.data.data;
      login(loggedIn?.customerId ?? registered?.customerId ?? "", loggedIn?.name ?? name);
      window.localStorage.setItem(LAST_LOGIN_ID_KEY, phone);

      router.push(registered?.eddRequired ? "/signup/edd" : "/accounts/open");
    } catch (err) {
      const apiError = getApiError(err);
      if (apiError?.code === "ACC_001_DUPLICATE_RESIDENT_REG_NO") {
        setDuplicate(true);
      } else {
        setError(apiError?.message ?? "가입에 실패했습니다. 다시 시도해주세요.");
        setPin("");
      }
      setSubmitting(false);
    }
  }

  if (!ready) return null;

  if (duplicate) {
    return (
      <MobileScreen className="items-center justify-center gap-[16px] px-[24px] text-center">
        <p className="text-[20px] font-bold text-[#191f28]">이미 등록된 계정이 있습니다</p>
        <p className="text-[14px] text-[#6b7684]">
          동일한 실명번호로 이미 가입된 계정이 있습니다. 로그인해주세요.
        </p>
        <Link
          href="/login"
          className="flex w-full items-center justify-center rounded-[14px] bg-[#0414a7] py-[16px]"
        >
          <p className="text-[17px] font-bold text-white">로그인하러 가기</p>
        </Link>
      </MobileScreen>
    );
  }

  return (
    <MobileScreen>
      <div className="flex w-full items-center justify-end px-[20px] py-[13px]">
        <button type="button" onClick={() => router.push("/welcome")}>
          <X className="size-[24px] text-[#191f28]" />
        </button>
      </div>
      <div className="flex w-full flex-col items-center pt-[16px]">
        <p className="text-[20px] font-medium leading-[28px] text-[#191f28]">간편비밀번호</p>
      </div>
      <PinDots value={pin} />
      {error ? (
        <p className="w-full pt-[16px] text-center text-[13px] text-[#f04452]">{error}</p>
      ) : null}
      <div className="min-h-px w-full flex-1" />
      <div className="flex w-full items-center justify-center pb-[63px]">
        <p className="text-[13px] text-[#b0b8c1]">비밀번호 다시 설정하기</p>
      </div>
      <PinKeypad
        onDigit={(d) => !submitting && pin.length < 6 && setPin(pin + d)}
        onBackspace={() => !submitting && setPin(pin.slice(0, -1))}
      />
    </MobileScreen>
  );
}
