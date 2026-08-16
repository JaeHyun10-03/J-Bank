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

/** "01012341234" -> "010-1234-1234". 입력 중에도 자릿수에 맞춰 그때그때 하이픈을 넣는다. */
export function formatPhoneNumber(digits: string): string {
  if (digits.length <= 3) return digits;
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7, 11)}`;
}

const MAX_AMOUNT_DIGITS = 12;

/** 금액 키패드 숫자 입력. "0"에서 시작해 자릿수를 이어붙이고, 12자리(1조 미만)를 넘으면 무시한다. */
export function appendAmountDigit(current: string, digit: string): string {
  if (current === "0") return digit === "00" ? "0" : digit;
  const next = current + digit;
  return next.length > MAX_AMOUNT_DIGITS ? current : next;
}

/** 금액 키패드 지우기. 마지막 자리를 지우고 비면 "0"으로 되돌린다. */
export function removeLastAmountDigit(current: string): string {
  const next = current.slice(0, -1);
  return next === "" ? "0" : next;
}
