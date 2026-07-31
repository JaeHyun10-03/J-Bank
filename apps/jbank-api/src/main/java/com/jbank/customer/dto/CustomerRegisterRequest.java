package com.jbank.customer.dto;

import com.jbank.customer.domain.IdentityVerificationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CustomerRegisterRequest(
    @NotBlank String name,
    @NotBlank String residentRegNo,
    @NotNull LocalDate birthDate,
    @NotBlank String phone,
    String address,
    String occupation,
    @NotNull IdentityVerificationMethod identityVerificationMethod,
    String transactionPurpose,
    String fundSource) {}
