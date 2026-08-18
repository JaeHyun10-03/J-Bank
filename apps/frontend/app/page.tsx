"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  Bell,
  Copy,
  MoreVertical,
  ChevronRight,
  Landmark,
  Receipt,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/lib/auth-store";
import { formatWon, isPositiveAmount } from "@/lib/format";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";
import { BottomTabBar } from "@/components/bottom-tab-bar";

type ApiResponsePage =
  components["schemas"]["ApiResponsePageResponseCustomerAccountSummaryResponse"];

export default function HomePage() {
  const router = useRouter();
  const customerId = useAuthStore((state) => state.customerId);
  const name = useAuthStore((state) => state.name);
  const logout = useAuthStore((state) => state.logout);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["accounts", customerId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponsePage>(
        `/customers/${customerId}/accounts`,
        { params: { page: 0, size: 20 } },
      );
      return response.data.data?.content ?? [];
    },
    enabled: !!customerId,
  });

  async function handleLogout() {
    try {
      await apiClient.post("/auth/logout");
    } finally {
      logout();
      router.push("/login");
    }
  }

  const accounts = data ?? [];
  const primary = accounts[0];
  const others = accounts.slice(1);

  return (
    <MobileScreen className="bg-[#edf1f7]">
      <div className="flex w-full items-center justify-between px-[24px] pb-[16px] pt-[20px]">
        <p className="text-[24px] font-bold text-[#191f28]">홈</p>
        <div className="flex items-center gap-[10px]">
          {name ? (
            <div className="flex items-center gap-[5px] rounded-full bg-white px-[12px] py-[7px]">
              <span className="size-[6px] rounded-full bg-[#8b95a5]" />
              <p className="text-[13px] font-medium text-[#6b7684]">{name}님</p>
            </div>
          ) : null}
          <Bell className="size-[24px] text-[#191f28]" strokeWidth={1.8} />
          <button
            type="button"
            onClick={handleLogout}
            className="text-[13px] font-medium text-[#8b95a1] underline underline-offset-2"
          >
            로그아웃
          </button>
        </div>
      </div>

      <div className="flex w-full flex-1 flex-col gap-[12px] px-[16px] pb-[24px]">
        {!customerId ? (
          <p className="text-[14px] text-[#6b7684]">로그인이 필요합니다.</p>
        ) : isLoading ? (
          <p className="text-[14px] text-[#6b7684]">불러오는 중...</p>
        ) : isError ? (
          <p className="text-[14px] text-[#f04452]">계좌 목록을 불러오지 못했습니다.</p>
        ) : primary ? (
          <>
            <div className="flex w-full flex-col gap-[16px] rounded-[20px] bg-white p-[20px]">
              <div className="flex w-full items-center justify-between">
                <Link
                  href={`/accounts/${primary.accountId}`}
                  className="flex items-center gap-[6px]"
                >
                  <p className="text-[14px] font-medium text-[#6b7684]">
                    입출금통장 {primary.accountNumber}
                  </p>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.preventDefault();
                      navigator.clipboard.writeText(primary.accountNumber ?? "");
                    }}
                  >
                    <Copy className="size-[16px] text-[#8b95a5]" strokeWidth={1.8} />
                  </button>
                </Link>
                {/* ponytail: 계좌 메뉴 UI만 배치, 실제 메뉴 없음 — 오픈뱅킹/자산위젯과 같은 보류 스코프 */}
                <MoreVertical className="size-[16px] text-[#8b95a5]" strokeWidth={1.8} />
              </div>
              <p className="text-[28px] font-bold text-[#191f28]">{formatWon(primary.balance)}</p>
              {isPositiveAmount(primary.holdAmount) ? (
                <p className="-mt-[8px] text-[13px] text-[#8b95a1]">
                  출금 가능 {formatWon(primary.availableBalance)} (지급정지{" "}
                  {formatWon(primary.holdAmount)})
                </p>
              ) : null}
              <div className="flex w-full gap-[8px]">
                <Link
                  href={`/accounts/${primary.accountId}/pull`}
                  className="flex flex-1 items-center justify-center rounded-[10px] bg-[#edf1f7] py-[13px]"
                >
                  <p className="text-[15px] font-semibold text-[#4262ff]">가져오기</p>
                </Link>
                <Link
                  href={`/accounts/${primary.accountId}/transfer`}
                  className="flex flex-1 items-center justify-center rounded-[10px] bg-[#edf1f7] py-[13px]"
                >
                  <p className="text-[15px] font-semibold text-[#4262ff]">이체하기</p>
                </Link>
              </div>
              <div className="h-px w-full bg-[#edf1f7]" />
              <div className="flex w-full items-center gap-[12px]">
                <div className="flex h-[32px] w-[48px] items-center justify-center rounded-[6px] bg-[#191f28]">
                  <p className="text-[7px] font-bold text-white">J-Bank</p>
                </div>
                <div className="flex flex-1 flex-col gap-[3px]">
                  <p className="text-[13px] font-medium text-[#6b7684]">이번 달 카드로 쓴 돈</p>
                  <p className="text-[18px] font-bold text-[#191f28]">0원</p>
                </div>
              </div>
              <div className="flex w-full items-center justify-center gap-[6px]">
                {[0, 1, 2, 3].map((i) => (
                  <span
                    key={i}
                    className={
                      i === 0
                        ? "size-[6px] rounded-full bg-[#191f28]"
                        : "size-[6px] rounded-full bg-[#e0e6f1]"
                    }
                  />
                ))}
              </div>
            </div>

            <div className="flex w-full flex-col gap-[16px] rounded-[20px] bg-white p-[20px]">
              <div className="flex w-full items-center justify-between">
                <p className="text-[20px] font-bold text-[#191f28]">내 자산</p>
                <div className="flex items-center gap-[4px] text-[#8b95a5]">
                  <p className="text-[14px] font-medium">편집</p>
                  <div className="flex size-[14px] items-center justify-center rounded-full border border-[#e0e6f1]">
                    <ChevronRight className="size-[10px]" strokeWidth={2} />
                  </div>
                </div>
              </div>
              {others.length > 0 ? (
                <>
                  <p className="text-[15px] font-bold text-[#191f28]">계좌</p>
                  {others.map((account) => (
                    <div key={account.accountId} className="flex w-full items-center gap-[12px]">
                      <div className="flex size-[40px] items-center justify-center rounded-full bg-[#edf1f7]">
                        <Landmark className="size-[18px] text-[#6b7684]" strokeWidth={1.8} />
                      </div>
                      <div className="flex flex-1 flex-col gap-[3px]">
                        <p className="text-[14px] font-medium text-[#6b7684]">
                          {account.accountNumber}
                        </p>
                        <p className="text-[18px] font-bold text-[#191f28]">
                          {formatWon(account.balance)}
                        </p>
                      </div>
                      <Link
                        href={`/accounts/${account.accountId}/transfer`}
                        className="rounded-full border border-[#e0e6f1] px-[16px] py-[9px]"
                      >
                        <p className="text-[13px] font-medium text-[#6b7684]">이체</p>
                      </Link>
                    </div>
                  ))}
                </>
              ) : null}
              <div className="flex w-full items-center gap-[12px]">
                <div className="flex size-[40px] items-center justify-center rounded-full bg-[#edf1f7]">
                  <Receipt className="size-[18px] text-[#6b7684]" strokeWidth={1.8} />
                </div>
                <div className="flex flex-1 flex-col gap-[3px]">
                  <p className="text-[14px] font-medium text-[#6b7684]">이번 달 내 지출</p>
                  <p className="text-[18px] font-bold text-[#191f28]">? 원</p>
                </div>
              </div>
            </div>

            <div className="flex w-full items-center justify-between rounded-[20px] bg-[#d1d8e3] px-[20px] py-[18px]">
              <div className="flex items-center gap-[10px]">
                <Bell className="size-[20px] text-[#8b95a5]" strokeWidth={1.6} />
                <div className="flex flex-col gap-[3px]">
                  <p className="text-[12px] text-[#8b95a5]">새로워진 제이뱅크 홈</p>
                  <p className="text-[15px] font-bold text-[#191f28]">
                    {name ?? "고객"}님의 의견이 궁금해요
                  </p>
                </div>
              </div>
              <ChevronRight className="size-[18px] text-[#8b95a5]" strokeWidth={2} />
            </div>

            <div className="flex w-full items-center justify-center gap-[12px] pt-[4px] text-[13px]">
              <p className="text-[#8b95a1]">간편 홈 모드</p>
              <p className="text-[#e0e6f1]">|</p>
              <p className="text-[#8b95a1]">홈 화면 설정</p>
            </div>
          </>
        ) : (
          <div className="flex w-full flex-col items-center gap-[16px] rounded-[20px] bg-white p-[20px]">
            <p className="text-[14px] text-[#6b7684]">보유한 계좌가 없습니다.</p>
            <Link
              href="/accounts/open"
              className="flex w-full items-center justify-center rounded-[10px] bg-[#4262ff] py-[13px]"
            >
              <p className="text-[15px] font-semibold text-white">계좌 개설하기</p>
            </Link>
          </div>
        )}
      </div>

      <BottomTabBar active="홈" />
    </MobileScreen>
  );
}
