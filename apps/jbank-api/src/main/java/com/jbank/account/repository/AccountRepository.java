package com.jbank.account.repository;

import com.jbank.account.domain.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

  Optional<Account> findByAccountNumber(String accountNumber);

  List<Account> findByCustomerId(Long customerId);
}
