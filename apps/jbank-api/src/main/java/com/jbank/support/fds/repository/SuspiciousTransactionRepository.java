package com.jbank.support.fds.repository;

import com.jbank.support.fds.domain.FdsRuleType;
import com.jbank.support.fds.domain.SuspiciousTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// API-022 조회가 ruleType·기간 필터를 선택적으로 조합해야 해서 AuditLogRepository와
// 같은 방식(JpaSpecificationExecutor)을 쓴다.
public interface SuspiciousTransactionRepository
    extends JpaRepository<SuspiciousTransaction, Long>, JpaSpecificationExecutor<SuspiciousTransaction> {

  boolean existsByTransactionIdAndRuleType(Long transactionId, FdsRuleType ruleType);
}
