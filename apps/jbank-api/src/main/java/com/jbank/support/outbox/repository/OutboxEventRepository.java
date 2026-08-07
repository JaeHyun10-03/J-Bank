package com.jbank.support.outbox.repository;

import com.jbank.support.outbox.domain.OutboxEvent;
import com.jbank.support.outbox.domain.OutboxEventStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  List<OutboxEvent> findByStatusOrderByOccurredAtAsc(OutboxEventStatus status, Pageable pageable);
}
