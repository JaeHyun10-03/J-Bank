package com.jbank.transfer.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 멱등성 키 유니크 제약 위반 이후 원래 결과를 조회하는 복구 경로 전용. PostgreSQL은 제약 위반 이후 같은 DB 트랜잭션을 완전히 포기 상태(aborted)로 만들어
 * 추가 쿼리를 전혀 받지 않으므로, 현재 트랜잭션은 롤백 대상으로 표시하고 조회는 새 물리 트랜잭션에서 한다. Deposit·Withdrawal·Transfer 세 서비스가
 * 동일하게 필요로 하는 경로라 공용으로 뺀다.
 */
@Component
public class IdempotencyRecovery {

  private final TransactionTemplate requiresNewTemplate;

  public IdempotencyRecovery(PlatformTransactionManager transactionManager) {
    this.requiresNewTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public <T> T recoverInNewTransaction(Supplier<T> action) {
    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    return requiresNewTemplate.execute(status -> action.get());
  }
}
