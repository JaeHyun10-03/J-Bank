package com.jbank.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void DomainException은_보유한_ErrorCode의_HTTP상태와_코드로_변환된다() {
    // given
    DomainException exception = new DomainException(ErrorCode.ACC_001_DUPLICATE_RESIDENT_REG_NO);

    // when
    ResponseEntity<ApiResponse<Void>> response = handler.handleDomainException(exception);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().success()).isFalse();
    assertThat(response.getBody().error().code()).isEqualTo("ACC_001_DUPLICATE_RESIDENT_REG_NO");
  }

  @Test
  void 필수_헤더가_없으면_400으로_변환된다() throws NoSuchMethodException {
    // given
    MethodParameter parameter =
        new MethodParameter(
            GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);
    MissingRequestHeaderException exception =
        new MissingRequestHeaderException("Idempotency-Key", parameter);

    // when
    ResponseEntity<ApiResponse<Void>> response = handler.handleMissingHeader(exception);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("COMMON_001_VALIDATION_FAILED");
  }

  @Test
  void 매핑_없는_경로는_404_COMMON_004로_변환된다() {
    // given
    NoResourceFoundException noResource =
        new NoResourceFoundException(HttpMethod.GET, "actuator/prometheus");
    NoHandlerFoundException noHandler =
        new NoHandlerFoundException("GET", "/actuator/unknown", new HttpHeaders());

    // when
    ResponseEntity<ApiResponse<Void>> resourceResponse = handler.handleNotFound(noResource);
    ResponseEntity<ApiResponse<Void>> handlerResponse = handler.handleNotFound(noHandler);

    // then
    assertThat(resourceResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resourceResponse.getBody().error().code()).isEqualTo("COMMON_004_NOT_FOUND");
    assertThat(handlerResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(handlerResponse.getBody().error().code()).isEqualTo("COMMON_004_NOT_FOUND");
  }

  private void sample(@RequestHeader("Idempotency-Key") String key) {}
}
