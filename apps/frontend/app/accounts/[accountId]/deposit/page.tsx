"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { resolveDomainError } from "@/lib/domain-error-map";
import { appendAmountDigit, removeLastAmountDigit, formatWon } from "@/lib/format";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";
import { MobileNavBar } from "@/components/mobile-nav-bar";
import { AmountKeypad } from "@/components/amount-keypad";

type ApiResponseAccountDetailResponse = components["schemas"]["ApiResponseAccountDetailResponse"];
type ApiResponseAccountTransactionResponse = components["schemas"]["ApiResponseAccountTransactionResponse"];

const amountSchema = z.object({
  amount: z.string().regex(/^[1-9]\d*$/, "금액을 입력하세요"),
});
type AmountForm = z.infer<typeof amountSchema>;

type Step = "input" | "confirm";

export default function DepositPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [step, setStep] = useState<Step>("input");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [result, setResult] = useState<{ amount: number; balanceAfter: number } | null>(null);

  const {
    setValue,
    watch,
    handleSubmit,
    formState: { errors },
  } = useForm<AmountForm>({ resolver: zodResolver(amountSchema), defaultValues: { amount: "0" } });
  const amount = watch("amount");
  const amountValue = Number(amount);

  const accountQuery = useQuery({
    queryKey: ["account", accountId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponseAccountDetailResponse>(`/accounts/${accountId}`);
      return response.data.data;
    },
  });

  function handleDigit(digit: string) {
    setValue("amount", appendAmountDigit(amount, digit), { shouldValidate: true });
  }

  function handleBackspace() {
    setValue("amount", removeLastAmountDigit(amount), { shouldValidate: true });
  }

  async function submitDeposit() {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const response = await apiClient.post<ApiResponseAccountTransactionResponse>(
        `/accounts/${accountId}/deposit`,
        { amount: amountValue, channel: "INTERNET_BANKING" },
      );
      const data = response.data.data;
      setResult({ amount: amountValue, balanceAfter: data?.balanceAfter ?? amountValue });
      queryClient.invalidateQueries({ queryKey: ["account-balance", accountId] });
      queryClient.invalidateQueries({ queryKey: ["transactions", accountId] });
    } catch (error) {
      setErrorMessage(resolveDomainError(getApiError(error)).message);
      setStep("input");
    } finally {
      setSubmitting(false);
    }
  }

  if (result) {
    return (
      <MobileScreen className="items-center justify-center px-[24px] text-center">
        <p className="text-[24px] font-bold leading-[34px] text-[#191f28]">
          {formatWon(result.amount)}이 입금됐어요
        </p>
        <p className="mt-[8px] text-[15px] text-[#8b95a5]">입금 후 잔액 {formatWon(result.balanceAfter)}</p>
        <button
          type="button"
          onClick={() => router.replace(`/accounts/${accountId}`)}
          className="mt-[32px] w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
        >
          확인
        </button>
      </MobileScreen>
    );
  }

  return (
    <MobileScreen className="items-start">
      <MobileNavBar onBack={() => router.back()} onCancel={() => router.push(`/accounts/${accountId}`)} />
      {errorMessage ? (
        <p className="w-full px-[24px] pb-[8px] text-[13px] text-[#f04452]">{errorMessage}</p>
      ) : null}
      {step === "confirm" ? (
        <div className="flex w-full flex-1 flex-col px-[24px] pt-[30px]">
          <p className="text-[24px] font-bold leading-[34px] text-[#191f28]">
            {formatWon(amountValue)}을 입금할까요?
          </p>
          <div className="mt-[32px] flex flex-col gap-[16px] rounded-[14px] bg-[#f7f9fd] px-[20px] py-[18px]">
            <div className="flex items-center justify-between">
              <span className="text-[15px] text-[#6b7684]">입금 계좌</span>
              <span className="text-[15px] text-[#191f28]">{accountQuery.data?.accountNumber}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-[15px] text-[#6b7684]">입금액</span>
              <span className="text-[15px] text-[#191f28]">{formatWon(amountValue)}</span>
            </div>
          </div>
          <div className="min-h-px flex-1" />
          <button
            type="button"
            disabled={submitting}
            onClick={submitDeposit}
            className="mb-[28px] w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white disabled:opacity-60"
          >
            입금하기
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit(() => setStep("confirm"))} className="flex w-full flex-1 flex-col">
          <div className="flex flex-col gap-[8px] px-[24px] pb-[8px] pt-[16px]">
            <p className="text-[24px] font-bold leading-[34px] text-[#191f28]">얼마를 입금할까요?</p>
            {errors.amount ? <p className="text-[13px] text-[#f04452]">{errors.amount.message}</p> : null}
          </div>
          <div className="flex h-[86px] items-center px-[24px] py-[24px]">
            <p className="text-[30px] font-bold leading-[normal] text-[#191f28]">{formatWon(amountValue)}</p>
          </div>
          <div className="min-h-px flex-1" />
          <AmountKeypad onDigit={handleDigit} onBackspace={handleBackspace} />
          <div className="px-[16px] pb-[16px] pt-[8px]">
            <button
              type="submit"
              disabled={amountValue <= 0}
              className={
                amountValue > 0
                  ? "w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
                  : "w-full rounded-[14px] bg-[#f2f4f6] py-[17px] text-[17px] font-semibold text-[#b0b8c1]"
              }
            >
              다음
            </button>
          </div>
        </form>
      )}
    </MobileScreen>
  );
}
