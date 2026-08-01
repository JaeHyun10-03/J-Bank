package com.jbank.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 인증은 됐지만 권한이 없을 때(@PreAuthorize 등) 던지는 403을 ApiResponse 포맷으로 맞춘다. */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public RestAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    ErrorCode errorCode = ErrorCode.COMMON_003_FORBIDDEN;
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write(
            objectMapper.writeValueAsString(
                ApiResponse.error(errorCode.name(), errorCode.getMessage())));
  }
}
