package com.jbank.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  COMMON_001_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 필드 형식 또는 필수값 검증 실패"),
  COMMON_002_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 쿠키 누락 또는 만료"),
  COMMON_003_FORBIDDEN(HttpStatus.FORBIDDEN, "인증은 되었으나 해당 자원에 대한 권한 없음"),
  COMMON_004_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원이 존재하지 않음"),
  COMMON_005_CONFLICT(HttpStatus.CONFLICT, "상태 충돌(중복 요청, 동시성 충돌 등)"),
  COMMON_006_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류"),
  COMMON_007_CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "위조 방지 토큰 누락 또는 쿠키 값과 헤더 값 불일치"),

  ACC_001_DUPLICATE_RESIDENT_REG_NO(HttpStatus.CONFLICT, "이미 등록된 실명번호입니다"),
  ACC_003_EDD_EVIDENCE_INSUFFICIENT(
      HttpStatus.UNPROCESSABLE_ENTITY, "소명 자료가 불충분하여 위험도를 재평가할 수 없습니다"),
  ACC_004_CUSTOMER_NOT_HIGH_RISK(HttpStatus.CONFLICT, "고위험으로 분류되지 않은 고객입니다"),
  ACC_005_CDD_NOT_COMPLETED(HttpStatus.FORBIDDEN, "고객확인(CDD)이 완료되지 않았습니다"),
  ACC_006_CUSTOMER_STATUS_INVALID(HttpStatus.CONFLICT, "정지 또는 해지 상태의 고객입니다"),
  ACC_007_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 계좌 상태 전이입니다"),
  ACC_008_BALANCE_NOT_ZERO(HttpStatus.CONFLICT, "잔액이 0원이 아닌 계좌는 해지할 수 없습니다"),
  ACC_009_ACCOUNT_STATUS_INVALID(HttpStatus.CONFLICT, "정지 또는 해지된 계좌입니다"),
  ACC_010_HOLD_AMOUNT_REMAINS(HttpStatus.CONFLICT, "지급정지 금액이 남아있어 해지할 수 없습니다");

  private final HttpStatus httpStatus;
  private final String message;

  ErrorCode(HttpStatus httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getMessage() {
    return message;
  }
}
