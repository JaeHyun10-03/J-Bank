package com.jbank.support.audit.service;

import com.jbank.global.response.PageResponse;
import com.jbank.support.audit.domain.AuditLog;
import com.jbank.support.audit.dto.AuditLogResponse;
import com.jbank.support.audit.repository.AuditLogRepository;
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
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;

  public AuditLogService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditLogResponse> getLogs(
      String eventType, String actorId, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
    Page<AuditLog> page =
        auditLogRepository.findAll(buildSpecification(eventType, actorId, from, to), pageable);
    return PageResponse.from(page.map(AuditLogResponse::from));
  }

  private Specification<AuditLog> buildSpecification(
      String eventType, String actorId, OffsetDateTime from, OffsetDateTime to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (eventType != null) {
        predicates.add(cb.equal(root.get("eventType"), eventType));
      }
      if (actorId != null) {
        predicates.add(cb.equal(root.get("actorId"), actorId));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
