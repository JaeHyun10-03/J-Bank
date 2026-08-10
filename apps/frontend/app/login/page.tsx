"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { X } from "lucide-react";
import { MobileScreen } from "@/components/mobile-screen";
import { PinDots, PinKeypad } from "@/components/pin-keypad";
import { useAuthStore } from "@/lib/auth-store";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import type { components } from "@/types/api";

type LoginResponse = components["schemas"]["LoginResponse"];
type ApiResponseLoginResponse = components["schemas"]["ApiResponseLoginResponse"];

const LAST_LOGIN_ID_KEY = "jbank_last_login_id";

export default function LoginPage() {
  const router = useRouter();
  const login = useAuthStore((s) => s.login);
  const [loginId, setLoginId] = useState<string | null | undefined>(undefined);
  const [pin, setPin] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [locked, setLocked] = useState(false);

  useEffect(() => {
    setLoginId(window.localStorage.getItem(LAST_LOGIN_ID_KEY));
  }, []);

  useEffect(() => {
    if (loginId === null) router.replace("/login/id");
  }, [loginId, router]);

  useEffect(() => {
    if (pin.length !== 6 || submitting) return;
    submit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pin]);

  async function submit() {
    setSubmitting(true);
    setError(null);
    try {
      const response = await apiClient.post<ApiResponseLoginResponse>("/auth/login", {
        loginId,
        password: pin,
      });
      const data = response.data.data as LoginResponse;
      login(data.customerId ?? "", data.name ?? "");
      router.push("/");
    } catch (err) {
      const apiError = getApiError(err);
      if (apiError?.code === "AUTH_002_ACCOUNT_LOCKED") {
        setLocked(true);
      } else {
        setError("비밀번호가 일치하지 않습니다.");
        setPin("");
      }
      setSubmitting(false);
    }
  }

  if (!loginId) return null;

  if (locked) {
    return (
      <MobileScreen className="items-center justify-center gap-[16px] px-[24px] text-center">
        <p className="text-[20px] font-bold text-[#191f28]">계정이 잠겼습니다</p>
        <p className="text-[14px] text-[#6b7684]">
          비밀번호 연속 실패로 계정이 잠겼습니다. 잠시 후 다시 시도해주세요.
        </p>
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
      <div className="flex w-full items-center justify-center gap-[14px] pb-[26px]">
        <p className="text-[13px] text-[#8b95a1]">비밀번호 재설정</p>
        <div className="h-[12px] w-px bg-[#e5e8ef]" />
        <button
          type="button"
          onClick={() => router.push("/login/id")}
          className="text-[13px] text-[#8b95a1]"
        >
          다른 방법으로 로그인
        </button>
      </div>
      <PinKeypad
        onDigit={(d) => !submitting && pin.length < 6 && setPin(pin + d)}
        onBackspace={() => !submitting && setPin(pin.slice(0, -1))}
      />
    </MobileScreen>
  );
}
