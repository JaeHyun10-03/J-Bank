package com.jbank.product.dto;

import java.time.OffsetDateTime;

public record ProductSubscribeResponse(
    String contractNumber,
    String productCode,
    OffsetDateTime subscribedAt,
    OffsetDateTime maturityAt) {}
