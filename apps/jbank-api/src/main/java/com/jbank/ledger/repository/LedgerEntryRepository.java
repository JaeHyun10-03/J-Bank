package com.jbank.ledger.repository;

import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** append-only 원장이라 delete류 메서드를 의도적으로 노출하지 않는다(ERD 문서 2.3절). */
public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

  LedgerEntry save(LedgerEntry entry);

  List<LedgerEntry> findByAccountId(Long accountId);

  // 원장 정합성 대사 배치(구현계획 W5)가 계좌별 캐시 잔액과 비교할 때 쓴다.
  @Query(
      "select new com.jbank.ledger.repository.AccountLedgerBalance(le.accountId, "
          + "sum(case when le.entryType = com.jbank.ledger.domain.EntryType.CREDIT then le.amount else -le.amount end)) "
          + "from LedgerEntry le group by le.accountId")
  List<AccountLedgerBalance> sumBalanceByAccount();

  // 전체 차변/대변 합 일치 검증(구현계획 W5)에 쓴다.
  @Query("select coalesce(sum(le.amount), 0) from LedgerEntry le where le.entryType = :entryType")
  BigDecimal sumAmountByEntryType(@Param("entryType") EntryType entryType);
}
