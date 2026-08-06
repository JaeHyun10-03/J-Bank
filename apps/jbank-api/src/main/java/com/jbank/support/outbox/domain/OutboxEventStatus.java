package com.jbank.support.outbox.domain;

public enum OutboxEventStatus {
  PENDING,
  PUBLISHED,
  FAILED
}
