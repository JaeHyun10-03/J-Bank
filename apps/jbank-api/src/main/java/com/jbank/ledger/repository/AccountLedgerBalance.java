package com.jbank.ledger.repository;

import java.math.BigDecimal;

/** 계좌 하나의 원장 CREDIT-DEBIT 합산 결과(원장 정합성 대사 배치용). */
public record AccountLedgerBalance(Long accountId, BigDecimal ledgerBalance) {}
