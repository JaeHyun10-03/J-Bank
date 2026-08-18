"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import {
  appendAccountDigit,
  removeLastAccountDigit,
  formatAccountNumber,
  appendAmountDigit,
  removeLastAmountDigit,
  formatWon,
} from "@/lib/format";
import { useTransferWizardStore } from "@/lib/transfer-wizard-store";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";
import { MobileNavBar } from "@/components/mobile-nav-bar";
import { AmountKeypad } from "@/components/amount-keypad";

type ApiResponseAccountDetailResponse = components["schemas"]["ApiResponseAccountDetailResponse"];

const ACCOUNT_NUMBER_PATTERN = /^\d{3}-\d{6}-\d$/;

type Step = "account" | "amount" | "memo";

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

  const [step, setStep] = useState<Step>("account");
  const [accountDigits, setAccountDigits] = useState("");
  const [accountError, setAccountError] = useState<string | null>(null);
  const [amount, setAmount] = useState("0");
  const [memo, setMemo] = useState("");

  const toAccountNumber = formatAccountNumber(accountDigits);
  const accountValid = ACCOUNT_NUMBER_PATTERN.test(toAccountNumber);
  const amountValue = Number(amount);

  function goToAmountStep() {
    if (!accountValid) return;
    if (toAccountNumber === accountQuery.data?.accountNumber) {
      setAccountError("출금 계좌와 동일한 계좌로는 이체할 수 없습니다.");
      return;
    }
    setAccountError(null);
    setStep("amount");
  }

  function handleBack() {
    if (step === "amount") setStep("account");
    else if (step === "memo") setStep("amount");
    else router.back();
  }

  function submit() {
    setInput({
      fromAccountNumber: accountQuery.data?.accountNumber ?? "",
      toAccountNumber,
      amount: amountValue,
      memo,
    });
    router.push(`/accounts/${accountId}/transfer/confirm`);
  }

  return (
    <MobileScreen className="items-start">
      <MobileNavBar onBack={handleBack} onCancel={() => router.push(`/accounts/${accountId}`)} />

      {step !== "account" ? (
        <div className="flex w-full flex-col gap-[4px] px-[24px] pb-[16px]">
          <p className="text-[13px] text-[#8b95a1]">받는 계좌 {toAccountNumber}</p>
          {step === "memo" ? (
            <p className="text-[13px] text-[#8b95a1]">보낼 금액 {formatWon(amountValue)}</p>
          ) : null}
        </div>
      ) : null}

      {step === "account" ? (
        <>
          <div className="flex flex-col gap-[8px] px-[24px] pb-[20px] pt-[16px]">
            <p className="text-[24px] font-bold leading-[34px] text-[#191f28]">어디로 보낼까요?</p>
          </div>
          <div className="flex flex-col gap-[6px] px-[24px] pb-[16px]">
            <p
              className={
                accountDigits
                  ? "border-b-2 border-[#4262ff] pb-[10px] text-[24px] font-bold text-[#191f28]"
                  : "border-b-2 border-[#e0e6f1] pb-[10px] text-[24px] font-bold text-[#b0b8c1]"
              }
            >
              {accountDigits ? toAccountNumber : "계좌번호 입력"}
            </p>
            {accountError ? <p className="text-[13px] text-[#f04452]">{accountError}</p> : null}
          </div>
          <div className="min-h-px flex-1" />
          <AmountKeypad
            onDigit={(d) => setAccountDigits((cur) => appendAccountDigit(cur, d === "00" ? "0" : d))}
            onBackspace={() => setAccountDigits((cur) => removeLastAccountDigit(cur))}
          />
          <div className="px-[16px] pb-[16px] pt-[8px]">
            <button
              type="button"
              disabled={!accountValid}
              onClick={goToAmountStep}
              className={
                accountValid
                  ? "w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
                  : "w-full rounded-[14px] bg-[#f2f4f6] py-[17px] text-[17px] font-semibold text-[#b0b8c1]"
              }
            >
              다음
            </button>
          </div>
        </>
      ) : step === "amount" ? (
        <>
          <div className="flex flex-col gap-[8px] px-[24px] pb-[8px]">
            <p className="text-[24px] font-bold leading-[34px] text-[#191f28]">얼마를 보낼까요?</p>
          </div>
          <div className="flex h-[70px] items-center px-[24px]">
            <p className="text-[28px] font-bold leading-[normal] text-[#191f28]">{formatWon(amountValue)}</p>
          </div>
          <div className="min-h-px flex-1" />
          <AmountKeypad
            onDigit={(d) => setAmount((cur) => appendAmountDigit(cur, d))}
            onBackspace={() => setAmount((cur) => removeLastAmountDigit(cur))}
          />
          <div className="px-[16px] pb-[16px] pt-[8px]">
            <button
              type="button"
              disabled={amountValue <= 0}
              onClick={() => setStep("memo")}
              className={
                amountValue > 0
                  ? "w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
                  : "w-full rounded-[14px] bg-[#f2f4f6] py-[17px] text-[17px] font-semibold text-[#b0b8c1]"
              }
            >
              다음
            </button>
          </div>
        </>
      ) : (
        <>
          <div className="flex flex-col gap-[8px] px-[24px] pb-[8px] pt-[8px]">
            <p className="text-[24px] font-bold leading-[34px] text-[#191f28]">
              받는 분에게 표시할
              <br />
              메모를 남겨보세요
            </p>
          </div>
          <div className="flex flex-col gap-[6px] px-[24px] pb-[16px] pt-[8px]">
            <input
              autoFocus
              value={memo}
              onChange={(e) => setMemo(e.target.value.slice(0, 200))}
              placeholder="메모(선택)"
              className="w-full rounded-[14px] border border-[#e0e6f1] px-[20px] py-[16px] text-[15px] text-[#191f28] placeholder:text-[#b0b8c1] focus:outline-none"
            />
          </div>
          <div className="min-h-px flex-1" />
          <div className="px-[16px] pb-[16px] pt-[8px]">
            <button
              type="button"
              onClick={submit}
              className="w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
            >
              다음
            </button>
          </div>
        </>
      )}
    </MobileScreen>
  );
}
