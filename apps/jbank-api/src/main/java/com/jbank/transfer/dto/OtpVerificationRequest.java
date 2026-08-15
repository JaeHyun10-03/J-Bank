package com.jbank.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerificationRequest(
    @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP는 6자리 숫자여야 합니다") String otpCode) {}
