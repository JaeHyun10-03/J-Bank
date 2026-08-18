"use client";

import { useEffect, useState } from "react";

/** 딤 배경 위에 아래에서 슬라이드업으로 뜨는 바텀시트. 가져오기·연락처송금 약관에서 공유. */
export function BottomSheet({
  onClose,
  children,
}: {
  onClose: () => void;
  children: React.ReactNode;
}) {
  const [shown, setShown] = useState(false);

  useEffect(() => {
    const id = requestAnimationFrame(() => setShown(true));
    return () => cancelAnimationFrame(id);
  }, []);

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center">
      <div
        className={
          shown
            ? "absolute inset-0 bg-black/40 transition-opacity duration-200 ease-out"
            : "absolute inset-0 bg-black/40 opacity-0 transition-opacity duration-200 ease-out"
        }
        onClick={onClose}
      />
      <div
        className={
          shown
            ? "relative flex w-full max-w-[430px] translate-y-0 flex-col rounded-t-[24px] bg-white pb-[40px] pt-[30px] transition-transform duration-200 ease-out"
            : "relative flex w-full max-w-[430px] translate-y-full flex-col rounded-t-[24px] bg-white pb-[40px] pt-[30px] transition-transform duration-200 ease-out"
        }
      >
        {children}
      </div>
    </div>
  );
}
