package com.jbank.transfer.repository;

import com.jbank.transfer.domain.Transaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// API-010 조회는 필터 조합이 많아 TransactionHistoryService가 Specification으로 동적으로 짠다.
public interface TransactionRepository
    extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

  Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
