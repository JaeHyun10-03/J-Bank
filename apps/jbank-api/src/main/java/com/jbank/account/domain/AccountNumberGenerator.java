package com.jbank.account.domain;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * 계좌번호 = 지점코드(3) + 시퀀스(6) + 체크디지트(1). 시퀀스는 DB 시퀀스(account_number_seq)로 채번해 동시 요청이 몰려도 같은 번호가 중복
 * 발급되지 않게 한다.
 */
@Component
public class AccountNumberGenerator {

  // ponytail: 지점을 하나만 다루는 범위라 상수로 고정. 다지점 지원 시 지점코드 입력을 받게 확장한다.
  private static final String BRANCH_CODE = "110";

  private final EntityManager entityManager;

  public AccountNumberGenerator(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public String generate() {
    long sequenceValue =
        ((Number)
                entityManager
                    .createNativeQuery("SELECT nextval('account_number_seq')")
                    .getSingleResult())
            .longValue();
    String sequencePart = String.format("%06d", sequenceValue);
    String body = BRANCH_CODE + sequencePart;
    int checkDigit = AccountNumberChecker.calculateCheckDigit(body);
    return BRANCH_CODE + "-" + sequencePart + "-" + checkDigit;
  }
}
