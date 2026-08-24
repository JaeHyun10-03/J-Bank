"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { resolveDomainError } from "@/lib/domain-error-map";
import { MobileScreen } from "@/components/mobile-screen";
import { MobileNavBar } from "@/components/mobile-nav-bar";
import { BottomSheet } from "@/components/bottom-sheet";

export default function AccountClosePage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [closed, setClosed] = useState(false);

  async function submit() {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await apiClient.delete(`/accounts/${accountId}`);
      setClosed(true);
    } catch (error) {
      setErrorMessage(resolveDomainError(getApiError(error)).message);
    } finally {
      setSubmitting(false);
    }
  }

  if (closed) {
    return (
      <MobileScreen className="items-center justify-center gap-[16px] px-[24px] text-center">
        <p className="text-[20px] font-bold text-[#191f28]">계좌해지 완료</p>
        <p className="text-[14px] text-[#6b7684]">계좌가 해지됐습니다.</p>
        <button
          type="button"
          onClick={() => router.replace("/")}
          className="mt-[8px] flex w-full items-center justify-center rounded-[10px] bg-[#4262ff] py-[13px]"
        >
          <p className="text-[15px] font-semibold text-white">홈으로</p>
        </button>
      </MobileScreen>
    );
  }

  return (
    <MobileScreen className="items-start">
      <MobileNavBar onBack={() => router.back()} />
      <div className="flex w-full flex-col px-[24px] pb-[24px] pt-[16px]">
        <p className="text-[24px] font-bold text-[#191f28]">계좌해지</p>
        <p className="mt-[8px] text-[14px] text-[#8b95a5]">
          잔액과 지급정지 금액이 모두 0원이어야 해지할 수 있어요.
        </p>
        {errorMessage ? (
          <p className="mt-[16px] text-[13px] text-[#f04452]">{errorMessage}</p>
        ) : null}
      </div>
      <div className="min-h-px w-full flex-1" />
      <div className="w-full px-[16px] pb-[16px]">
        <ConfirmTrigger submitting={submitting} onConfirm={submit} />
      </div>
    </MobileScreen>
  );
}

function ConfirmTrigger({
  submitting,
  onConfirm,
}: {
  submitting: boolean;
  onConfirm: () => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        disabled={submitting}
        onClick={() => setOpen(true)}
        className="w-full rounded-[14px] bg-[#f04452] py-[17px] text-[17px] font-semibold text-white disabled:opacity-60"
      >
        계좌해지
      </button>
      {open ? (
        <BottomSheet onClose={() => setOpen(false)}>
          <div className="flex flex-col px-[20px]">
            <p className="text-[19px] font-bold text-[#191f28]">정말 해지하시겠어요?</p>
            <p className="mt-[8px] text-[14px] text-[#6b7684]">이 작업은 되돌릴 수 없어요.</p>
            <div className="h-[24px]" />
            <button
              type="button"
              disabled={submitting}
              onClick={() => {
                setOpen(false);
                onConfirm();
              }}
              className="w-full rounded-[14px] bg-[#f04452] py-[16px] text-[17px] font-semibold text-white disabled:opacity-60"
            >
              해지할게요
            </button>
          </div>
        </BottomSheet>
      ) : null}
    </>
  );
}
