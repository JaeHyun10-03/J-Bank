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
  @Timed(value = "db.lock.wait", extraTags = {"repository", "account", "method", "findByAccountNumberForUpdate"})
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.accountNumber = :accountNumber")
  Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

  // 입금·출금은 단일 계좌만 잠그면 되므로 accountId 기준 락 조회를 별도로 둔다(FR-TXN-002).
  @Timed(value = "db.lock.wait", extraTags = {"repository", "account", "method", "findByIdForUpdate"})
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.accountId = :accountId")
  Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);
}
