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

const OTP_VALIDITY_MS = 3 * 60 * 1000;

export default function TransferConfirmPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();

  const fromAccountNumber = useTransferWizardStore((s) => s.fromAccountNumber);
  const toAccountNumber = useTransferWizardStore((s) => s.toAccountNumber);
  const amount = useTransferWizardStore((s) => s.amount);
  const memo = useTransferWizardStore((s) => s.memo);
  const setPendingOtp = useTransferWizardStore((s) => s.setPendingOtp);
  const reset = useTransferWizardStore((s) => s.reset);

  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [result, setResult] = useState<{ toAccountNumber: string; amount: number } | null>(null);

  useEffect(() => {
    if (!toAccountNumber && !result) router.replace(`/accounts/${accountId}/transfer`);
  }, [toAccountNumber, result, accountId, router]);

  async function handleSend() {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const response = await apiClient.post<ApiResponseTransferResponse>("/transfers", {
        fromAccountNumber,
        toAccountNumber,
        amount,
        memo: memo || undefined,
      });
      const data = response.data.data;
      queryClient.invalidateQueries({ queryKey: ["account-balance", accountId] });
      queryClient.invalidateQueries({ queryKey: ["transactions", accountId] });
      if (data?.status === "PENDING_OTP" && data.transactionId) {
        setPendingOtp({
          transactionId: data.transactionId,
          otpExpiresAt: new Date(Date.now() + OTP_VALIDITY_MS).toISOString(),
        });
        router.push(`/accounts/${accountId}/transfer/otp`);
        return;
      }
      setResult({ toAccountNumber, amount });
      reset();
    } catch (error) {
      const apiError = getApiError(error);
      setErrorMessage(resolveDomainError(apiError).message);
      if (apiError?.code === "TXN_002_SAME_ACCOUNT_TRANSFER" || apiError?.code === "TXN_003_COUNTERPARTY_ACCOUNT_NOT_FOUND") {
        router.replace(`/accounts/${accountId}/transfer`);
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

  if (!toAccountNumber) return null;

  return (
    <MobileScreen className="items-start">
      <MobileNavBar onBack={() => router.back()} onCancel={() => router.push(`/accounts/${accountId}`)} />
      <div className="flex w-full flex-1 flex-col px-[24px] pt-[30px]">
        <p className="text-center text-[24px] font-bold leading-[34px] text-[#191f28]">
          {toAccountNumber}로
          <br />
          {formatWon(amount)}을 보낼까요?
        </p>
        <div className="mt-[32px] flex flex-col gap-[16px] rounded-[14px] bg-[#f7f9fd] px-[20px] py-[18px]">
          <div className="flex items-center justify-between">
            <span className="text-[15px] text-[#6b7684]">보내는 계좌</span>
            <span className="text-[15px] text-[#191f28]">{fromAccountNumber}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-[15px] text-[#6b7684]">받는 계좌</span>
            <span className="text-[15px] text-[#191f28]">{toAccountNumber}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-[15px] text-[#6b7684]">금액</span>
            <span className="text-[15px] text-[#191f28]">{formatWon(amount)}</span>
          </div>
          {memo ? (
            <div className="flex items-center justify-between">
              <span className="text-[15px] text-[#6b7684]">메모</span>
              <span className="text-[15px] text-[#191f28]">{memo}</span>
            </div>
          ) : null}
        </div>
        {errorMessage ? <p className="mt-[16px] text-[14px] text-[#f04452]">{errorMessage}</p> : null}
        <div className="min-h-px flex-1" />
        <button
          type="button"
          disabled={submitting}
          onClick={handleSend}
          className="mb-[28px] w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white disabled:opacity-60"
        >
          보내기
        </button>
      </div>
    </MobileScreen>
  );
}
