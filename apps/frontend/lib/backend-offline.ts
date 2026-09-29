/**
 * 백엔드에 연결 자체가 안 된 오류인지. 운영 인스턴스가 꺼져 있으면 연결 단계에서 실패한다.
 * 연결된 뒤의 지연·오류(느린 이체 등)는 여기에 해당하지 않는다 — 요청이 서버에서 처리됐을 수
 * 있으므로 "서버 꺼짐"으로 바꾸면 사용자가 다시 제출해 중복이 생길 수 있다.
 */
const CONNECT_ERROR_CODES = new Set([
  "ECONNREFUSED",
  "ETIMEDOUT",
  "EHOSTUNREACH",
  "ENETUNREACH",
  "ENOTFOUND",
  "UND_ERR_CONNECT_TIMEOUT",
]);

export function isBackendConnectError(error: unknown): boolean {
  const cause = (error as { cause?: { code?: string } } | null)?.cause;
  return typeof cause?.code === "string" && CONNECT_ERROR_CODES.has(cause.code);
}
