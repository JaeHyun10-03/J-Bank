"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { resolveDomainError } from "@/lib/domain-error-map";
import { useAuthStore } from "@/lib/auth-store";
import { MobileScreen } from "@/components/mobile-screen";
import type { components } from "@/types/api";

type ApiResponseAccountOpenResponse = components["schemas"]["ApiResponseAccountOpenResponse"];

export default function AccountOpenPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const customerId = useAuthStore((state) => state.customerId);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [accountNumber, setAccountNumber] = useState<string | null>(null);

  async function submit() {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const response = await apiClient.post<ApiResponseAccountOpenResponse>("/accounts", {
        productType: "CHECKING",
        initialDeposit: 0,
      });
      setAccountNumber(response.data.data?.accountNumber ?? null);
      queryClient.invalidateQueries({ queryKey: ["accounts", customerId] });
    } catch (error) {
      setErrorMessage(resolveDomainError(getApiError(error)).message);
    } finally {
      setSubmitting(false);
    }
  }

  if (accountNumber) {
    return (
      <MobileScreen className="items-center justify-center gap-[16px] px-[24px] text-center">
        <p className="text-[20px] font-bold text-[#191f28]">계좌개설 완료</p>
        <p className="text-[14px] text-[#6b7684]">{accountNumber} 계좌가 개설됐습니다.</p>
        <button
          type="button"
          onClick={() => router.push("/")}
          className="mt-[8px] flex w-full items-center justify-center rounded-[10px] bg-[#4262ff] py-[13px]"
        >
          <p className="text-[15px] font-semibold text-white">홈으로</p>
        </button>
      </MobileScreen>
    );
  }

  return (
    <MobileScreen className="items-center justify-center gap-[16px] px-[24px] text-center">
      <p className="text-[20px] font-bold text-[#191f28]">계좌개설</p>
      <p className="text-[14px] text-[#6b7684]">입출금이 자유로운 계좌를 개설합니다.</p>
      {errorMessage ? <p className="text-[13px] text-[#f04452]">{errorMessage}</p> : null}
      <button
        type="button"
        disabled={submitting}
        onClick={submit}
        className="mt-[8px] flex w-full items-center justify-center rounded-[10px] bg-[#4262ff] py-[13px] disabled:opacity-50"
      >
        <p className="text-[15px] font-semibold text-white">계좌 개설하기</p>
      </button>
    </MobileScreen>
  );
}
