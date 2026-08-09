const currencyFormatter = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 });
const dateTimeFormatter = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeStyle: "short",
});

export function formatWon(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) return "-";
  return `${currencyFormatter.format(amount)}원`;
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  return dateTimeFormatter.format(new Date(value));
}

/**
 * 백엔드가 BigDecimal 금액을 JSON 문자열로 내려보내(정밀도 보존 목적) "0.00" 같은 값이
 * JS에서 truthy가 된다. 0보다 큰지 판단할 때는 항상 이 함수를 거쳐야 한다.
 */
export function isPositiveAmount(value: number | string | null | undefined): boolean {
  return Number(value ?? 0) > 0;
}
