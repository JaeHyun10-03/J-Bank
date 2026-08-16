import { formatWon } from "@/lib/format";
import { MobileScreen } from "@/components/mobile-screen";

/** 이체 확인(즉시 완료)과 OTP 검증 성공, 두 지점 모두에서 쓰는 완료 화면. Transfer4-Complete 프레임 참고. */
export function TransferCompleteCard({
  toAccountNumber,
  amount,
  onConfirm,
}: {
  toAccountNumber: string;
  amount: number;
  onConfirm: () => void;
}) {
  return (
    <MobileScreen className="items-center justify-center px-[24px] text-center">
      <p className="text-[24px] font-bold leading-[34px] text-[#191f28]">
        {formatWon(amount)}을 보냈어요
      </p>
      <p className="mt-[8px] text-[14px] text-[#8b95a5]">받는계좌 {toAccountNumber}</p>
      <button
        type="button"
        onClick={onConfirm}
        className="mt-[32px] w-full rounded-[14px] bg-[#0114a7] py-[17px] text-[17px] font-semibold text-white"
      >
        확인
      </button>
    </MobileScreen>
  );
}
