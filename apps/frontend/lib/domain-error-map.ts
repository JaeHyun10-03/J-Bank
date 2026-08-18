import type { components } from "@/types/api";

type ErrorDetail = components["schemas"]["ErrorDetail"];

type DomainErrorEntry = {
  /** 값이 있으면 해당 RHF 필드에 붙여야 하는 에러, 없으면 화면 전체에 뜨는 알림. */
  field?: string;
  message: string;
};

/**
 * API설계 2.7절 공통 에러코드 중 입출금·이체·OTP 화면(API-006/007/008/016)에서
 * 실제로 돌아오는 코드만 다룬다. 새 화면에서 새 코드가 필요해지면 여기에 추가한다.
 */
const DOMAIN_ERROR_MAP: Record<string, DomainErrorEntry> = {
  COMMON_001_VALIDATION_FAILED: { message: "입력값을 다시 확인해주세요." },
  ACC_009_ACCOUNT_STATUS_INVALID: { message: "정지 또는 해지된 계좌는 거래할 수 없습니다." },
  TXN_001_INSUFFICIENT_BALANCE: { field: "amount", message: "출금 가능 금액을 초과했습니다." },
  TXN_002_SAME_ACCOUNT_TRANSFER: {
    field: "toAccountNumber",
    message: "출금 계좌와 동일한 계좌로는 이체할 수 없습니다.",
  },
  TXN_003_COUNTERPARTY_ACCOUNT_NOT_FOUND: {
    field: "toAccountNumber",
    message: "계좌번호를 다시 확인해주세요.",
  },
  TXN_004_COUNTERPARTY_ACCOUNT_INVALID: {
    field: "toAccountNumber",
    message: "상대 계좌를 이용할 수 없습니다.",
  },
  TXN_005_TRANSACTION_NOT_PENDING: { message: "이미 완료되었거나 취소된 거래입니다." },
  AUTH_004_OTP_MISMATCH: { field: "otpCode", message: "인증번호가 일치하지 않습니다." },
  AUTH_005_OTP_EXPIRED: {
    message: "인증 시간이 만료되어 지급정지가 해제됐습니다. 이체를 다시 시도해주세요.",
  },
  ACC_005_CDD_NOT_COMPLETED: { message: "고객확인 절차가 끝나지 않아 계좌를 개설할 수 없습니다." },
  ACC_006_CUSTOMER_STATUS_INVALID: { message: "정지 또는 해지된 고객은 계좌를 개설할 수 없습니다." },
};

export function resolveDomainError(error: ErrorDetail | undefined): DomainErrorEntry {
  if (!error?.code) {
    return { message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요." };
  }
  return DOMAIN_ERROR_MAP[error.code] ?? { message: error.message ?? "요청을 처리하지 못했습니다." };
}
