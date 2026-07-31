package com.jbank.transfer.repository;

import com.jbank.transfer.domain.Transaction;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

  Page<Transaction> findByFromAccountIdOrToAccountId(
      Long fromAccountId, Long toAccountId, Pageable pageable);
}
