package com.jbank.ledger.repository;

import com.jbank.ledger.domain.LedgerEntry;
import java.util.List;
import org.springframework.data.repository.Repository;

/** append-only 원장이라 delete류 메서드를 의도적으로 노출하지 않는다(ERD 문서 2.3절). */
public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

  LedgerEntry save(LedgerEntry entry);

  List<LedgerEntry> findByAccountId(Long accountId);
}
