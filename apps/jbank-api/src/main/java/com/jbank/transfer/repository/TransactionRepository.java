package com.jbank.transfer.repository;

import com.jbank.transfer.domain.Transaction;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// API-010 조회는 필터 조합이 많아 TransactionHistoryService가 Specification으로 동적으로 짠다.
public interface TransactionRepository
    extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

  Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

  // 고액현금거래 판별 배치(구현계획 W5)가 하루치 입금·출금(현금성 거래) 합계를 계좌별로 집계할 때 쓴다.
  // DEPOSIT은 toAccountId만, WITHDRAWAL은 fromAccountId만 채워지므로 coalesce로 묶는다.
  @Query(
      "select new com.jbank.transfer.repository.AccountCashTotal("
          + "coalesce(t.fromAccountId, t.toAccountId), sum(t.amount)) "
          + "from Transaction t "
          + "where t.transactionType in (com.jbank.transfer.domain.TransactionType.DEPOSIT, "
          + "com.jbank.transfer.domain.TransactionType.WITHDRAWAL) "
          + "and t.status = com.jbank.transfer.domain.TransactionStatus.COMPLETED "
          + "and t.processedAt >= :startOfDay and t.processedAt < :endOfDay "
          + "group by coalesce(t.fromAccountId, t.toAccountId)")
  List<AccountCashTotal> sumCashByAccount(
      @Param("startOfDay") OffsetDateTime startOfDay, @Param("endOfDay") OffsetDateTime endOfDay);
}
