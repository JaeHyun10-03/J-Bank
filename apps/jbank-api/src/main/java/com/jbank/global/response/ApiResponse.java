package com.jbank.global.response;

public record ApiResponse<T>(boolean success, T data, ErrorDetail error) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static <T> ApiResponse<T> error(String code, String message) {
    return new ApiResponse<>(false, null, new ErrorDetail(code, message));
  }

  public record ErrorDetail(String code, String message) {}
}
