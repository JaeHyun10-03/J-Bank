"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { formatWon, formatDateTime } from "@/lib/format";
import type { components } from "@/types/api";
import { AppHeader } from "@/components/app-header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";

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
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-4 p-8">
      <AppHeader />
      {forbidden ? (
        <Alert variant="destructive">
          <AlertDescription>본인 소유의 계좌만 조회할 수 있습니다.</AlertDescription>
        </Alert>
      ) : detailQuery.isLoading || balanceQuery.isLoading ? (
        <p className="text-sm text-muted-foreground">불러오는 중...</p>
      ) : detailQuery.isError || balanceQuery.isError ? (
        <p className="text-sm text-destructive">계좌 정보를 불러오지 못했습니다.</p>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <span>{detailQuery.data?.accountNumber}</span>
              <Badge variant={detailQuery.data?.status === "ACTIVE" ? "default" : "secondary"}>
                {STATUS_LABEL[detailQuery.data?.status ?? ""] ?? detailQuery.data?.status}
              </Badge>
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div>
              <p className="text-2xl font-semibold">{formatWon(balanceQuery.data?.balance)}</p>
              {balanceQuery.data?.holdAmount ? (
                <p className="text-sm text-muted-foreground">
                  출금 가능 {formatWon(balanceQuery.data?.availableBalance)} (지급정지{" "}
                  {formatWon(balanceQuery.data?.holdAmount)})
                </p>
              ) : null}
            </div>
            <p className="text-xs text-muted-foreground">
              개설일 {formatDateTime(detailQuery.data?.openedAt)}
            </p>
            <Button render={<Link href={`/accounts/${accountId}/transactions`} />}>
              거래내역 조회
            </Button>
          </CardContent>
        </Card>
      )}
    </main>
  );
}
