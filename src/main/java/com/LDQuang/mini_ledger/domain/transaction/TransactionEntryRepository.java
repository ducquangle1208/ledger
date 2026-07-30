package com.LDQuang.mini_ledger.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionEntryRepository extends JpaRepository<TransactionEntry, Long> {

    List<TransactionEntry> findByTransactionIdOrderByIdAsc(Long transactionId);

    long countByTransactionId(Long transactionId);
}
