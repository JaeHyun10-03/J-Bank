"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { formatWon, formatDateTime } from "@/lib/format";
import type { components } from "@/types/api";
import { AppHeader } from "@/components/app-header";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

type ApiResponsePageResponseTransactionSummaryResponse =
  components["schemas"]["ApiResponsePageResponseTransactionSummaryResponse"];

const TYPE_OPTIONS = [
  { value: "ALL", label: "전체" },
  { value: "DEPOSIT", label: "입금" },
  { value: "WITHDRAWAL", label: "출금" },
  { value: "TRANSFER_IN", label: "이체 입금" },
  { value: "TRANSFER_OUT", label: "이체 출금" },
] as const;

const TX_TYPE_LABEL: Record<string, string> = {
  DEPOSIT: "입금",
  WITHDRAWAL: "출금",
  TRANSFER: "이체",
};

const PAGE_SIZE = 20;

/** date input(yyyy-mm-dd)을 백엔드가 요구하는 ISO OffsetDateTime 문자열로 바꾼다. */
function toRangeBoundary(dateValue: string, endOfDay: boolean): string | undefined {
  if (!dateValue) return undefined;
  const date = new Date(dateValue);
  if (endOfDay) {
    date.setHours(23, 59, 59, 999);
  }
  return date.toISOString();
}

export default function TransactionsPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const [type, setType] = useState<string>("ALL");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["transactions", accountId, type, from, to, page],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponsePageResponseTransactionSummaryResponse>(
        `/accounts/${accountId}/transactions`,
        {
          params: {
            type: type === "ALL" ? undefined : type,
            from: toRangeBoundary(from, false),
            to: toRangeBoundary(to, true),
            page,
            size: PAGE_SIZE,
          },
        },
      );
      return response.data.data;
    },
    placeholderData: keepPreviousData,
  });

  function applyFilters() {
    setPage(0);
  }

  const transactions = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col gap-4 p-8">
      <AppHeader />
      <h1 className="text-xl font-semibold">거래내역 조회</h1>

      <div className="flex flex-wrap items-end gap-3">
        <Field className="w-40">
          <FieldLabel>거래유형</FieldLabel>
          <Select value={type} onValueChange={(value) => setType(value as string)}>
            <SelectTrigger>
              <SelectValue>
                {(value: string) =>
                  TYPE_OPTIONS.find((option) => option.value === value)?.label ?? value
                }
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              {TYPE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
        <Field className="w-40">
          <FieldLabel>시작일</FieldLabel>
          <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </Field>
        <Field className="w-40">
          <FieldLabel>종료일</FieldLabel>
          <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </Field>
        <Button onClick={applyFilters}>조회</Button>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">불러오는 중...</p>
      ) : isError ? (
        <p className="text-sm text-destructive">거래내역을 불러오지 못했습니다.</p>
      ) : transactions.length === 0 ? (
        <p className="text-sm text-muted-foreground">조회된 거래내역이 없습니다.</p>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>유형</TableHead>
                <TableHead>금액</TableHead>
                <TableHead>상태</TableHead>
                <TableHead>메모</TableHead>
                <TableHead>처리일시</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {transactions.map((tx) => (
                <TableRow key={tx.transactionId}>
                  <TableCell>{TX_TYPE_LABEL[tx.type ?? ""] ?? tx.type}</TableCell>
                  <TableCell>{formatWon(tx.amount)}</TableCell>
                  <TableCell>{tx.status}</TableCell>
                  <TableCell>{tx.memo ?? "-"}</TableCell>
                  <TableCell>{formatDateTime(tx.processedAt ?? tx.createdAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <div className="flex items-center justify-center gap-3">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              이전
            </Button>
            <span className="text-sm text-muted-foreground">
              {page + 1} / {Math.max(totalPages, 1)}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              다음
            </Button>
          </div>
        </>
      )}
    </main>
  );
}
