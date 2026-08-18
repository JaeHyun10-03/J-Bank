"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Check, ChevronRight, Landmark, Pencil } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { resolveDomainError } from "@/lib/domain-error-map";
import { formatWon } from "@/lib/format";
import { useTransferWizardStore } from "@/lib/transfer-wizard-store";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";
import { MobileNavBar } from "@/components/mobile-nav-bar";
import { TransferCompleteCard } from "@/components/transfer-complete-card";
import { BottomSheet } from "@/components/bottom-sheet";

type ApiResponseTransferResponse = components["schemas"]["ApiResponseTransferResponse"];

const OTP_VALIDITY_MS = 3 * 60 * 1000;
type Sheet = "none" | "memo" | "time";

export default function TransferConfirmPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();

  const fromAccountNumber = useTransferWizardStore((s) => s.fromAccountNumber);
  const toAccountNumber = useTransferWizardStore((s) => s.toAccountNumber);
  const amount = useTransferWizardStore((s) => s.amount);
  const memo = useTransferWizardStore((s) => s.memo);
  const setInput = useTransferWizardStore((s) => s.setInput);
  const setPendingOtp = useTransferWizardStore((s) => s.setPendingOtp);
  const reset = useTransferWizardStore((s) => s.reset);

  const [sheet, setSheet] = useState<Sheet>("none");
  const [memoDraft, setMemoDraft] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [result, setResult] = useState<{ toAccountNumber: string; amount: number } | null>(null);

  useEffect(() => {
    if (!toAccountNumber && !result) router.replace(`/accounts/${accountId}/transfer`);
  }, [toAccountNumber, result, accountId, router]);

  function openMemoSheet() {
    setMemoDraft(memo);
    setSheet("memo");
  }

  function confirmMemo() {
    setInput({ fromAccountNumber, toAccountNumber, amount, memo: memoDraft.slice(0, 200) });
    setSheet("none");
  }

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
        accountId={accountId}
        toAccountNumber={result.toAccountNumber}
        amount={result.amount}
        onConfirm={() => router.replace("/")}
      />
    );
  }

  if (!toAccountNumber) return null;

  return (
    <MobileScreen className="items-start animate-in slide-in-from-right fade-in duration-250">
      <MobileNavBar onBack={() => router.back()} onCancel={() => router.push(`/accounts/${accountId}`)} />

      <div className="flex w-full flex-col items-center pb-[24px] pt-[60px]">
        <div className="flex items-center gap-[8px]">
          <div className="flex size-[48px] items-center justify-center rounded-full bg-[#4262ff]">
            <p className="text-[20px] font-bold text-white">J</p>
          </div>
          <ChevronRight className="size-[16px] text-[#b0b8c1]" strokeWidth={2.5} />
          <div className="flex size-[48px] items-center justify-center rounded-full bg-[#e0e6f1]">
            <Landmark className="size-[22px] text-[#6b7684]" strokeWidth={1.8} />
          </div>
        </div>
        <p className="mt-[20px] text-center text-[24px] font-bold leading-[34px] text-[#191f28]">
          {toAccountNumber}로
          <br />
          {formatWon(amount)}을
          <br />
          보낼까요?
        </p>
      </div>

      <div className="flex w-full flex-1 flex-col px-[16px]">
        <div className="flex w-full flex-col gap-[16px] rounded-[14px] bg-[#f7f9fd] px-[20px] py-[18px]">
          <div className="flex w-full items-center justify-between">
            <p className="text-[15px] text-[#6b7684]">받는사람에게 표시할 메모</p>
            <button type="button" onClick={openMemoSheet} className="flex items-center gap-[6px]">
              <p className="text-[15px] text-[#191f28]">{memo || "메모 남기기"}</p>
              <Pencil className="size-[14px] text-[#8b95a1]" strokeWidth={1.8} />
            </button>
          </div>
          <div className="flex w-full items-center justify-between">
            <p className="text-[15px] text-[#6b7684]">보낼 시간</p>
            <button
              type="button"
              onClick={() => setSheet("time")}
              className="flex items-center gap-[6px]"
            >
              <p className="text-[15px] text-[#191f28]">바로 보낼게요</p>
              <Pencil className="size-[14px] text-[#8b95a1]" strokeWidth={1.8} />
            </button>
          </div>
        </div>
        {errorMessage ? <p className="mt-[16px] text-[14px] text-[#f04452]">{errorMessage}</p> : null}
      </div>

      {sheet === "memo" ? (
        <BottomSheet onClose={() => setSheet("none")}>
          <div className="flex flex-col px-[20px]">
            <p className="text-[19px] font-bold text-[#191f28]">받는사람에게 표시할 메모</p>
            <div className="h-[20px]" />
            <input
              autoFocus
              value={memoDraft}
              onChange={(e) => setMemoDraft(e.target.value.slice(0, 200))}
              placeholder="메모 남기기"
              className="w-full rounded-[14px] border border-[#e0e6f1] p-[16px] text-[16px] text-[#191f28] placeholder:text-[#b0b8c4] focus:outline-none"
            />
            <div className="h-[24px]" />
            <button
              type="button"
              onClick={confirmMemo}
              className="w-full rounded-[14px] bg-[#0114a7] py-[16px] text-[17px] font-semibold text-white"
            >
              확인
            </button>
          </div>
        </BottomSheet>
      ) : null}

      {sheet === "time" ? (
        <BottomSheet onClose={() => setSheet("none")}>
          <div className="flex flex-col px-[20px]">
            <p className="text-[19px] font-bold text-[#191f28]">언제 보낼까요?</p>
            <div className="h-[20px]" />
            {/* ponytail: 예약이체 백엔드가 없어 지금 바로 보내는 선택지만 실제로 동작한다 */}
            <div className="flex items-center justify-between rounded-[14px] bg-[#f7f9fd] px-[20px] py-[18px]">
              <p className="text-[16px] font-semibold text-[#191f28]">바로 보낼게요</p>
              <Check className="size-[20px] text-[#0114a7]" strokeWidth={2.5} />
            </div>
            <div className="h-[24px]" />
            <button
              type="button"
              onClick={() => setSheet("none")}
              className="w-full rounded-[14px] bg-[#0114a7] py-[16px] text-[17px] font-semibold text-white"
            >
              확인
            </button>
          </div>
        </BottomSheet>
      ) : null}

      <div className="w-full px-[16px] pb-[16px] pt-[8px]">
        <button
          type="button"
          disabled={submitting}
          onClick={handleSend}
          className="w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white disabled:opacity-60"
        >
          보내기
        </button>
      </div>
    </MobileScreen>
  );
}
