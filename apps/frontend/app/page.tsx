"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Bell, Copy, Home, ShoppingBag, Gift, Star, Menu } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/lib/auth-store";
import { formatWon, isPositiveAmount } from "@/lib/format";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";

type ApiResponsePage =
  components["schemas"]["ApiResponsePageResponseCustomerAccountSummaryResponse"];

const TABS = [
  { label: "홈", href: "/", active: true, Icon: Home },
  { label: "상품", href: "/products", active: false, Icon: ShoppingBag },
  { label: "혜택", href: null, active: false, Icon: Gift },
  { label: "서비스", href: null, active: false, Icon: Star },
  { label: "전체", href: null, active: false, Icon: Menu },
];

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

  return (
    <MobileScreen className="bg-[#edf1f7]">
      <div className="flex w-full items-center justify-between px-[24px] pb-[16px] pt-[20px]">
        <p className="text-[24px] font-bold text-[#191f28]">홈</p>
        <div className="flex items-center gap-[10px]">
          {name ? (
            <div className="flex items-center gap-[5px] rounded-full bg-white px-[12px] py-[7px]">
              <p className="text-[13px] font-medium text-[#6b7684]">{name}님</p>
            </div>
          ) : null}
          <Bell className="size-[20px] text-[#8b95a1]" strokeWidth={1.8} />
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
        ) : data && data.length > 0 ? (
          data.map((account) => (
            <div
              key={account.accountId}
              className="flex w-full flex-col gap-[16px] rounded-[20px] bg-white p-[20px]"
            >
              <div className="flex w-full items-center justify-between">
                <Link
                  href={`/accounts/${account.accountId}`}
                  className="flex items-center gap-[6px]"
                >
                  <p className="text-[14px] font-medium text-[#6b7684]">
                    {account.accountNumber}
                  </p>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.preventDefault();
                      navigator.clipboard.writeText(account.accountNumber ?? "");
                    }}
                  >
                    <Copy className="size-[14px] text-[#8b95a5]" strokeWidth={1.8} />
                  </button>
                </Link>
              </div>
              <p className="text-[28px] font-bold text-[#191f28]">{formatWon(account.balance)}</p>
              {isPositiveAmount(account.holdAmount) ? (
                <p className="text-[13px] text-[#8b95a1]">
                  출금 가능 {formatWon(account.availableBalance)} (지급정지{" "}
                  {formatWon(account.holdAmount)})
                </p>
              ) : null}
              <Link
                href={`/accounts/${account.accountId}/transfer`}
                className="flex w-full items-center justify-center rounded-[10px] bg-[#edf1f7] py-[13px]"
              >
                <p className="text-[15px] font-semibold text-[#4262ff]">이체하기</p>
              </Link>
            </div>
          ))
        ) : (
          <p className="text-[14px] text-[#6b7684]">보유한 계좌가 없습니다.</p>
        )}
      </div>

      <div className="sticky bottom-0 flex h-[83px] w-full items-center justify-between border-t border-[#edf1f7] bg-white px-[8px] pb-[24px] pt-[10px]">
        {TABS.map(({ label, href, active, Icon }) => {
          const content = (
            <>
              <Icon
                className={active ? "size-[23px] text-[#4262ff]" : "size-[23px] text-[#8b95a5]"}
                strokeWidth={1.8}
              />
              <p className={active ? "text-[11px] font-semibold text-[#4262ff]" : "text-[11px] text-[#8b95a5]"}>
                {label}
              </p>
            </>
          );
          return href ? (
            <Link key={label} href={href} className="flex flex-1 flex-col items-center justify-center gap-[5px]">
              {content}
            </Link>
          ) : (
            <div key={label} className="flex flex-1 flex-col items-center justify-center gap-[5px]">
              {content}
            </div>
          );
        })}
      </div>
    </MobileScreen>
  );
}
