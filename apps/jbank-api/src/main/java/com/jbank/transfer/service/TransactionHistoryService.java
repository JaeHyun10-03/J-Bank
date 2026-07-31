package com.jbank.transfer.service;

import com.jbank.global.response.PageResponse;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.dto.TransactionHistoryFilter;
import com.jbank.transfer.dto.TransactionSummaryResponse;
import com.jbank.transfer.repository.TransactionRepository;
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
public class TransactionHistoryService {

  private final TransactionRepository transactionRepository;

  public TransactionHistoryService(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<TransactionSummaryResponse> getHistory(
      Long accountId,
      TransactionHistoryFilter filter,
      OffsetDateTime from,
      OffsetDateTime to,
      Pageable pageable) {
    Page<Transaction> page =
        transactionRepository.findAll(buildSpecification(accountId, filter, from, to), pageable);
    return PageResponse.from(page.map(TransactionHistoryService::toSummary));
  }

  private Specification<Transaction> buildSpecification(
      Long accountId, TransactionHistoryFilter filter, OffsetDateTime from, OffsetDateTime to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(
          cb.or(
              cb.equal(root.get("fromAccountId"), accountId),
              cb.equal(root.get("toAccountId"), accountId)));

      if (filter != null) {
        switch (filter) {
          case DEPOSIT ->
              predicates.add(cb.equal(root.get("transactionType"), TransactionType.DEPOSIT));
          case WITHDRAWAL ->
              predicates.add(cb.equal(root.get("transactionType"), TransactionType.WITHDRAWAL));
          case TRANSFER_IN -> {
            predicates.add(cb.equal(root.get("transactionType"), TransactionType.TRANSFER));
            predicates.add(cb.equal(root.get("toAccountId"), accountId));
          }
          case TRANSFER_OUT -> {
            predicates.add(cb.equal(root.get("transactionType"), TransactionType.TRANSFER));
            predicates.add(cb.equal(root.get("fromAccountId"), accountId));
          }
        }
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static TransactionSummaryResponse toSummary(Transaction transaction) {
    return new TransactionSummaryResponse(
        String.valueOf(transaction.getTransactionId()),
        transaction.getTransactionType(),
        transaction.getAmount(),
        transaction.getStatus(),
        transaction.getMemo(),
        transaction.getProcessedAt(),
        transaction.getCreatedAt());
  }
}
