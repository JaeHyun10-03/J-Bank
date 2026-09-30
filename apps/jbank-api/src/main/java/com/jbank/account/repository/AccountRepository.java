package com.jbank.account.repository;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountStatus;
import io.micrometer.core.annotation.Timed;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

  Optional<Account> findByAccountNumber(String accountNumber);

  List<Account> findByCustomerId(Long customerId);

  Page<Account> findByCustomerId(Long customerId, Pageable pageable);

  Page<Account> findByCustomerIdAndStatus(Long customerId, AccountStatus status, Pageable pageable);

  // 이체 시 두 계좌번호를 오름차순 정렬한 뒤 이 메서드를 그 순서대로 호출해 교착상태를 막는다(FR-TXN-003).
  @Timed(
      value = "db.lock.wait",
      extraTags = {"repository", "account", "method", "findByAccountNumberForUpdate"})
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.accountNumber = :accountNumber")
  Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

  // 입금 반영 워커와 이체 송금 계좌가 잔액을 바꿀 때 쓴다. FOR NO KEY UPDATE는 이체가 수신 계좌에 거는
  // FOR KEY SHARE와 충돌하지 않아, 핫 계좌 반영 중에도 그 계좌로 가는 이체가 줄 서지 않는다(ADR 0012).
  @Timed(
      value = "db.lock.wait",
      extraTags = {"repository", "account", "method", "lockForBalanceUpdate"})
  @Query(
      value = "select * from accounts where account_id = :accountId for no key update",
      nativeQuery = true)
  Optional<Account> lockForBalanceUpdate(@Param("accountId") Long accountId);

  // 이체의 송금 계좌. 잔액을 바꾸므로 NO KEY UPDATE(워커 반영과 같은 모드).
  @Timed(
      value = "db.lock.wait",
      extraTags = {"repository", "account", "method", "lockSenderByAccountNumber"})
  @Query(
      value = "select * from accounts where account_number = :accountNumber for no key update",
      nativeQuery = true)
  Optional<Account> lockSenderByAccountNumber(@Param("accountNumber") String accountNumber);

  // 이체의 수신 계좌. 존재·상태만 확인하고 잔액은 바꾸지 않으므로 KEY SHARE로 읽는다 — 워커 반영·다른 이체의
  // NO KEY UPDATE와 충돌하지 않고 해지·상태 변경의 FOR UPDATE와만 충돌한다(ADR 0012).
  @Timed(
      value = "db.lock.wait",
      extraTags = {"repository", "account", "method", "lockReceiverByAccountNumber"})
  @Query(
      value = "select * from accounts where account_number = :accountNumber for key share",
      nativeQuery = true)
  Optional<Account> lockReceiverByAccountNumber(@Param("accountNumber") String accountNumber);

  @Query(
      value = "select * from accounts where account_id = :accountId for key share",
      nativeQuery = true)
  Optional<Account> lockReceiverById(@Param("accountId") Long accountId);

  // 해지·상태 변경용. FOR UPDATE는 잔액 갱신(NO KEY UPDATE)과 이체의 수신 계좌 읽기(KEY SHARE) 모두와
  // 충돌해, 검사하는 동안 잔액이 바뀌거나 새 입금이 들어오지 못하게 한다. 이 트랜잭션에서 계좌를 처음 읽는
  // 쿼리여야 한다 — 먼저 findById로 읽어 두면 영속성 컨텍스트가 옛 값을 돌려줘 전체 컬럼 UPDATE가 동시
  // 잔액 갱신을 덮어쓴다(ADR 0012).
  @Query(
      value = "select * from accounts where account_id = :accountId for update",
      nativeQuery = true)
  Optional<Account> lockForStatusChange(@Param("accountId") Long accountId);

  // 해지 전 확인: 아직 반영되지 않은 입금 대기나, 이 계좌로 들어올 OTP 인증 대기 이체가 있는가(ADR 0012).
  // 이체 도메인을 참조하지 않으려고 테이블을 직접 본다.
  @Query(
      value =
          "select exists(select 1 from pending_credits where account_id = :accountId and applied_at is null) "
              + "or exists(select 1 from transactions where to_account_id = :accountId and status = 'PENDING_OTP')",
      nativeQuery = true)
  boolean hasIncomingInProgress(@Param("accountId") Long accountId);

  // 입금·출금은 단일 계좌만 잠그면 되므로 accountId 기준 락 조회를 별도로 둔다(FR-TXN-002).
  @Timed(
      value = "db.lock.wait",
      extraTags = {"repository", "account", "method", "findByIdForUpdate"})
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.accountId = :accountId")
  Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);
}
