package com.jbank.batch.interest;

import com.jbank.product.domain.ProductContract;
import java.math.BigDecimal;

/** 만기 도래 계약과 그 계약에 지급할 이자 금액. */
public record MaturedContractInterest(ProductContract contract, BigDecimal interestAmount) {}
