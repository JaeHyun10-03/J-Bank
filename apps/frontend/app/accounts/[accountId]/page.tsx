"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { formatWon, formatDateTime, isPositiveAmount } from "@/lib/format";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";

type ApiResponseAccountDetailResponse = components["schemas"]["ApiResponseAccountDetailResponse"];
type ApiResponseBalanceResponse = components["schemas"]["ApiResponseBalanceResponse"];

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "정상",
  SUSPENDED: "정지",
  DORMANT: "휴면",
  CLOSED: "해지",
};

export default function AccountDetailPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();

  const detailQuery = useQuery({
    queryKey: ["account", accountId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponseAccountDetailResponse>(
        `/accounts/${accountId}`,
      );
      return response.data.data;
    },
  });

  const balanceQuery = useQuery({
    queryKey: ["account-balance", accountId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponseBalanceResponse>(
        `/accounts/${accountId}/balance`,
      );
      return response.data.data;
    },
  });

  const forbidden =
    getApiError(detailQuery.error)?.code === "COMMON_003_FORBIDDEN" ||
    getApiError(balanceQuery.error)?.code === "COMMON_003_FORBIDDEN";

  return (
    <MobileScreen className="bg-[#edf1f7]">
      <div className="flex w-full items-center gap-[8px] px-[16px] pb-[16px] pt-[20px]">
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="뒤로가기"
          className="flex size-[28px] items-center justify-center"
        >
          <ChevronLeft className="size-[20px] text-[#191f28]" strokeWidth={2} />
        </button>
        <p className="text-[20px] font-bold text-[#191f28]">계좌상세</p>
      </div>

      <div className="flex w-full flex-1 flex-col gap-[12px] px-[16px] pb-[24px]">
        {forbidden ? (
          <p className="text-[14px] text-[#f04452]">본인 소유의 계좌만 조회할 수 있습니다.</p>
        ) : detailQuery.isLoading || balanceQuery.isLoading ? (
          <p className="text-[14px] text-[#6b7684]">불러오는 중...</p>
        ) : detailQuery.isError || balanceQuery.isError ? (
          <p className="text-[14px] text-[#f04452]">계좌 정보를 불러오지 못했습니다.</p>
        ) : (
          <div className="flex w-full flex-col gap-[16px] rounded-[20px] bg-white p-[20px]">
            <div className="flex items-center justify-between">
              <p className="text-[14px] font-medium text-[#6b7684]">
                {detailQuery.data?.accountNumber}
              </p>
              <span className="rounded-full bg-[#edf1f7] px-[10px] py-[4px] text-[12px] font-medium text-[#6b7684]">
                {STATUS_LABEL[detailQuery.data?.status ?? ""] ?? detailQuery.data?.status}
              </span>
            </div>
            <p className="text-[28px] font-bold text-[#191f28]">
              {formatWon(balanceQuery.data?.balance)}
            </p>
            {isPositiveAmount(balanceQuery.data?.holdAmount) ? (
              <p className="text-[13px] text-[#8b95a1]">
                출금 가능 {formatWon(balanceQuery.data?.availableBalance)} (지급정지{" "}
                {formatWon(balanceQuery.data?.holdAmount)})
              </p>
            ) : null}
            <p className="text-[13px] text-[#8b95a1]">
              개설일 {formatDateTime(detailQuery.data?.openedAt)}
            </p>
            <Link
              href={`/accounts/${accountId}/transactions`}
              className="flex w-full items-center justify-center rounded-[10px] bg-[#edf1f7] py-[13px]"
            >
              <p className="text-[15px] font-semibold text-[#4262ff]">거래내역 조회</p>
            </Link>
          </div>
        )}
      </div>
    </MobileScreen>
  );
}
