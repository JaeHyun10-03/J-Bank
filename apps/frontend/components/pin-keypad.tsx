"use client";

import { useState } from "react";
import { Delete } from "lucide-react";
import { cn } from "@/lib/utils";
import { usePressFeedback } from "@/lib/use-press-feedback";

const PIN_LENGTH = 6;
const ORDERED_DIGITS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "back"];

function shuffledDigits(): string[] {
  const digits = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"];
  for (let i = digits.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [digits[i], digits[j]] = [digits[j], digits[i]];
  }
  return digits;
}

export function PinDots({ value }: { value: string }) {
  return (
    <div className="flex w-full items-center justify-center gap-[16.6px] pt-[70px]">
      {Array.from({ length: PIN_LENGTH }).map((_, i) =>
        i < value.length ? (
          <div key={i} className="size-[12px] rounded-full bg-[#0414a7]" />
        ) : (
          <div key={i} className="h-[1.5px] w-[16.6px] rounded-[1px] bg-[#d8d8d8]" />
        ),
      )}
    </div>
  );
}

export function PinKeypad({ onDigit, onBackspace }: { onDigit: (d: string) => void; onBackspace: () => void }) {
  const [shuffled, setShuffled] = useState(false);
  const [digits, setDigits] = useState(() => ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]);
  const { pressed, onPressStart, onPressEnd } = usePressFeedback();

  const keys = shuffled
    ? [...digits.slice(0, 9), "재배열", digits[9], "back"]
    : ORDERED_DIGITS.map((k) => (k === "" ? "재배열" : k));

  function handleShuffle() {
    setDigits(shuffledDigits());
    setShuffled(true);
  }

  return (
    <div className="flex w-full flex-col items-start bg-[#f7f8fb] pb-[18px] pt-[6px]">
      {[0, 1, 2, 3].map((row) => (
        <div key={row} className="flex w-full items-start">
          {keys.slice(row * 3, row * 3 + 3).map((key, i) => {
            const keyId = `${row}-${i}`;
            return (
              <button
                key={keyId}
                type="button"
                onClick={() => {
                  if (key === "재배열") handleShuffle();
                  else if (key === "back") onBackspace();
                  else onDigit(key);
                }}
                onPointerDown={() => onPressStart(keyId)}
                onPointerUp={onPressEnd}
                onPointerLeave={onPressEnd}
                onPointerCancel={onPressEnd}
                className={cn(
                  "flex h-[58px] flex-1 items-center justify-center rounded-[12px] transition-colors",
                  pressed === keyId && "bg-[#f2f4f6]",
                )}
              >
                {key === "재배열" ? (
                  <span className="text-[14px] font-medium leading-[20px] text-[#6b7684]">재배열</span>
                ) : key === "back" ? (
                  <Delete className="size-[26px] text-[#191f28]" strokeWidth={1.6} />
                ) : (
                  <span className="text-[24px] font-medium leading-[32px] text-[#191f28]">{key}</span>
                )}
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
}
