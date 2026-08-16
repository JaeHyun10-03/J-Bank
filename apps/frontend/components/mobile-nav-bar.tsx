"use client";

import { ChevronLeft } from "lucide-react";

/** 이체/입출금 화면 상단 내비게이션(뒤로가기 + 선택적 취소). 앱디자인노트 "레이아웃 규칙" 상단바 패턴. */
export function MobileNavBar({
  onBack,
  onCancel,
  cancelLabel = "취소",
}: {
  onBack: () => void;
  onCancel?: () => void;
  cancelLabel?: string;
}) {
  return (
    <div className="flex w-full items-center justify-between px-[20px] pb-[8px] pt-[12px]">
      <button
        type="button"
        onClick={onBack}
        aria-label="뒤로가기"
        className="flex size-[28px] items-center justify-center"
      >
        <ChevronLeft className="size-[20px] text-[#191f28]" strokeWidth={2} />
      </button>
      {onCancel ? (
        <button
          type="button"
          onClick={onCancel}
          className="pr-[4px] text-[16px] font-medium text-[#6b7684]"
        >
          {cancelLabel}
        </button>
      ) : null}
    </div>
  );
}
