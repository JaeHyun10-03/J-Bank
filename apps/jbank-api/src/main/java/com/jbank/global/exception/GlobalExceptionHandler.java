package com.jbank.global.exception;

import com.jbank.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
    ErrorCode errorCode = ErrorCode.COMMON_006_INTERNAL_ERROR;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(errorCode.name(), errorCode.getMessage()));
  }
}
