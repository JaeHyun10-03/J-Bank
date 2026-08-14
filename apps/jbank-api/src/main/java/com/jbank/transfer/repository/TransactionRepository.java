package com.jbank.transfer.repository;

import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// API-010 조회는 필터 조합이 많아 TransactionHistoryService가 Specification으로 동적으로 짠다.
public interface TransactionRepository
    extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

  Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

  // 만료 대기 거래 정리 스케줄러가 쓰는 조회다. ERD 3절 부분 인덱스(status, created_at)와
  // 조건이 일치한다.
  List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, OffsetDateTime cutoff);

  // OTP 검증·만료 대기 거래 정리가 같은 거래를 동시에 취소하지 않도록 잠근다(FR-AUTH-003).
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Transaction t where t.transactionId = :transactionId")
  Optional<Transaction> findByIdForUpdate(@Param("transactionId") Long transactionId);

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
