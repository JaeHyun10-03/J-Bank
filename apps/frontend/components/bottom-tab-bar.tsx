"use client";

import Link from "next/link";
import { Home, ShoppingBag, Gift, Star, Menu } from "lucide-react";

const TABS = [
  { label: "홈", href: "/", Icon: Home },
  { label: "상품", href: "/products", Icon: ShoppingBag },
  { label: "혜택", href: "/benefits", Icon: Gift },
  { label: "서비스", href: "/services", Icon: Star },
  { label: "전체", href: null, Icon: Menu },
] as const;

/** 앱 전역 하단 탭바. 피그마 TabBar 컴포넌트, 홈/상품 화면에서 공유한다. */
export function BottomTabBar({ active }: { active: "홈" | "상품" | "혜택" | "서비스" | "전체" }) {
  return (
    <div className="sticky bottom-0 flex h-[83px] w-full items-center justify-between border-t border-[#edf1f7] bg-white px-[8px] pb-[24px] pt-[10px]">
      {TABS.map(({ label, href, Icon }) => {
        const isActive = label === active;
        const content = (
          <>
            <Icon
              className={isActive ? "size-[23px] text-[#4262ff]" : "size-[23px] text-[#8b95a5]"}
              strokeWidth={1.8}
            />
            <p
              className={
                isActive
                  ? "text-[11px] font-semibold text-[#4262ff]"
                  : "text-[11px] text-[#8b95a5]"
              }
            >
              {label}
            </p>
          </>
        );
        return href ? (
          <Link
            key={label}
            href={href}
            className="flex flex-1 flex-col items-center justify-center gap-[5px]"
          >
            {content}
          </Link>
        ) : (
          <div key={label} className="flex flex-1 flex-col items-center justify-center gap-[5px]">
            {content}
          </div>
        );
      })}
    </div>
  );
}
