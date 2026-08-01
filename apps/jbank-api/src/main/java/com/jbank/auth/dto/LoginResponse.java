package com.jbank.auth.dto;

import java.time.OffsetDateTime;

public record LoginResponse(
    String customerId, String name, OffsetDateTime accessTokenExpiresAt, String csrfToken) {}
