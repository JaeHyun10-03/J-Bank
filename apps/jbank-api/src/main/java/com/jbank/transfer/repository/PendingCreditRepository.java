package com.jbank.transfer.repository;

import com.jbank.transfer.domain.PendingCredit;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingCreditRepository extends JpaRepository<PendingCredit, Long> {

  @Query(
      value =
          "select distinct account_id from pending_credits where applied_at is null limit :limit",
      nativeQuery = true)
  List<Long> findAccountIdsWithUnapplied(@Param("limit") int limit);

  // 여러 반영기가 같은 행을 두 번 가져가지 않게 잠긴 행은 건너뛴다(계좌 행 락과 함께 중복 반영 방지).
  @Query(
      value =
          "select * from pending_credits where account_id = :accountId and applied_at is null "
              + "order by pending_credit_id limit :limit for update skip locked",
      nativeQuery = true)
  List<PendingCredit> lockUnappliedForAccount(
      @Param("accountId") Long accountId, @Param("limit") int limit);

  // 트랜잭션 시작 시각(now())이 아니라 실제 반영 시각을 남긴다 — 반영 지연 측정 기준.
  @Modifying
  @Query(
      value =
          "update pending_credits set applied_at = clock_timestamp() where pending_credit_id in (:ids)",
      nativeQuery = true)
  int markApplied(@Param("ids") List<Long> ids);

  @Query(
      value = "select coalesce(sum(amount), 0) from pending_credits where applied_at is null",
      nativeQuery = true)
  BigDecimal sumUnapplied();

  @Query(
      value =
          "select count(*) > 0 from pending_credits where account_id = :accountId and applied_at is null",
      nativeQuery = true)
  boolean existsUnappliedForAccount(@Param("accountId") Long accountId);

  /** [미반영 수, 가장 오래된 미반영의 나이(초)] — 지표 캐시용. */
  @Query(
      value =
          "select count(*), coalesce(extract(epoch from clock_timestamp() - min(created_at)), 0) "
              + "from pending_credits where applied_at is null",
      nativeQuery = true)
  List<Object[]> unappliedStats();
}
