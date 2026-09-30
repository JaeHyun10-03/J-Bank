package com.jbank.transfer.service;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.repository.AccountRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.PageResponse;
import com.jbank.transfer.domain.PendingCredit;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.dto.TransactionHistoryFilter;
import com.jbank.transfer.dto.TransactionSummaryResponse;
import com.jbank.transfer.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;

  public TransactionHistoryService(
      AccountRepository accountRepository, TransactionRepository transactionRepository) {
    this.accountRepository = accountRepository;
    this.transactionRepository = transactionRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<TransactionSummaryResponse> getHistory(
      Long accountId,
      TransactionHistoryFilter filter,
      OffsetDateTime from,
      OffsetDateTime to,
      Pageable pageable,
      Long requestingCustomerId) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
    if (!account.getCustomerId().equals(requestingCustomerId)) {
      throw new AccountException(ErrorCode.COMMON_003_FORBIDDEN);
    }

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
      // 이체 입금은 수신 계좌에 비동기로 반영된다(ADR 0012). 반영 전에는 받는 사람의 잔액에도 내역에도
      // 보이지 않게, 이 계좌가 받을 미반영 입금 대기가 있는 거래는 뺀다. 반영 뒤에는 원래 거래 시각 위치에 나타난다.
      Subquery<Long> unapplied = query.subquery(Long.class);
      Root<PendingCredit> credit = unapplied.from(PendingCredit.class);
      unapplied
          .select(credit.get("pendingCreditId"))
          .where(
              cb.equal(credit.get("transactionId"), root.get("transactionId")),
              cb.equal(credit.get("accountId"), accountId),
              cb.isNull(credit.get("appliedAt")));
      predicates.add(cb.not(cb.exists(unapplied)));

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
