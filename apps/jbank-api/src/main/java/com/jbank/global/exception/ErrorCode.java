package com.jbank.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  COMMON_001_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 필드 형식 또는 필수값 검증 실패"),
  COMMON_002_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 쿠키 누락 또는 만료"),
  COMMON_003_FORBIDDEN(HttpStatus.FORBIDDEN, "인증은 되었으나 해당 자원에 대한 권한 없음"),
  COMMON_004_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원이 존재하지 않음"),
  COMMON_005_CONFLICT(HttpStatus.CONFLICT, "상태 충돌(중복 요청, 동시성 충돌 등)"),
  COMMON_006_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류"),
  COMMON_007_CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "위조 방지 토큰 누락 또는 쿠키 값과 헤더 값 불일치");

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
