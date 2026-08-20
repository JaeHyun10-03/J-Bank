"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/lib/auth-store";
import { MobileScreen } from "@/components/mobile-screen";
import { cn } from "@/lib/utils";

// ponytail: 알림/화면 모드/PC 로그인/오픈뱅킹은 아직 도메인·API가 없어 피그마 시안대로
// 정적 행만 둔다. 실제 설정 화면이 생기면 그때 라우팅을 연결한다.
const APP_SETTINGS = [
  { key: "notification", label: "알림" },
  { key: "screen-mode", label: "화면 모드", badge: "Beta" },
] as const;

export default function SettingsPage() {
  const router = useRouter();
  const logout = useAuthStore((state) => state.logout);
  const [simpleHome, setSimpleHome] = useState(false);

  async function handleLogout() {
    try {
      await apiClient.post("/auth/logout");
    } finally {
      logout();
      router.push("/login");
    }
  }

  return (
    <MobileScreen>
      <div className="relative flex w-full items-center justify-center pb-[12px] pt-[20px]">
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="뒤로가기"
          className="absolute left-[20px] flex size-[20px] items-center justify-center"
        >
          <ChevronLeft className="size-[20px] text-[#191f28]" strokeWidth={2} />
        </button>
        <p className="text-[17px] font-bold text-[#191f28]">설정</p>
      </div>

      <div className="flex w-full px-[16px] py-[12px]">
        <div className="flex flex-1 items-center justify-between rounded-[12px] bg-[#f7f9fd] px-[18px] py-[16px]">
          <div className="flex items-center gap-[8px]">
            <span className="size-[16px] rounded-full bg-[#191f28]" />
            <p className="text-[15px] font-medium text-[#6b7684]">안전하게 로그아웃 하세요</p>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="text-[15px] font-semibold text-[#191f28]"
          >
            로그아웃
          </button>
        </div>
      </div>

      <div className="flex w-full flex-col px-[24px] pt-[24px]">
        <p className="text-[17px] font-bold text-[#191f28]">앱 설정</p>
        <div className="h-[8px] w-full" />
        {APP_SETTINGS.map((item, i) => (
          <div
            key={item.key}
            className={cn(
              "flex w-full items-center justify-between py-[18px]",
              i > 0 && "border-t border-[#edf1f7]",
            )}
          >
            <div className="flex items-center gap-[8px]">
              <p className="text-[16px] text-[#191f28]">{item.label}</p>
              {"badge" in item ? (
                <span className="rounded-[6px] bg-[#edf1f7] px-[7px] py-[3px] text-[11px] font-semibold text-[#4262ff]">
                  {item.badge}
                </span>
              ) : null}
            </div>
            <ChevronRight className="size-[16px] text-[#8b95a5]" strokeWidth={2} />
          </div>
        ))}
        <div className="h-px w-full bg-[#edf1f7]" />
      </div>

      <div className="flex w-full flex-col px-[24px] pt-[24px]">
        <p className="text-[17px] font-bold text-[#191f28]">보기 모드</p>
        <div className="h-[8px] w-full" />
        <div className="flex w-full items-center justify-between py-[18px]">
          <p className="text-[16px] text-[#191f28]">간편 홈</p>
          <button
            type="button"
            role="switch"
            aria-checked={simpleHome}
            onClick={() => setSimpleHome((v) => !v)}
            className={cn(
              "relative h-[28px] w-[50px] rounded-full transition-colors",
              simpleHome ? "bg-[#4262ff]" : "bg-[#e0e6f1]",
            )}
          >
            <span
              className={cn(
                "absolute top-[3px] size-[22px] rounded-full bg-white transition-transform",
                simpleHome ? "translate-x-[25px]" : "translate-x-[3px]",
              )}
            />
            {!simpleHome ? (
              <p className="absolute right-[8px] top-[9px] text-[10px] font-bold text-[#8b95a5]">
                OFF
              </p>
            ) : null}
          </button>
        </div>
        <div className="h-px w-full bg-[#edf1f7]" />
      </div>

      <div className="flex w-full flex-col px-[24px] pt-[24px]">
        <p className="text-[17px] font-bold text-[#191f28]">로그인</p>
        <div className="h-[8px] w-full" />
        <div className="flex w-full items-center justify-between py-[18px]">
          <p className="text-[16px] text-[#191f28]">PC 로그인</p>
          <ChevronRight className="size-[16px] text-[#8b95a5]" strokeWidth={2} />
        </div>
        <div className="h-px w-full bg-[#edf1f7]" />
      </div>

      <div className="flex w-full flex-col px-[24px] pt-[24px] pb-[24px]">
        <p className="text-[17px] font-bold text-[#191f28]">서비스 설정</p>
        <div className="h-[8px] w-full" />
        <div className="flex w-full items-center justify-between py-[18px]">
          <p className="text-[16px] text-[#191f28]">오픈뱅킹</p>
          <ChevronRight className="size-[16px] text-[#8b95a5]" strokeWidth={2} />
        </div>
      </div>
    </MobileScreen>
  );
}
