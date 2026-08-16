"use client";

import { useParams, useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { appendAmountDigit, removeLastAmountDigit, formatWon } from "@/lib/format";
import { useTransferWizardStore } from "@/lib/transfer-wizard-store";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";
import { MobileNavBar } from "@/components/mobile-nav-bar";
import { AmountKeypad } from "@/components/amount-keypad";

type ApiResponseAccountDetailResponse = components["schemas"]["ApiResponseAccountDetailResponse"];

const ACCOUNT_NUMBER_PATTERN = /^\d{3}-\d{6}-\d$/;

const transferInputSchema = z.object({
  toAccountNumber: z
    .string()
    .regex(ACCOUNT_NUMBER_PATTERN, "계좌번호 형식이 올바르지 않습니다 (예: 110-000123-4)"),
  amount: z.string().regex(/^[1-9]\d*$/, "금액을 입력하세요"),
  memo: z.string().max(200, "메모는 200자 이내로 입력하세요").optional(),
});
type TransferInputForm = z.infer<typeof transferInputSchema>;

export default function TransferInputPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();
  const setInput = useTransferWizardStore((s) => s.setInput);

  const accountQuery = useQuery({
    queryKey: ["account", accountId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponseAccountDetailResponse>(`/accounts/${accountId}`);
      return response.data.data;
    },
  });

  const {
    register,
    setValue,
    watch,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<TransferInputForm>({
    resolver: zodResolver(transferInputSchema),
    defaultValues: { toAccountNumber: "", amount: "0", memo: "" },
  });
  const amount = watch("amount");
  const amountValue = Number(amount);

  function handleDigit(digit: string) {
    setValue("amount", appendAmountDigit(amount, digit), { shouldValidate: true });
  }

  function handleBackspace() {
    setValue("amount", removeLastAmountDigit(amount), { shouldValidate: true });
  }

  function onSubmit(values: TransferInputForm) {
    if (values.toAccountNumber === accountQuery.data?.accountNumber) {
      setError("toAccountNumber", { message: "출금 계좌와 동일한 계좌로는 이체할 수 없습니다." });
      return;
    }
    setInput({
      fromAccountNumber: accountQuery.data?.accountNumber ?? "",
      toAccountNumber: values.toAccountNumber,
      amount: Number(values.amount),
      memo: values.memo ?? "",
    });
    router.push(`/accounts/${accountId}/transfer/confirm`);
  }

  return (
    <MobileScreen className="items-start">
      <MobileNavBar onBack={() => router.back()} onCancel={() => router.push(`/accounts/${accountId}`)} />
      <form onSubmit={handleSubmit(onSubmit)} className="flex w-full flex-1 flex-col">
        <div className="flex flex-col gap-[8px] px-[24px] pb-[20px] pt-[16px]">
          <p className="text-[24px] font-bold leading-[34px] text-[#191f28]">어디로 보낼까요?</p>
        </div>
        <div className="flex flex-col gap-[6px] px-[24px] pb-[24px]">
          <input
            {...register("toAccountNumber")}
            placeholder="계좌번호 입력 (예: 110-000123-4)"
            className="w-full rounded-[14px] border border-[#e0e6f1] px-[20px] py-[20px] text-[16px] text-[#191f28] placeholder:text-[#b0b8c1] focus:outline-none"
          />
          {errors.toAccountNumber ? (
            <p className="text-[13px] text-[#f04452]">{errors.toAccountNumber.message}</p>
          ) : null}
        </div>
        <div className="flex flex-col gap-[8px] px-[24px] pb-[8px]">
          <p className="text-[20px] font-bold leading-[normal] text-[#191f28]">얼마를 보낼까요?</p>
          {errors.amount ? <p className="text-[13px] text-[#f04452]">{errors.amount.message}</p> : null}
        </div>
        <div className="flex h-[70px] items-center px-[24px]">
          <p className="text-[28px] font-bold leading-[normal] text-[#191f28]">{formatWon(amountValue)}</p>
        </div>
        <div className="flex flex-col gap-[6px] px-[24px] pb-[16px] pt-[8px]">
          <input
            {...register("memo")}
            placeholder="받는 분에게 표시할 메모(선택)"
            className="w-full rounded-[14px] border border-[#e0e6f1] px-[20px] py-[16px] text-[15px] text-[#191f28] placeholder:text-[#b0b8c1] focus:outline-none"
          />
          {errors.memo ? <p className="text-[13px] text-[#f04452]">{errors.memo.message}</p> : null}
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
    </MobileScreen>
  );
}
