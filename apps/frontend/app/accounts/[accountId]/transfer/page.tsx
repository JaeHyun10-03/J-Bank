"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Star, Search, Landmark, Check } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import {
  appendAmountDigit,
  removeLastAmountDigit,
  formatWon,
  formatAccountNumber,
} from "@/lib/format";
import { useAuthStore } from "@/lib/auth-store";
import { useTransferWizardStore } from "@/lib/transfer-wizard-store";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";
import { MobileNavBar } from "@/components/mobile-nav-bar";
import { AmountKeypad } from "@/components/amount-keypad";
import { BottomSheet } from "@/components/bottom-sheet";

type ApiResponsePage =
  components["schemas"]["ApiResponsePageResponseCustomerAccountSummaryResponse"];
type ApiResponseAccountDetailResponse = components["schemas"]["ApiResponseAccountDetailResponse"];
type ApiResponseBalanceResponse = components["schemas"]["ApiResponseBalanceResponse"];
type Account = components["schemas"]["CustomerAccountSummaryResponse"];

const ACCOUNT_NUMBER_PATTERN = /^\d{3}-\d{6}-\d$/;
const CHIPS = [
  { label: "+1만", delta: 10_000 },
  { label: "+10만", delta: 100_000 },
  { label: "+100만", delta: 1_000_000 },
];

// ponytail: 연락처 송금은 디바이스 연락처 접근·전화번호→계좌 매핑 백엔드가 없어
// 피그마 원본 더미 연락처로만 시각적으로 채운다. 실제 송금은 발생하지 않는다.
const DUMMY_CONTACTS = [
  { name: "강동완쌤", phone: "010-6621-2332" },
  { name: "강민서 선생님", phone: "010-9794-3899" },
  { name: "강민재", phone: "010-3677-6614" },
  { name: "강서영", phone: "010-9946-7449" },
  { name: "강수 원장선생님", phone: "010-6334-9851" },
  { name: "경현 누나", phone: "010-6668-4764" },
];

type Mode = "account" | "phone";
type AccountStep = "account" | "amount";
type PhoneStep = "contacts" | "amount" | "complete";

export default function TransferInputPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();
  const customerId = useAuthStore((state) => state.customerId);
  const setInput = useTransferWizardStore((s) => s.setInput);

  const accountQuery = useQuery({
    queryKey: ["account", accountId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponseAccountDetailResponse>(`/accounts/${accountId}`);
      return response.data.data;
    },
  });
  const balanceQuery = useQuery({
    queryKey: ["account-balance", accountId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponseBalanceResponse>(`/accounts/${accountId}/balance`);
      return response.data.data;
    },
  });
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

  const [mode, setMode] = useState<Mode>("account");

  // 계좌번호 경로
  const [accountStep, setAccountStep] = useState<AccountStep>("account");
  const [accountDigits, setAccountDigits] = useState("");
  const [accountError, setAccountError] = useState<string | null>(null);
  const [amount, setAmount] = useState("0");

  // 휴대폰번호 경로 (더미)
  const [phoneTermsAgreed, setPhoneTermsAgreed] = useState(false);
  const [phoneStep, setPhoneStep] = useState<PhoneStep>("contacts");
  const [contactQuery, setContactQuery] = useState("");
  const [selectedContact, setSelectedContact] = useState<{ name: string; phone: string } | null>(
    null,
  );
  const [phoneAmount, setPhoneAmount] = useState("0");

  const toAccountNumber = formatAccountNumber(accountDigits);
  const accountValid = ACCOUNT_NUMBER_PATTERN.test(toAccountNumber);
  const amountValue = Number(amount);
  const availableBalance = Number(balanceQuery.data?.availableBalance ?? 0);

  function selectMyAccount(account: Account) {
    setAccountDigits((account.accountNumber ?? "").replace(/\D/g, ""));
    setAccountError(null);
    setAccountStep("amount");
  }

  function goToAmountStep() {
    if (!accountValid) return;
    if (toAccountNumber === accountQuery.data?.accountNumber) {
      setAccountError("출금 계좌와 동일한 계좌로는 이체할 수 없습니다.");
      return;
    }
    setAccountError(null);
    setAccountStep("amount");
  }

  function submitTransferInput() {
    setInput({
      fromAccountNumber: accountQuery.data?.accountNumber ?? "",
      toAccountNumber,
      amount: amountValue,
      memo: "",
    });
    router.push(`/accounts/${accountId}/transfer/confirm`);
  }

  function selectContact(contact: { name: string; phone: string }) {
    setSelectedContact(contact);
    setPhoneAmount("0");
    setPhoneStep("amount");
  }

  const filteredContacts = DUMMY_CONTACTS.filter((c) =>
    contactQuery ? c.name.includes(contactQuery) || c.phone.includes(contactQuery) : true,
  );
  const phoneAmountValue = Number(phoneAmount);

  if (mode === "phone" && phoneStep === "complete" && selectedContact) {
    return (
      <MobileScreen className="items-center px-[24px] pb-[24px] pt-[104px]">
        <Check className="size-[36px] text-[#0114a7]" strokeWidth={3} />
        <p className="mt-[24px] text-center text-[22px] font-bold text-[#191f28]">
          <span className="text-[#4262ff]">{selectedContact.name}</span>님에게
          <br />
          {formatWon(phoneAmountValue)}을 보냈어요
        </p>
        <p className="mt-[16px] text-[14px] text-[#8b95a5]">{selectedContact.phone}로 보냈어요</p>
        <div className="min-h-px w-full flex-1" />
        <button
          type="button"
          onClick={() => router.replace("/")}
          className="w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
        >
          확인
        </button>
      </MobileScreen>
    );
  }

  if (mode === "phone" && phoneStep === "amount" && selectedContact) {
    return (
      <MobileScreen className="items-start">
        <MobileNavBar
          onBack={() => setPhoneStep("contacts")}
          onCancel={() => router.push(`/accounts/${accountId}`)}
        />
        <div className="flex w-full flex-col gap-[8px] px-[24px] pt-[16px]">
          <div className="flex items-center gap-[8px]">
            <span className="size-[22px] rounded-full bg-[#4262ff]" />
            <p className="text-[15px] font-semibold text-[#191f28]">
              {accountQuery.data?.accountNumber} 에서
            </p>
          </div>
          <div className="flex items-center gap-[8px]">
            <span className="size-[22px] rounded-full bg-[#f5c842]" />
            <p className="text-[15px] font-semibold text-[#191f28]">
              {selectedContact.name} {selectedContact.phone}
            </p>
          </div>
        </div>
        <div className="flex w-full flex-col gap-[8px] px-[24px] pb-[8px] pt-[16px]">
          <p className="text-[24px] font-bold text-[#8b95a1]">얼마를 보내시겠어요?</p>
          <p className="text-[14px]">
            <span className="text-[#8b95a1]">출금 가능금액 </span>
            <span className="font-semibold text-[#6b7684]">{formatWon(availableBalance)}</span>
          </p>
        </div>
        <div className="flex h-[86px] items-center px-[24px]">
          <p className="text-[30px] font-bold text-[#191f28]">{formatWon(phoneAmountValue)}</p>
        </div>
        <div className="min-h-px flex-1" />
        <div className="flex gap-[8px] px-[24px] pb-[16px]">
          {CHIPS.map((chip) => (
            <button
              key={chip.label}
              type="button"
              onClick={() => setPhoneAmount(String(Math.min(phoneAmountValue + chip.delta, availableBalance)))}
              className="flex-1 rounded-[10px] bg-[#edf1f7] py-[12px]"
            >
              <p className="text-[14px] font-semibold text-[#4262ff]">{chip.label}</p>
            </button>
          ))}
          <button
            type="button"
            onClick={() => setPhoneAmount(String(availableBalance))}
            className="flex-1 rounded-[10px] bg-[#edf1f7] py-[12px]"
          >
            <p className="text-[14px] font-semibold text-[#4262ff]">전액</p>
          </button>
        </div>
        <AmountKeypad
          onDigit={(d) => setPhoneAmount((cur) => appendAmountDigit(cur, d))}
          onBackspace={() => setPhoneAmount((cur) => removeLastAmountDigit(cur))}
        />
        <div className="w-full px-[16px] pb-[16px] pt-[8px]">
          <button
            type="button"
            disabled={phoneAmountValue <= 0 || phoneAmountValue > availableBalance}
            onClick={() => setPhoneStep("complete")}
            className={
              phoneAmountValue > 0 && phoneAmountValue <= availableBalance
                ? "w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
                : "w-full rounded-[14px] bg-[#f2f4f6] py-[17px] text-[17px] font-semibold text-[#b0b8c1]"
            }
          >
            다음
          </button>
        </div>
      </MobileScreen>
    );
  }

  if (accountStep === "amount") {
    return (
      <MobileScreen className="items-start">
        <MobileNavBar
          onBack={() => setAccountStep("account")}
          onCancel={() => router.push(`/accounts/${accountId}`)}
        />
        <div className="flex w-full flex-col gap-[8px] px-[24px] pt-[16px]">
          <div className="flex items-center gap-[8px]">
            <span className="size-[22px] rounded-full bg-[#4262ff]" />
            <p className="text-[15px] font-semibold text-[#191f28]">
              {accountQuery.data?.accountNumber} 에서
            </p>
          </div>
          <div className="flex items-center gap-[8px]">
            <span className="size-[22px] rounded-full bg-[#f5c842]" />
            <p className="text-[15px] font-semibold text-[#191f28]">{toAccountNumber} 로</p>
          </div>
        </div>
        <div className="flex w-full flex-col gap-[8px] px-[24px] pb-[8px] pt-[16px]">
          <p className="text-[24px] font-bold text-[#8b95a1]">얼마를 보내시겠어요?</p>
          <p className="text-[14px]">
            <span className="text-[#8b95a1]">출금 가능금액 </span>
            <span className="font-semibold text-[#6b7684]">{formatWon(availableBalance)}</span>
          </p>
        </div>
        <div className="flex h-[86px] items-center px-[24px]">
          <p className="text-[30px] font-bold text-[#191f28]">{formatWon(amountValue)}</p>
        </div>
        <div className="min-h-px flex-1" />
        <div className="flex gap-[8px] px-[24px] pb-[16px]">
          {CHIPS.map((chip) => (
            <button
              key={chip.label}
              type="button"
              onClick={() => setAmount(String(Math.min(amountValue + chip.delta, availableBalance)))}
              className="flex-1 rounded-[10px] bg-[#edf1f7] py-[12px]"
            >
              <p className="text-[14px] font-semibold text-[#4262ff]">{chip.label}</p>
            </button>
          ))}
          <button
            type="button"
            onClick={() => setAmount(String(availableBalance))}
            className="flex-1 rounded-[10px] bg-[#edf1f7] py-[12px]"
          >
            <p className="text-[14px] font-semibold text-[#4262ff]">전액</p>
          </button>
        </div>
        <AmountKeypad
          onDigit={(d) => setAmount((cur) => appendAmountDigit(cur, d))}
          onBackspace={() => setAmount((cur) => removeLastAmountDigit(cur))}
        />
        <div className="w-full px-[16px] pb-[16px] pt-[8px]">
          <button
            type="button"
            disabled={amountValue <= 0 || amountValue > availableBalance}
            onClick={submitTransferInput}
            className={
              amountValue > 0 && amountValue <= availableBalance
                ? "w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
                : "w-full rounded-[14px] bg-[#f2f4f6] py-[17px] text-[17px] font-semibold text-[#b0b8c1]"
            }
          >
            다음
          </button>
        </div>
      </MobileScreen>
    );
  }

  return (
    <MobileScreen className="items-start">
      <MobileNavBar onBack={() => router.back()} onCancel={() => router.push(`/accounts/${accountId}`)} />
      <div className="flex w-full flex-col px-[24px] pb-[24px] pt-[16px]">
        <p className="text-[24px] font-bold leading-[30px] text-[#191f28]">어디로 돈을 보낼까요?</p>
      </div>

      <div className="flex w-full px-[24px] pb-[20px]">
        <div className="flex w-full rounded-[12px] bg-[#edf1f7] p-[4px]">
          <button
            type="button"
            onClick={() => setMode("account")}
            className={
              mode === "account"
                ? "flex-1 rounded-[9px] bg-white py-[11px] text-[15px] font-semibold text-[#191f28]"
                : "flex-1 rounded-[9px] py-[11px] text-[15px] font-semibold text-[#8b95a5]"
            }
          >
            계좌번호
          </button>
          <button
            type="button"
            onClick={() => setMode("phone")}
            className={
              mode === "phone"
                ? "flex-1 rounded-[9px] bg-white py-[11px] text-[15px] font-semibold text-[#191f28]"
                : "flex-1 rounded-[9px] py-[11px] text-[15px] font-semibold text-[#8b95a5]"
            }
          >
            휴대폰번호
          </button>
        </div>
      </div>

      {mode === "account" ? (
        <>
          <div className="flex w-full px-[24px] pb-[32px]">
            <input
              autoFocus
              inputMode="numeric"
              value={accountDigits ? toAccountNumber : ""}
              onChange={(e) => setAccountDigits(e.target.value.replace(/\D/g, "").slice(0, 10))}
              onKeyDown={(e) => e.key === "Enter" && goToAmountStep()}
              placeholder="계좌번호 입력"
              className="w-full rounded-[14px] border border-[#e0e6f1] p-[20px] text-[16px] text-[#191f28] placeholder:text-[#b0b8c4] focus:outline-none"
            />
          </div>
          {accountError ? (
            <p className="w-full px-[24px] pb-[12px] text-[13px] text-[#f04452]">{accountError}</p>
          ) : null}
          {accountValid ? (
            <div className="w-full px-[24px] pb-[24px]">
              <button
                type="button"
                onClick={goToAmountStep}
                className="w-full rounded-[14px] bg-[#0114a7] py-[16px] text-[17px] font-semibold text-white"
              >
                다음
              </button>
            </div>
          ) : null}

          <div className="w-full px-[24px] pb-[12px]">
            <p className="text-[15px] font-bold text-[#191f28]">내 계좌</p>
          </div>
          {(accountsQuery.data ?? [])
            .filter((a) => a.accountId !== accountId)
            .map((account) => (
              <button
                key={account.accountId}
                type="button"
                onClick={() => selectMyAccount(account)}
                className="flex w-full items-center gap-[12px] px-[24px] py-[8px]"
              >
                <div className="flex size-[40px] items-center justify-center rounded-full bg-[#3350f5]">
                  <Landmark className="size-[18px] text-white" strokeWidth={1.8} />
                </div>
                <div className="flex flex-1 flex-col items-start gap-[1px]">
                  <p className="text-[16px] font-semibold text-[#191f28]">입출금통장</p>
                  <p className="text-[13px] text-[#8b95a5]">{account.accountNumber}</p>
                </div>
                <Star className="size-[22px] text-[#e0e6f1]" strokeWidth={1.8} />
              </button>
            ))}

          <div className="flex w-full justify-center pt-[24px]">
            <div className="flex items-center gap-[6px] rounded-full bg-white px-[16px] py-[12px] shadow-[0px_2px_8px_0px_rgba(0,0,0,0.08)]">
              <span className="size-[16px] rounded-full bg-[#4262ff]" />
              <p className="text-[14px] font-semibold text-[#191f28]">한 글자만 쳐도 찾아줘요</p>
            </div>
          </div>
        </>
      ) : (
        <div className="flex w-full flex-col">
          <div className="px-[24px]">
            <div className="flex w-full items-center gap-[10px] rounded-[16px] border border-[#e0e6f1] px-[20px] py-[16px]">
              <input
                value={contactQuery}
                onChange={(e) => setContactQuery(e.target.value)}
                placeholder="이름 또는 휴대폰 번호 입력"
                className="flex-1 bg-transparent text-[16px] text-[#191f28] placeholder:text-[#8b95a5] focus:outline-none"
              />
              <Search className="size-[20px] text-[#8b95a5]" strokeWidth={1.8} />
            </div>
          </div>
          <div className="px-[24px] pb-[12px] pt-[24px]">
            <p className="text-[15px] font-bold text-[#191f28]">모든 연락처</p>
          </div>
          {filteredContacts.map((contact) => (
            <button
              key={contact.phone}
              type="button"
              onClick={() => selectContact(contact)}
              className="flex w-full items-center gap-[12px] px-[24px] py-[8px]"
            >
              <div className="flex size-[40px] items-center justify-center rounded-full bg-[#edf1f7]">
                <p className="text-[14px] font-semibold text-[#6b7684]">{contact.name.slice(0, 1)}</p>
              </div>
              <div className="flex flex-col items-start gap-[1px]">
                <p className="text-[16px] font-semibold text-[#191f28]">{contact.name}</p>
                <p className="text-[13px] text-[#8b95a5]">{contact.phone}</p>
              </div>
            </button>
          ))}
        </div>
      )}

      {mode === "phone" && !phoneTermsAgreed ? (
        <BottomSheet onClose={() => setMode("account")}>
          <div className="flex flex-col px-[20px]">
            <p className="text-[19px] font-bold leading-[29px] text-[#191f28]">
              연락처로 돈을 보내시려면
              <br />
              약관에 동의해주세요
            </p>
            <div className="h-[7px]" />
            <p className="text-[14px] text-[#6b7684]">상대방의 휴대폰 번호로 바로 이체할 수 있어요</p>
            <div className="h-[35px]" />
            <div className="flex flex-col rounded-[14px] border border-[#f2f4f6]">
              <div className="flex items-center gap-[12px] p-[16px]">
                <Check className="size-[24px] text-[#0114a7]" strokeWidth={2} />
                <p className="flex-1 text-[15px] font-semibold text-[#191f28]">필수 항목 모두 동의</p>
              </div>
              <div className="h-px w-full bg-[#f2f4f6]" />
              <div className="flex flex-col gap-[4px] px-[16px] py-[12px] text-[13px] text-[#8b95a5]">
                <p>연락처 송금 서비스 설명서</p>
                <p>연락처 송금 이용약관</p>
                <p>개인정보 수집 및 이용동의</p>
              </div>
            </div>
            <div className="h-[38px]" />
            <button
              type="button"
              onClick={() => setPhoneTermsAgreed(true)}
              className="w-full rounded-[14px] bg-[#0114a7] py-[16px] text-[17px] font-semibold text-white"
            >
              약관 동의 하고 돈 보내기
            </button>
          </div>
        </BottomSheet>
      ) : null}
    </MobileScreen>
  );
}
