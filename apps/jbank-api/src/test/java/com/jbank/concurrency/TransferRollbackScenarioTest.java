package com.jbank.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.account.domain.Account;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시나리오⑤. 이체 처리 도중(두 번째 원장 엔트리를 남기기 전) 강제로 예외를 발생시켜, 락 획득부터 거래 이력 저장까지가 단일 트랜잭션이라는 FR-TXN-003 요구사항대로
 * 첫 번째 원장 엔트리와 거래 이력까지 함께 롤백되고 한쪽만 남는 상태가 생기지 않는지 확인한다. TransferService 내부에 장애를 실제로 주입할 훅이 없어(그런 훅을
 * 프로덕션 코드에 두는 것 자체가 부적절하다), TransferService가 따르는 것과 같은 락→기록 순서를 트랜잭션 템플릿으로 직접 재현하고 대변 엔트리 기록 직전에
 * 예외를 던져 롤백 여부를 검증한다.
 */
class TransferRollbackScenarioTest extends AbstractConcurrencyTest {

  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void 이체_도중_예외_발생하면_원장_두_건_모두_롤백된다() {
    Account from = saveAccount(new BigDecimal("100000.00"));
    Account to = saveAccount(new BigDecimal("0.00"));
    String idempotencyKey = UUID.randomUUID().toString();
    BigDecimal amount = new BigDecimal("10000.00");

    TransactionTemplate template = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                template.executeWithoutResult(
                    status -> {
                      Account lockedFrom =
                          accountRepository
                              .findByAccountNumberForUpdate(from.getAccountNumber())
                              .orElseThrow();
                      Account lockedTo =
                          accountRepository
                              .findByAccountNumberForUpdate(to.getAccountNumber())
                              .orElseThrow();

                      Transaction transaction =
                          transactionRepository.save(
                              new Transaction(
                                  TransactionType.TRANSFER,
                                  lockedFrom.getAccountId(),
                                  lockedTo.getAccountId(),
                                  amount,
                                  idempotencyKey,
                                  null));

                      OffsetDateTime occurredAt = OffsetDateTime.now();
                      lockedFrom.debit(amount);
                      ledgerEntryRepository.save(
                          new LedgerEntry(
                              lockedFrom.getAccountId(),
                              transaction.getTransactionId(),
                              EntryType.DEBIT,
                              amount,
                              lockedFrom.getCurrentBalanceCache(),
                              occurredAt));

                      // 대변 엔트리를 기록하기 전에 강제로 장애를 낸다.
                      throw new RuntimeException("강제 장애");
                    }))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("강제 장애");

    assertThat(transactionRepository.findByIdempotencyKey(idempotencyKey)).isEmpty();
    Account updatedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    Account updatedTo = accountRepository.findById(to.getAccountId()).orElseThrow();
    assertThat(updatedFrom.getCurrentBalanceCache()).isEqualByComparingTo("100000.00");
    assertThat(updatedTo.getCurrentBalanceCache()).isEqualByComparingTo("0.00");
    assertThat(ledgerEntryRepository.findByAccountId(from.getAccountId())).isEmpty();
    assertThat(ledgerEntryRepository.findByAccountId(to.getAccountId())).isEmpty();
  }
}
