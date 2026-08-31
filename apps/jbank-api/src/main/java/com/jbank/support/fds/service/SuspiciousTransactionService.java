package com.jbank.support.fds.service;

import com.jbank.global.response.PageResponse;
import com.jbank.support.fds.domain.FdsRuleType;
import com.jbank.support.fds.domain.SuspiciousTransaction;
import com.jbank.support.fds.dto.SuspiciousTransactionResponse;
import com.jbank.support.fds.repository.SuspiciousTransactionRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuspiciousTransactionService {

  private final SuspiciousTransactionRepository suspiciousTransactionRepository;

  public SuspiciousTransactionService(
      SuspiciousTransactionRepository suspiciousTransactionRepository) {
    this.suspiciousTransactionRepository = suspiciousTransactionRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<SuspiciousTransactionResponse> getSuspiciousTransactions(
      FdsRuleType ruleType, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
    Page<SuspiciousTransaction> page =
        suspiciousTransactionRepository.findAll(buildSpecification(ruleType, from, to), pageable);
    return PageResponse.from(page.map(SuspiciousTransactionService::toResponse));
  }

  private Specification<SuspiciousTransaction> buildSpecification(
      FdsRuleType ruleType, OffsetDateTime from, OffsetDateTime to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (ruleType != null) {
        predicates.add(cb.equal(root.get("ruleType"), ruleType));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("detectedAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("detectedAt"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static SuspiciousTransactionResponse toResponse(SuspiciousTransaction entity) {
    return new SuspiciousTransactionResponse(
        String.valueOf(entity.getSuspiciousTransactionId()),
        String.valueOf(entity.getTransactionId()),
        String.valueOf(entity.getAccountId()),
        entity.getRuleType(),
        entity.getAmount(),
        entity.getDetail(),
        entity.getDetectedAt());
  }
}
