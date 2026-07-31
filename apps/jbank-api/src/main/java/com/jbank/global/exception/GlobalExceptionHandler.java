package com.jbank.global.exception;

import com.jbank.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    ErrorCode errorCode = ErrorCode.COMMON_001_VALIDATION_FAILED;
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse(errorCode.getMessage());
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(errorCode.name(), message));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
    ErrorCode errorCode = ErrorCode.COMMON_001_VALIDATION_FAILED;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(errorCode.name(), ex.getHeaderName() + " 헤더는 필수입니다"));
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex) {
    ErrorCode errorCode = ex.getErrorCode();
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(errorCode.name(), ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
    ErrorCode errorCode = ErrorCode.COMMON_006_INTERNAL_ERROR;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(errorCode.name(), errorCode.getMessage()));
  }
}
