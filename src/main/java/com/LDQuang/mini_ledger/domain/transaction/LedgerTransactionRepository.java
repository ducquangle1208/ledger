package com.LDQuang.mini_ledger.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByReferenceCode(String referenceCode);
}
