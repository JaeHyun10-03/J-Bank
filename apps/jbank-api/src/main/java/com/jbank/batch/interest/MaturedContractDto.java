package com.jbank.batch.interest;

import java.math.BigDecimal;

/**
 * jbank-product의 {@code GET /internal/v1/contracts/matured}가 돌려주는 만기 계약 정보.
 * 이자 계산(요율 × 가입기간)은 요율 데이터를 가진 jbank-product 쪽에서 이미 끝낸 값을
 * 받는다 — jbank-api는 이 금액을 계좌에 입금하는 역할만 한다.
 */
public record MaturedContractDto(Long contractId, Long accountId, BigDecimal interestAmount) {}
