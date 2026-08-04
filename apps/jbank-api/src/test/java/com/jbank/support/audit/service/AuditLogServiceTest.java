package com.jbank.support.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.jbank.support.audit.domain.ActorType;
import com.jbank.support.audit.domain.AuditLog;
import com.jbank.support.audit.repository.AuditLogRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

  @Mock private AuditLogRepository auditLogRepository;

  private AuditLogService auditLogService;

  @Test
  void 조회결과를_응답DTO로_변환해서_반환한다() {
    auditLogService = new AuditLogService(auditLogRepository);
    AuditLog auditLog =
        new AuditLog(
            "ACCOUNT_STATUS_CHANGED",
            ActorType.SYSTEM,
            null,
            "ACCOUNT",
            "1",
            Map.of("previousStatus", "ACTIVE", "newStatus", "SUSPENDED"),
            OffsetDateTime.now());
    given(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .willReturn(new PageImpl<>(List.of(auditLog)));

    var result =
        auditLogService.getLogs(null, null, null, null, PageRequest.of(0, 20));

    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).eventType()).isEqualTo("ACCOUNT_STATUS_CHANGED");
    assertThat(result.content().get(0).targetId()).isEqualTo("1");
  }
}
