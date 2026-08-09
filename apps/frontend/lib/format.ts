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
