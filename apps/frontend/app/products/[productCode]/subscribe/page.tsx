"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Landmark, Check } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { resolveDomainError } from "@/lib/domain-error-map";
import { useAuthStore } from "@/lib/auth-store";
import { appendAmountDigit, removeLastAmountDigit, formatWon } from "@/lib/format";
import { MobileScreen } from "@/components/mobile-screen";
import { MobileNavBar } from "@/components/mobile-nav-bar";
import { AmountKeypad } from "@/components/amount-keypad";
import type { components } from "@/types/api";

type ApiResponsePageAccounts =
  components["schemas"]["ApiResponsePageResponseCustomerAccountSummaryResponse"];
type ApiResponsePageProducts = components["schemas"]["ApiResponsePageResponseProductSummaryResponse"];
type ApiResponseSubscribe = components["schemas"]["ApiResponseProductSubscribeResponse"];
type Account = components["schemas"]["CustomerAccountSummaryResponse"];

type Step = "account" | "amount" | "complete";

export default function ProductSubscribePage() {
  const { productCode } = useParams<{ productCode: string }>();
  const router = useRouter();
  const customerId = useAuthStore((state) => state.customerId);

  const [step, setStep] = useState<Step>("account");
  const [accountNumber, setAccountNumber] = useState<string | null>(null);
  const [amount, setAmount] = useState("0");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [result, setResult] = useState<{ contractNumber: string; maturityAt: string } | null>(null);

  const accountsQuery = useQuery({
    queryKey: ["accounts", customerId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponsePageAccounts>(
        `/customers/${customerId}/accounts`,
        { params: { page: 0, size: 20 } },
      );
      return response.data.data?.content ?? [];
    },
    enabled: !!customerId,
  });

  const productsQuery = useQuery({
    queryKey: ["products"],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponsePageProducts>("/products", {
        params: { page: 0, size: 20 },
      });
      return response.data.data?.content ?? [];
    },
  });
  const product = productsQuery.data?.find((p) => p.productCode === productCode);

  const amountValue = Number(amount);

  function selectAccount(account: Account) {
    setAccountNumber(account.accountNumber ?? null);
    setStep("amount");
  }

  async function submit() {
    if (!accountNumber) return;
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const response = await apiClient.post<ApiResponseSubscribe>(
        `/products/${productCode}/subscriptions`,
        { accountNumber, subscriptionAmount: amountValue },
      );
      const data = response.data.data;
      setResult({
        contractNumber: data?.contractNumber ?? "",
        maturityAt: data?.maturityAt ?? "",
      });
      setStep("complete");
    } catch (error) {
      setErrorMessage(resolveDomainError(getApiError(error)).message);
    } finally {
      setSubmitting(false);
    }
  }

  if (step === "complete" && result) {
    return (
      <MobileScreen className="items-center px-[24px] pb-[24px] pt-[104px]">
        <Check className="size-[36px] text-[#0114a7]" strokeWidth={3} />
        <p className="mt-[24px] text-center text-[22px] font-bold text-[#191f28]">
          {product?.productName ?? productCode} 가입이
          <br />
          완료됐어요
        </p>
        <p className="mt-[16px] text-[14px] text-[#8b95a5]">계약번호 {result.contractNumber}</p>
        {result.maturityAt ? (
          <p className="text-[14px] text-[#8b95a5]">
            만기일 {new Date(result.maturityAt).toLocaleDateString("ko-KR")}
          </p>
        ) : null}
        <div className="min-h-px w-full flex-1" />
        <button
          type="button"
          onClick={() => router.push("/contracts")}
          className="w-full rounded-[14px] bg-[#edf1f7] py-[16px] text-[15px] font-semibold text-[#191f28]"
        >
          내 가입상품 보기
        </button>
        <button
          type="button"
          onClick={() => router.replace("/")}
          className="mt-[8px] w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
        >
          홈으로
        </button>
      </MobileScreen>
    );
  }

  if (step === "amount") {
    const minAmount = Number(product?.minSubscriptionAmount ?? 0);
    const valid = amountValue >= minAmount && amountValue > 0;
    return (
      <MobileScreen className="items-start">
        <MobileNavBar onBack={() => setStep("account")} onCancel={() => router.back()} />
        <div className="flex w-full flex-col gap-[8px] px-[24px] pt-[16px]">
          <p className="text-[15px] font-semibold text-[#191f28]">{accountNumber} 에서 가입</p>
        </div>
        <div className="flex w-full flex-col gap-[8px] px-[24px] pb-[8px] pt-[16px]">
          <p className="text-[24px] font-bold text-[#8b95a1]">얼마를 가입하시겠어요?</p>
          {minAmount > 0 ? (
            <p className="text-[14px]">
              <span className="text-[#8b95a1]">최소 가입금액 </span>
              <span className="font-semibold text-[#6b7684]">{formatWon(minAmount)}</span>
            </p>
          ) : null}
        </div>
        <div className="flex h-[86px] items-center px-[24px]">
          <p className="text-[30px] font-bold text-[#191f28]">{formatWon(amountValue)}</p>
        </div>
        {errorMessage ? (
          <p className="w-full px-[24px] pb-[8px] text-[13px] text-[#f04452]">{errorMessage}</p>
        ) : null}
        <div className="min-h-px flex-1" />
        <AmountKeypad
          onDigit={(d) => setAmount((cur) => appendAmountDigit(cur, d))}
          onBackspace={() => setAmount((cur) => removeLastAmountDigit(cur))}
        />
        <div className="w-full px-[16px] pb-[16px] pt-[8px]">
          <button
            type="button"
            disabled={!valid || submitting}
            onClick={submit}
            className={
              valid && !submitting
                ? "w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
                : "w-full rounded-[14px] bg-[#f2f4f6] py-[17px] text-[17px] font-semibold text-[#b0b8c1]"
            }
          >
            가입하기
          </button>
        </div>
      </MobileScreen>
    );
  }

  return (
    <MobileScreen className="items-start">
      <MobileNavBar onBack={() => router.back()} />
      <div className="flex w-full flex-col px-[24px] pb-[24px] pt-[16px]">
        <p className="text-[24px] font-bold leading-[30px] text-[#191f28]">
          {product?.productName ?? "상품"} 가입
        </p>
        <p className="mt-[8px] text-[14px] text-[#8b95a5]">출금계좌를 선택해주세요</p>
      </div>
      {(accountsQuery.data ?? []).map((account) => (
        <button
          key={account.accountId}
          type="button"
          onClick={() => selectAccount(account)}
          className="flex w-full items-center gap-[12px] px-[24px] py-[12px]"
        >
          <div className="flex size-[40px] items-center justify-center rounded-full bg-[#3350f5]">
            <Landmark className="size-[18px] text-white" strokeWidth={1.8} />
          </div>
          <div className="flex flex-1 flex-col items-start gap-[1px]">
            <p className="text-[16px] font-semibold text-[#191f28]">입출금통장</p>
            <p className="text-[13px] text-[#8b95a5]">{account.accountNumber}</p>
          </div>
        </button>
      ))}
    </MobileScreen>
  );
}
