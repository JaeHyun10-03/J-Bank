"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { resolveDomainError } from "@/lib/domain-error-map";
import { formatWon } from "@/lib/format";
import { useTransferWizardStore } from "@/lib/transfer-wizard-store";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";
import { MobileNavBar } from "@/components/mobile-nav-bar";
import { TransferCompleteCard } from "@/components/transfer-complete-card";

type ApiResponseTransferResponse = components["schemas"]["ApiResponseTransferResponse"];

/**
 * AUTH_005(만료)와 TXN_005(실패횟수 초과로 이미 취소됨) 둘 다 지급정지가 풀린
 * 상태라 같은 방식으로 처리한다 — 화면플로우차트 190~191행: 안내 후 이체 재시작 유도.
 */
const HOLD_RELEASED_CODES = new Set(["AUTH_005_OTP_EXPIRED", "TXN_005_TRANSACTION_NOT_PENDING"]);

function formatTimer(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

export default function TransferOtpPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();

  const transactionId = useTransferWizardStore((s) => s.transactionId);
  const otpExpiresAt = useTransferWizardStore((s) => s.otpExpiresAt);
  const toAccountNumber = useTransferWizardStore((s) => s.toAccountNumber);
  const amount = useTransferWizardStore((s) => s.amount);
  const reset = useTransferWizardStore((s) => s.reset);

  const [otpCode, setOtpCode] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [result, setResult] = useState<{ toAccountNumber: string; amount: number } | null>(null);

  useEffect(() => {
    if (!transactionId) router.replace(`/accounts/${accountId}/transfer`);
  }, [transactionId, accountId, router]);

  useEffect(() => {
    if (!otpExpiresAt) return;
    const tick = () => {
      const secondsLeft = Math.max(0, Math.round((new Date(otpExpiresAt).getTime() - Date.now()) / 1000));
      setRemainingSeconds(secondsLeft);
    };
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [otpExpiresAt]);

  function invalidateAccountQueries() {
    queryClient.invalidateQueries({ queryKey: ["account-balance", accountId] });
    queryClient.invalidateQueries({ queryKey: ["transactions", accountId] });
  }

  function backToTransferInput() {
    invalidateAccountQueries();
    reset();
    router.replace(`/accounts/${accountId}/transfer`);
  }

  async function handleVerify() {
    if (!otpCode || remainingSeconds <= 0) return;
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await apiClient.post<ApiResponseTransferResponse>(
        `/transfers/${transactionId}/otp-verifications`,
        { otpCode },
      );
      invalidateAccountQueries();
      const completedToAccountNumber = toAccountNumber;
      const completedAmount = amount;
      reset();
      setResult({ toAccountNumber: completedToAccountNumber, amount: completedAmount });
    } catch (error) {
      const apiError = getApiError(error);
      setErrorMessage(resolveDomainError(apiError).message);
      setOtpCode("");
      if (apiError?.code && HOLD_RELEASED_CODES.has(apiError.code)) {
        backToTransferInput();
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (result) {
    return (
      <TransferCompleteCard
        toAccountNumber={result.toAccountNumber}
        amount={result.amount}
        onConfirm={() => router.replace(`/accounts/${accountId}`)}
      />
    );
  }

  if (!transactionId) return null;

  const expired = remainingSeconds <= 0;

  return (
    <MobileScreen className="items-start">
      <MobileNavBar onBack={() => router.back()} onCancel={() => router.push(`/accounts/${accountId}`)} />
      <div className="flex w-full flex-1 flex-col px-[24px] pt-[30px]">
        <p className="text-[24px] font-bold leading-[34px] text-[#191f28]">
          인증번호를 입력해주세요
        </p>
        <div className="mt-[16px] flex flex-col gap-[10px] rounded-[14px] bg-[#f7f9fd] px-[20px] py-[18px]">
          <div className="flex items-center justify-between">
            <span className="text-[15px] text-[#6b7684]">받는 계좌</span>
            <span className="text-[15px] text-[#191f28]">{toAccountNumber}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-[15px] text-[#6b7684]">지급정지된 금액</span>
            <span className="text-[15px] text-[#191f28]">{formatWon(amount)}</span>
          </div>
        </div>
        <div className="mt-[24px] flex w-full items-center gap-[8px] rounded-[16px] border border-[#e5e8ef] bg-white py-[15px] pl-[20px] pr-[14px]">
          <input
            autoFocus
            inputMode="numeric"
            value={otpCode}
            onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ""))}
            placeholder="인증번호"
            disabled={expired}
            className="flex-1 bg-transparent text-[18px] font-medium leading-[26px] text-[#191f28] placeholder:text-[#8b95a1] focus:outline-none disabled:opacity-50"
          />
          <p className={expired ? "text-[13px] font-semibold text-[#8b95a1]" : "text-[13px] font-semibold text-[#f04452]"}>
            {formatTimer(remainingSeconds)}
          </p>
        </div>
        {expired ? (
          <p className="mt-[12px] text-[14px] text-[#f04452]">
            인증 시간이 만료되어 지급정지가 해제됐습니다. 이체를 다시 시도해주세요.
          </p>
        ) : errorMessage ? (
          <p className="mt-[12px] text-[14px] text-[#f04452]">{errorMessage}</p>
        ) : null}
        <div className="min-h-px flex-1" />
        {expired ? (
          <button
            type="button"
            onClick={backToTransferInput}
            className="mb-[28px] w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
          >
            이체 다시 시도하기
          </button>
        ) : (
          <button
            type="button"
            disabled={submitting || !otpCode}
            onClick={handleVerify}
            className="mb-[28px] w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white disabled:opacity-60"
          >
            확인
          </button>
        )}
      </div>
    </MobileScreen>
  );
}
