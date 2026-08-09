"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/lib/auth-store";
import { formatWon } from "@/lib/format";
import type { components } from "@/types/api";
import { AppHeader } from "@/components/app-header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

type ApiResponsePage =
  components["schemas"]["ApiResponsePageResponseCustomerAccountSummaryResponse"];

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "정상",
  SUSPENDED: "정지",
  DORMANT: "휴면",
  CLOSED: "해지",
};

export default function HomePage() {
  const customerId = useAuthStore((state) => state.customerId);

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

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-4 p-8">
      <AppHeader />
      <h1 className="text-xl font-semibold">내 계좌</h1>
      {!customerId ? (
        <p className="text-sm text-muted-foreground">로그인이 필요합니다.</p>
      ) : isLoading ? (
        <p className="text-sm text-muted-foreground">불러오는 중...</p>
      ) : isError ? (
        <p className="text-sm text-destructive">계좌 목록을 불러오지 못했습니다.</p>
      ) : data && data.length > 0 ? (
        <div className="flex flex-col gap-3">
          {data.map((account) => (
            <Link key={account.accountId} href={`/accounts/${account.accountId}`}>
              <Card className="transition-colors hover:bg-muted/50">
                <CardHeader>
                  <CardTitle className="flex items-center justify-between">
                    <span>{account.accountNumber}</span>
                    <Badge variant={account.status === "ACTIVE" ? "default" : "secondary"}>
                      {STATUS_LABEL[account.status ?? ""] ?? account.status}
                    </Badge>
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-lg font-semibold">{formatWon(account.balance)}</p>
                  {account.holdAmount ? (
                    <p className="text-xs text-muted-foreground">
                      출금 가능 {formatWon(account.availableBalance)} (지급정지{" "}
                      {formatWon(account.holdAmount)})
                    </p>
                  ) : null}
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">보유한 계좌가 없습니다.</p>
      )}
    </main>
  );
}
