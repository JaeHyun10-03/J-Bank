"use client";

import { useRouter } from "next/navigation";
import { ChevronDown, ChevronRight, MessageSquare } from "lucide-react";
import { formatWon } from "@/lib/format";
import { useAuthStore } from "@/lib/auth-store";
import { MobileScreen } from "@/components/mobile-screen";

/** 이체 확인(즉시 완료)과 OTP 검증 성공, 두 지점 모두에서 쓰는 완료 화면. Transfer4-Complete 프레임 참고. */
export function TransferCompleteCard({
  accountId,
  toAccountNumber,
  amount,
  onConfirm,
}: {
  accountId: string;
  toAccountNumber: string;
  amount: number;
  onConfirm: () => void;
}) {
  const router = useRouter();
  const name = useAuthStore((state) => state.name);

  return (
    <MobileScreen className="items-center px-[24px] pb-[24px] pt-[80px]">
      <ChevronDown className="size-[22px] text-[#8b95a5]" strokeWidth={2.5} />
      <p className="mt-[24px] text-center text-[24px] font-bold leading-[34px] text-[#191f28]">
        {formatWon(amount)}을
        <br />
        보냈어요
      </p>
      <div className="mt-[24px] flex items-center gap-[8px]">
        <p className="text-[14px] text-[#8b95a5]">받는계좌</p>
        <div className="h-[12px] w-px bg-[#e0e6f1]" />
        <p className="text-[14px] font-medium text-[#6b7684]">{toAccountNumber}</p>
      </div>

      <div className="min-h-px w-full flex-1" />

      <button
        type="button"
        className="flex w-full items-center justify-between rounded-[14px] bg-[#edf1f7] px-[20px] py-[16px]"
      >
        <div className="flex items-center gap-[10px]">
          <MessageSquare className="size-[20px] text-[#8b95a5]" strokeWidth={1.8} />
          <div className="flex flex-col items-start gap-[3px]">
            <p className="text-[12px] text-[#8b95a5]">제이뱅크 이체하기</p>
            <p className="text-[15px] font-bold text-[#191f28]">
              {name ?? "고객"}님의 의견이 궁금해요
            </p>
          </div>
        </div>
        <ChevronRight className="size-[18px] text-[#8b95a5]" strokeWidth={2} />
      </button>

      <div className="mt-[12px] flex w-full gap-[8px]">
        <button
          type="button"
          onClick={() => router.push(`/accounts/${accountId}/transfer`)}
          className="flex-1 rounded-[14px] bg-[#edf1f7] py-[17px] text-[17px] font-semibold text-[#191f28]"
        >
          추가이체
        </button>
        <button
          type="button"
          onClick={onConfirm}
          className="flex-[2] rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
        >
          확인
        </button>
      </div>
    </MobileScreen>
  );
}
