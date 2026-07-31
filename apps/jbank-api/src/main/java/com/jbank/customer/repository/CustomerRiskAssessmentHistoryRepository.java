package com.jbank.customer.repository;

import com.jbank.customer.domain.CustomerRiskAssessmentHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRiskAssessmentHistoryRepository
    extends JpaRepository<CustomerRiskAssessmentHistory, Long> {

  List<CustomerRiskAssessmentHistory> findByCustomerId(Long customerId);
}
