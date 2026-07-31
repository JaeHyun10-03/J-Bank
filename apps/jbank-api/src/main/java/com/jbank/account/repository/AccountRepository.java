package com.jbank.account.repository;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

  Optional<Account> findByAccountNumber(String accountNumber);

  List<Account> findByCustomerId(Long customerId);

  Page<Account> findByCustomerId(Long customerId, Pageable pageable);

  Page<Account> findByCustomerIdAndStatus(Long customerId, AccountStatus status, Pageable pageable);
}
