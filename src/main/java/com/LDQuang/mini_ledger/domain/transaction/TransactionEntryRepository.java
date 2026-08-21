package com.LDQuang.mini_ledger.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;

public interface TransactionEntryRepository extends JpaRepository<TransactionEntry, Long> {

    List<TransactionEntry> findByTransactionIdOrderByIdAsc(Long transactionId);

    long countByTransactionId(Long transactionId);

    @Query(value = """
            SELECT e.id AS entryId, t.id AS transactionId, t.type AS transactionType,
                   t.status AS transactionStatus, e.entry_type AS entryType, e.amount AS amount,
                   t.currency AS currency, t.description AS description, e.balance_after AS balanceAfter,
                   cp.account_number AS counterpartyAccountNumber, e.created_at AS createdAt
            FROM transaction_entries e
            JOIN transactions t ON t.id = e.transaction_id
            LEFT JOIN transaction_entries other_entry
                   ON other_entry.transaction_id = e.transaction_id AND other_entry.id <> e.id
            LEFT JOIN accounts cp ON cp.id = other_entry.account_id
            WHERE e.account_id = :accountId
            ORDER BY e.created_at DESC, e.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TransactionHistoryProjection> findFirstHistoryPage(
            @Param("accountId") Long accountId,
            @Param("limit") int limit);

    @Query(value = """
            SELECT e.id AS entryId, t.id AS transactionId, t.type AS transactionType,
                   t.status AS transactionStatus, e.entry_type AS entryType, e.amount AS amount,
                   t.currency AS currency, t.description AS description, e.balance_after AS balanceAfter,
                   cp.account_number AS counterpartyAccountNumber, e.created_at AS createdAt
            FROM transaction_entries e
            JOIN transactions t ON t.id = e.transaction_id
            LEFT JOIN transaction_entries other_entry
                   ON other_entry.transaction_id = e.transaction_id AND other_entry.id <> e.id
            LEFT JOIN accounts cp ON cp.id = other_entry.account_id
            WHERE e.account_id = :accountId
              AND (e.created_at, e.id) < (:cursorCreatedAt, :cursorId)
            ORDER BY e.created_at DESC, e.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TransactionHistoryProjection> findHistoryAfter(
            @Param("accountId") Long accountId,
            @Param("cursorCreatedAt") Timestamp cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
