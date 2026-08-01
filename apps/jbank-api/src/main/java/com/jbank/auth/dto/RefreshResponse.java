package com.jbank.auth.dto;

import java.time.OffsetDateTime;

public record RefreshResponse(OffsetDateTime accessTokenExpiresAt, String csrfToken) {}
