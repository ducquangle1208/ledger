package com.LDQuang.mini_ledger.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByReferenceCode(String referenceCode);

    @Query("""
            SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END
            FROM LedgerTransaction t
            JOIN TransactionEntry e ON e.transaction = t
            JOIN Account a ON a.id = e.accountId
            WHERE t.id = :transactionId AND a.userId = :userId
            """)
    boolean existsOwnedByUser(@Param("transactionId") Long transactionId, @Param("userId") Long userId);
}
