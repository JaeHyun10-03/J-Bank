package com.jbank.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record EddRegisterRequest(
    @NotBlank String transactionPurpose,
    @NotBlank String fundSource,
    @NotBlank String supportingDocumentRef) {}
