package com.jbank.support.ctr.repository;

import com.jbank.support.ctr.domain.CtrReportQueue;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CtrReportQueueRepository extends JpaRepository<CtrReportQueue, Long> {

  boolean existsByCustomerIdAndAccountIdAndTransactionDate(
      Long customerId, Long accountId, LocalDate transactionDate);
}
