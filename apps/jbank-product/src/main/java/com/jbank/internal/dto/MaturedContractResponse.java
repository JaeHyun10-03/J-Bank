package com.jbank.internal.dto;

import java.math.BigDecimal;

/** jbank-api의 이자 지급 배치가 소비하는 만기 계약 정보. 이자 금액은 이미 계산돼 있다. */
public record MaturedContractResponse(Long contractId, Long accountId, BigDecimal interestAmount) {}
