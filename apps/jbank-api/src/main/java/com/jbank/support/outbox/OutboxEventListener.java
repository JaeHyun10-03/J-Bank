package com.jbank.support.outbox;

import com.jbank.common.event.TransferCompletedEvent;
import com.jbank.support.outbox.domain.OutboxEvent;
import com.jbank.support.outbox.repository.OutboxEventRepository;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 발신함 리스너. 이체 완료는 커밋-발행 원자성이 요구되는 이벤트라(발신함 패턴) 감사 로그와
 * 마찬가지로 호출자 트랜잭션에 참여시켜(REQUIRED) 저장 실패 시 이체 자체도 롤백한다.
 */
@Component
public class OutboxEventListener {

  private final OutboxEventRepository outboxEventRepository;

  public OutboxEventListener(OutboxEventRepository outboxEventRepository) {
    this.outboxEventRepository = outboxEventRepository;
  }

  @EventListener
  public void onTransferCompleted(TransferCompletedEvent event) {
    outboxEventRepository.save(
        new OutboxEvent(
            "TRANSACTION",
            String.valueOf(event.transactionId()),
            "TRANSFER_COMPLETED",
            Map.of(
                "transactionId", String.valueOf(event.transactionId()),
                "fromAccountId", String.valueOf(event.fromAccountId()),
                "toAccountId", String.valueOf(event.toAccountId()),
                "amount", event.amount().toPlainString()),
            event.occurredAt()));
  }
}
