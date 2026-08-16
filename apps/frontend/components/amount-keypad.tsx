"use client";

import { Delete } from "lucide-react";

const KEYS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "back"];

/** 입출금·이체 금액 입력용 숫자 키패드. pin-keypad.tsx와 같은 그리드 패턴, 셔플 없이 자연 순서. */
export function AmountKeypad({
  onDigit,
  onBackspace,
}: {
  onDigit: (digit: string) => void;
  onBackspace: () => void;
}) {
  return (
    <div className="flex w-full flex-col items-start px-[8px]">
      {[0, 1, 2, 3].map((row) => (
        <div key={row} className="flex w-full items-start">
          {KEYS.slice(row * 3, row * 3 + 3).map((key, i) => (
            <button
              key={`${row}-${i}`}
              type="button"
              onClick={() => (key === "back" ? onBackspace() : onDigit(key))}
              className="flex h-[58px] flex-1 items-center justify-center"
            >
              {key === "back" ? (
                <Delete className="size-[24px] text-[#191f28]" strokeWidth={1.6} />
              ) : (
                <span className="text-[26px] font-normal leading-[32px] text-[#191f28]">{key}</span>
              )}
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}
