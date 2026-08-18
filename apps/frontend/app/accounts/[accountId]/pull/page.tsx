"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronDown, ChevronRight, Landmark, Plus, Zap, Check } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { resolveDomainError } from "@/lib/domain-error-map";
import { appendAmountDigit, removeLastAmountDigit, formatWon } from "@/lib/format";
import { useAuthStore } from "@/lib/auth-store";
import { useTransferWizardStore } from "@/lib/transfer-wizard-store";
import type { components } from "@/types/api";
import { AmountKeypad } from "@/components/amount-keypad";

type ApiResponsePage =
  components["schemas"]["ApiResponsePageResponseCustomerAccountSummaryResponse"];
type Account = components["schemas"]["CustomerAccountSummaryResponse"];
type ApiResponseTransferResponse = components["schemas"]["ApiResponseTransferResponse"];

const OTP_VALIDITY_MS = 3 * 60 * 1000;
const CHIPS = [
  { label: "+1만", delta: 10_000 },
  { label: "+10만", delta: 100_000 },
  { label: "+100만", delta: 1_000_000 },
];

type Sheet = "none" | "account" | "amount";

function BottomSheet({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative flex w-full max-w-[430px] flex-col rounded-t-[24px] bg-white pb-[40px] pt-[30px]">
        {children}
      </div>
    </div>
  );
}

export default function PullMoneyPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const customerId = useAuthStore((state) => state.customerId);
  const setInput = useTransferWizardStore((s) => s.setInput);
  const setPendingOtp = useTransferWizardStore((s) => s.setPendingOtp);

  const accountsQuery = useQuery({
    queryKey: ["accounts", customerId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponsePage>(`/customers/${customerId}/accounts`, {
        params: { page: 0, size: 20 },
      });
      return response.data.data?.content ?? [];
    },
    enabled: !!customerId,
  });

  const [sheet, setSheet] = useState<Sheet>("none");
  const [source, setSource] = useState<Account | null>(null);
  const [amount, setAmount] = useState("0");
  const [draftAmount, setDraftAmount] = useState("0");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [result, setResult] = useState<{ source: Account; amount: number } | null>(null);

  const current = accountsQuery.data?.find((a) => a.accountId === accountId);
  const others = (accountsQuery.data ?? []).filter((a) => a.accountId !== accountId);
  const amountValue = Number(amount);
  const draftValue = Number(draftAmount);
  const sourceBalance = Number(source?.balance ?? 0);
  const canSubmit = !!source && amountValue > 0 && amountValue <= sourceBalance;

  function openAmountSheet() {
    setDraftAmount(amount === "0" ? "0" : amount);
    setSheet("amount");
  }

  function confirmAmount() {
    setAmount(draftAmount);
    setSheet("none");
  }

  async function submit() {
    if (!source || !current || !canSubmit) return;
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const response = await apiClient.post<ApiResponseTransferResponse>("/transfers", {
        fromAccountNumber: source.accountNumber,
        toAccountNumber: current.accountNumber,
        amount: amountValue,
      });
      const data = response.data.data;
      queryClient.invalidateQueries({ queryKey: ["accounts", customerId] });
      queryClient.invalidateQueries({ queryKey: ["account-balance", accountId] });
      if (data?.status === "PENDING_OTP" && data.transactionId) {
        setInput({
          fromAccountNumber: source.accountNumber ?? "",
          toAccountNumber: current.accountNumber ?? "",
          amount: amountValue,
          memo: "",
        });
        setPendingOtp({
          transactionId: data.transactionId,
          otpExpiresAt: new Date(Date.now() + OTP_VALIDITY_MS).toISOString(),
        });
        router.push(`/accounts/${accountId}/transfer/otp`);
        return;
      }
      setResult({ source, amount: amountValue });
    } catch (error) {
      setErrorMessage(resolveDomainError(getApiError(error)).message);
    } finally {
      setSubmitting(false);
    }
  }

  if (result) {
    return (
      <div className="min-h-screen w-full bg-white">
        <div className="mx-auto flex min-h-screen w-full max-w-[430px] flex-col bg-white pb-[28px]">
          <div className="h-[104px]" />
          <div className="flex w-full items-center justify-center">
            <Check className="size-[36px] text-[#0114a7]" strokeWidth={3} />
          </div>
          <div className="h-[33px]" />
          <p className="text-center text-[22px] font-bold text-[#191f28]">가져오기 완료</p>
          <div className="mt-[79px] flex w-full flex-col gap-[8px] border-y border-[#e4e8f0] px-[20px] py-[16px]">
            <div className="flex w-full items-center justify-between">
              <p className="text-[14px] text-[#6b7684]">가져온 통장</p>
              <p className="text-[15px] font-semibold text-[#191f28]">
                {result.source.accountNumber}
              </p>
            </div>
            <div className="flex w-full items-center justify-between">
              <p className="text-[14px] text-[#6b7684]">가져온 금액</p>
              <p className="text-[15px] font-semibold text-[#191f28]">
                {formatWon(result.amount)}
              </p>
            </div>
          </div>
          <div className="min-h-px w-full flex-1" />
          <div className="px-[20px]">
            <button
              type="button"
              onClick={() => router.replace("/")}
              className="w-full rounded-[14px] bg-[#0114a7] py-[16px] text-[17px] font-bold text-white"
            >
              확인
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen w-full bg-white">
      <div className="relative mx-auto flex min-h-screen w-full max-w-[430px] flex-col bg-white pb-[28px]">
        <div className="flex h-[56px] items-center pl-[20px]">
          <button type="button" onClick={() => router.back()} aria-label="뒤로가기">
            <ChevronLeft className="size-[24px] text-[#191f28]" strokeWidth={1.8} />
          </button>
        </div>
        <div className="h-[23px]" />
        <div className="flex flex-col px-[20px]">
          <p className="text-[22px] font-bold leading-[32px] text-[#191f28]">
            이 계좌로 얼마를
            <br />
            가져오시겠어요?
          </p>
          <div className="h-[18px]" />
          <p className="text-[15px] text-[#8b95a1]">잔액 {formatWon(current?.balance)}</p>
          <div className="h-[51px]" />

          <p className="text-[15px] font-bold text-[#191f28]">가져올 통장</p>
          <div className="h-[9px]" />
          <button
            type="button"
            onClick={() => setSheet("account")}
            className="flex h-[70px] w-full items-center rounded-[14px] border border-[#e4e8f0] px-[18px]"
          >
            <div className="flex flex-1 flex-col items-start gap-[2px]">
              {source ? (
                <>
                  <p className="text-[16px] font-semibold text-[#191f28]">
                    {source.accountNumber}
                  </p>
                  <p className="text-[14px] text-[#8b95a1]">{formatWon(source.balance)}</p>
                </>
              ) : (
                <p className="text-[16px] font-medium text-[#b0b8c1]">계좌를 선택해주세요</p>
              )}
            </div>
            <ChevronDown className="size-[18px] text-[#8b95a1]" strokeWidth={2} />
          </button>
          <div className="h-[31px]" />

          <p className="text-[15px] font-bold text-[#191f28]">가져올 금액</p>
          <div className="h-[9px]" />
          <button
            type="button"
            onClick={openAmountSheet}
            className="flex h-[70px] w-full flex-col items-start gap-[3px] rounded-[14px] border border-[#e4e8f0] px-[18px] pt-[13px]"
          >
            <p className="text-[12px] text-[#b0b8c1]">금액</p>
            <div className="flex w-full items-center">
              <p
                className={
                  amountValue > 0
                    ? "text-[18px] font-medium text-[#191f28]"
                    : "text-[18px] font-medium text-[#b0b8c1]"
                }
              >
                {amountValue > 0 ? amountValue.toLocaleString("ko-KR") : "0"}
              </p>
              <div className="flex-1" />
              <p className="text-[16px] font-medium text-[#333d4b]">원</p>
            </div>
          </button>
          {errorMessage ? (
            <p className="pt-[12px] text-[13px] text-[#f04452]">{errorMessage}</p>
          ) : null}
          <div className="h-[55px]" />

          <div className="flex h-[74px] w-full items-center gap-[12px] rounded-[14px] bg-[#eff2f6] pl-[18px] pr-[16px] opacity-60">
            <Zap className="size-[24px] text-[#3350f5]" strokeWidth={1.8} />
            <div className="flex flex-1 flex-col gap-[2px]">
              <p className="text-[13px] text-[#4e5968]">매번 직접 돈 채우기 귀찮다면?</p>
              <p className="text-[15px] font-bold text-[#191f28]">편하게 자동충전</p>
            </div>
            <ChevronRight className="size-[18px] text-[#8b95a1]" strokeWidth={2} />
          </div>
        </div>

        <div className="min-h-px flex-1" />
        <div className="px-[20px]">
          <button
            type="button"
            disabled={!canSubmit || submitting}
            onClick={submit}
            className={
              canSubmit
                ? "flex h-[56px] w-full items-center justify-center rounded-[14px] bg-[#0114a7] text-[17px] font-bold text-white disabled:opacity-60"
                : "flex h-[56px] w-full items-center justify-center rounded-[14px] bg-[#e4e8f0] text-[17px] font-bold text-[#b0b8c1]"
            }
          >
            가져오기
          </button>
        </div>

        {sheet === "account" ? (
          <BottomSheet onClose={() => setSheet("none")}>
            <div className="flex flex-col px-[20px]">
              <p className="text-[19px] font-bold text-[#191f28]">어디서 돈을 가져올까요?</p>
              <div className="h-[20px]" />
              {others.length === 0 ? (
                <p className="py-[16px] text-[14px] text-[#8b95a1]">
                  가져올 수 있는 다른 계좌가 없어요.
                </p>
              ) : (
                others.map((account) => (
                  <button
                    key={account.accountId}
                    type="button"
                    onClick={() => {
                      setSource(account);
                      setSheet("none");
                    }}
                    className="flex h-[60px] w-full items-center gap-[12px]"
                  >
                    <div className="flex size-[44px] items-center justify-center rounded-full bg-[#3350f5]">
                      <Landmark className="size-[20px] text-white" strokeWidth={1.8} />
                    </div>
                    <div className="flex flex-col items-start gap-[1px]">
                      <p className="text-[15px] font-bold text-[#191f28]">
                        {account.accountNumber}
                      </p>
                      <p className="text-[14px] text-[#8b95a1]">{formatWon(account.balance)}</p>
                    </div>
                  </button>
                ))
              )}
              <div className="h-[18px]" />
              <div className="flex h-[26px] w-full items-center gap-[8px] opacity-50">
                <Plus className="size-[18px] text-[#6b7684]" strokeWidth={1.8} />
                <p className="text-[15px] font-medium text-[#6b7684]">다른 은행 계좌 불러오기</p>
              </div>
            </div>
          </BottomSheet>
        ) : null}

        {sheet === "amount" ? (
          <BottomSheet onClose={() => setSheet("none")}>
            <div className="flex flex-col px-[20px]">
              <div className="flex h-[34px] items-center gap-[6px]">
                <div className="h-[30px] w-[3px] shrink-0 bg-[#3b5bff]" />
                <p className="text-[24px] font-bold text-[#8b95a1]">얼마를 가져오시겠어요?</p>
              </div>
              <div className="h-[9px]" />
              <p className="text-[14px]">
                <span className="font-bold text-[#333d4b] underline">
                  {formatWon(sourceBalance)}
                </span>
                <span className="text-[#6b7684]"> 까지 가져올 수 있어요</span>
              </p>
            </div>
            <div className="h-[38px]" />
            <div className="flex gap-[10px] px-[20px]">
              {CHIPS.map((chip) => (
                <button
                  key={chip.label}
                  type="button"
                  onClick={() =>
                    setDraftAmount(String(Math.min(draftValue + chip.delta, sourceBalance)))
                  }
                  className="flex h-[30px] w-[80px] items-center justify-center rounded-[8px] bg-[#edf3ff]"
                >
                  <p className="text-[13px] font-medium text-[#3b5bff]">{chip.label}</p>
                </button>
              ))}
              <button
                type="button"
                onClick={() => setDraftAmount(String(sourceBalance))}
                className="flex h-[30px] w-[80px] items-center justify-center rounded-[8px] bg-[#edf3ff]"
              >
                <p className="text-[13px] font-medium text-[#3b5bff]">전액</p>
              </button>
            </div>
            <div className="h-[16px]" />
            <AmountKeypad
              onDigit={(d) => setDraftAmount((cur) => appendAmountDigit(cur, d))}
              onBackspace={() => setDraftAmount((cur) => removeLastAmountDigit(cur))}
            />
            <div className="h-[7px]" />
            <div className="px-[20px]">
              <button
                type="button"
                disabled={draftValue <= 0 || draftValue > sourceBalance}
                onClick={confirmAmount}
                className={
                  draftValue > 0 && draftValue <= sourceBalance
                    ? "flex h-[54px] w-full items-center justify-center rounded-[14px] bg-[#0114a7] text-[17px] font-bold text-white"
                    : "flex h-[54px] w-full items-center justify-center rounded-[14px] bg-[#e4e8f0] text-[17px] font-bold text-[#b0b8c1]"
                }
              >
                확인
              </button>
            </div>
          </BottomSheet>
        ) : null}
      </div>
    </div>
  );
}
