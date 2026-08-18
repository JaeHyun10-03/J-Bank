"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { ChevronLeft } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { formatWon, formatDateTime } from "@/lib/format";
import type { components } from "@/types/api";
import { MobileScreen } from "@/components/mobile-screen";

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
  INTEREST: "이자",
};

const TX_STATUS_LABEL: Record<string, string> = {
  PENDING: "처리중",
  PENDING_OTP: "인증대기",
  FAILED: "실패",
  CANCELLED: "취소됨",
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

const inputClass =
  "rounded-[10px] border border-[#e0e6f1] px-[12px] py-[9px] text-[13px] text-[#191f28] focus:outline-none";

export default function TransactionsPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const router = useRouter();
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

  const transactions = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

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
        <p className="text-[20px] font-bold text-[#191f28]">거래내역 조회</p>
      </div>

      <div className="flex w-full flex-1 flex-col gap-[12px] px-[16px] pb-[24px]">
        <div className="flex flex-wrap items-center gap-[8px] rounded-[16px] bg-white p-[12px]">
          <select
            value={type}
            onChange={(e) => {
              setType(e.target.value);
              setPage(0);
            }}
            className={inputClass}
          >
            {TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <input
            type="date"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              setPage(0);
            }}
            className={inputClass}
          />
          <span className="text-[13px] text-[#8b95a1]">~</span>
          <input
            type="date"
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              setPage(0);
            }}
            className={inputClass}
          />
        </div>

        <div className="flex w-full flex-col rounded-[20px] bg-white px-[20px]">
          {isLoading ? (
            <p className="py-[24px] text-center text-[14px] text-[#6b7684]">불러오는 중...</p>
          ) : isError ? (
            <p className="py-[24px] text-center text-[14px] text-[#f04452]">
              거래내역을 불러오지 못했습니다.
            </p>
          ) : transactions.length === 0 ? (
            <p className="py-[24px] text-center text-[14px] text-[#6b7684]">
              조회된 거래내역이 없습니다.
            </p>
          ) : (
            transactions.map((tx) => (
              <div
                key={tx.transactionId}
                className="flex flex-col gap-[6px] border-b border-[#f2f4f6] py-[16px] last:border-none"
              >
                <div className="flex items-center justify-between">
                  <p className="text-[15px] font-medium text-[#191f28]">
                    {TX_TYPE_LABEL[tx.type ?? ""] ?? tx.type}
                    {tx.status && tx.status !== "COMPLETED" ? (
                      <span className="ml-[6px] text-[12px] font-medium text-[#f04452]">
                        {TX_STATUS_LABEL[tx.status] ?? tx.status}
                      </span>
                    ) : null}
                  </p>
                  <p className="text-[15px] font-semibold text-[#191f28]">{formatWon(tx.amount)}</p>
                </div>
                <div className="flex items-center justify-between">
                  <p className="text-[13px] text-[#8b95a1]">{tx.memo ?? "-"}</p>
                  <p className="text-[13px] text-[#8b95a1]">
                    {formatDateTime(tx.processedAt ?? tx.createdAt)}
                  </p>
                </div>
              </div>
            ))
          )}
        </div>

        {transactions.length > 0 ? (
          <div className="flex items-center justify-center gap-[16px] py-[8px]">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
              className="text-[13px] font-medium text-[#4262ff] disabled:text-[#b0b8c1]"
            >
              이전
            </button>
            <span className="text-[13px] text-[#8b95a1]">
              {page + 1} / {Math.max(totalPages, 1)}
            </span>
            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
              className="text-[13px] font-medium text-[#4262ff] disabled:text-[#b0b8c1]"
            >
              다음
            </button>
          </div>
        ) : null}
      </div>
    </MobileScreen>
  );
}
