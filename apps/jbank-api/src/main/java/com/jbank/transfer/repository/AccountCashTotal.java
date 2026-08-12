package com.jbank.transfer.repository;

import java.math.BigDecimal;

/** 계좌 하나의 하루 현금성 거래(입금·출금) 합계(고액현금거래 판별 배치용). */
public record AccountCashTotal(Long accountId, BigDecimal totalAmount) {}
