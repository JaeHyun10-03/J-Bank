package com.jbank.support.audit;

import com.jbank.common.event.AccountStatusChangedEvent;
import com.jbank.common.event.CustomerGradeChangedEvent;
import com.jbank.common.event.TransferCompletedEvent;
import com.jbank.support.audit.domain.ActorType;
import com.jbank.support.audit.domain.AuditLog;
import com.jbank.support.audit.repository.AuditLogRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 리스너. 이체 완료는 자금 이동 증적이라 본 트랜잭션과 반드시 함께 커밋돼야 해서 호출자 트랜잭션에 참여시켜(REQUIRED) 감사 기록 실패 시 이체 자체도
 * 롤백한다. 계좌 상태 변경과 등급 변경은 감사 기록 실패가 본 업무를 막으면 안 되는 이벤트라 REQUIRES_NEW로 분리하고 예외를 삼켜 로그만 남긴다.
 */
@Component
public class AuditLogListener {

  private static final Logger log = LoggerFactory.getLogger(AuditLogListener.class);

  private final AuditLogRepository auditLogRepository;

  public AuditLogListener(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  @EventListener
  public void onTransferCompleted(TransferCompletedEvent event) {
    auditLogRepository.save(
        new AuditLog(
            "TRANSFER_COMPLETED",
            ActorType.SYSTEM,
            null,
            "TRANSACTION",
            String.valueOf(event.transactionId()),
            Map.of(
                "fromAccountId", String.valueOf(event.fromAccountId()),
                "toAccountId", String.valueOf(event.toAccountId()),
                "amount", event.amount().toPlainString()),
            event.occurredAt()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @EventListener
  public void onAccountStatusChanged(AccountStatusChangedEvent event) {
    try {
      auditLogRepository.save(
          new AuditLog(
              "ACCOUNT_STATUS_CHANGED",
              ActorType.SYSTEM,
              null,
              "ACCOUNT",
              String.valueOf(event.accountId()),
              Map.of("previousStatus", event.previousStatus(), "newStatus", event.newStatus()),
              event.occurredAt()));
    } catch (RuntimeException e) {
      log.error("계좌 상태 변경 감사 로그 기록 실패: accountId={}", event.accountId(), e);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @EventListener
  public void onCustomerGradeChanged(CustomerGradeChangedEvent event) {
    try {
      auditLogRepository.save(
          new AuditLog(
              "CUSTOMER_GRADE_CHANGED",
              ActorType.SYSTEM,
              event.assessedBy(),
              "CUSTOMER",
              String.valueOf(event.customerId()),
              Map.of(
                  "previousKycGrade", event.previousKycGrade(),
                  "newKycGrade", event.newKycGrade(),
                  "previousAmlRiskLevel", event.previousAmlRiskLevel(),
                  "newAmlRiskLevel", event.newAmlRiskLevel()),
              event.occurredAt()));
    } catch (RuntimeException e) {
      log.error("고객 등급 변경 감사 로그 기록 실패: customerId={}", event.customerId(), e);
    }
  }
}
