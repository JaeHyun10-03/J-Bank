package com.jbank.global.exception;

import org.springframework.http.HttpStatus;

/**
 * jbank-api의 ErrorCode와 코드 이름·의미를 맞춘 부분집합이다 — 프론트엔드
 * domain-error-map.ts가 서비스에 상관없이 같은 코드를 보고 같은 메시지를
 * 보여줘야 하므로, 겹치는 COMMON_*은 값까지 동일하게 유지한다. 이 서비스가
 * 실제로 쓰는 코드만 들고 있고, 계좌·거래·인증 도메인 코드는 없다.
 */
public enum ErrorCode {
  COMMON_001_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 필드 형식 또는 필수값 검증 실패"),
  COMMON_002_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 쿠키 누락 또는 만료"),
  COMMON_003_FORBIDDEN(HttpStatus.FORBIDDEN, "인증은 되었으나 해당 자원에 대한 권한 없음"),
  COMMON_004_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원이 존재하지 않음"),
  COMMON_005_CONFLICT(HttpStatus.CONFLICT, "상태 충돌(중복 요청, 동시성 충돌 등)"),
  COMMON_006_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류"),
  COMMON_007_CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "위조 방지 토큰 누락 또는 쿠키 값과 헤더 값 불일치"),

  PRD_001_MIN_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "최소 가입금액에 미달합니다"),
  PRD_002_PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "판매 중지된 상품입니다"),
  PRD_003_SUBSCRIPTION_FAILED(HttpStatus.CONFLICT, "가입 처리 중 출금에 실패해 가입이 취소되었습니다");

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
