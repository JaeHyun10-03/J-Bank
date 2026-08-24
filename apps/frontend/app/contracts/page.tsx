"use client";

import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { formatWon, formatDateTime } from "@/lib/format";
import { useAuthStore } from "@/lib/auth-store";
import { MobileScreen } from "@/components/mobile-screen";
import { MobileNavBar } from "@/components/mobile-nav-bar";
import type { components } from "@/types/api";

type ApiResponsePageResponseContractSummaryResponse =
  components["schemas"]["ApiResponsePageResponseContractSummaryResponse"];

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "가입중",
  MATURED: "만기",
  TERMINATED: "해지",
};

export default function MyContractsPage() {
  const router = useRouter();
  const customerId = useAuthStore((state) => state.customerId);

  const contractsQuery = useQuery({
    queryKey: ["contracts", customerId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponsePageResponseContractSummaryResponse>(
        `/customers/${customerId}/contracts`,
        { params: { page: 0, size: 20 } },
      );
      return response.data.data?.content ?? [];
    },
    enabled: !!customerId,
  });

  const contracts = contractsQuery.data ?? [];

  return (
    <MobileScreen className="items-start">
      <MobileNavBar onBack={() => router.back()} />
      <div className="flex w-full flex-col px-[24px] pb-[16px] pt-[8px]">
        <p className="text-[24px] font-bold text-[#191f28]">내 가입상품</p>
      </div>

      {contracts.length === 0 ? (
        <div className="flex w-full flex-1 items-center justify-center px-[24px] py-[80px]">
          <p className="text-[14px] text-[#8b95a5]">가입한 상품이 없어요</p>
        </div>
      ) : (
        <div className="flex w-full flex-col px-[24px]">
          {contracts.map((contract, i) => (
            <div
              key={contract.contractNumber}
              className={i > 0 ? "flex w-full flex-col gap-[6px] border-t border-[#edf1f7] py-[16px]" : "flex w-full flex-col gap-[6px] py-[16px]"}
            >
              <div className="flex items-center justify-between">
                <p className="text-[16px] font-bold text-[#191f28]">{contract.productCode}</p>
                <p className="text-[12px] font-semibold text-[#8b95a5]">
                  {STATUS_LABEL[contract.status ?? ""] ?? contract.status}
                </p>
              </div>
              <p className="text-[13px] text-[#8b95a5]">계약번호 {contract.contractNumber}</p>
              <p className="text-[18px] font-bold text-[#4262ff]">
                {formatWon(Number(contract.subscriptionAmount ?? 0))}
              </p>
              <p className="text-[12px] text-[#b0b8c4]">
                가입일 {formatDateTime(contract.subscribedAt)} · 만기일{" "}
                {formatDateTime(contract.maturityAt)}
              </p>
            </div>
          ))}
        </div>
      )}
    </MobileScreen>
  );
}
