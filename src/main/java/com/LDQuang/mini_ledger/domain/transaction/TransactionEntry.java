package com.LDQuang.mini_ledger.domain.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private TransactionEntry(LedgerTransaction transaction, Long accountId, EntryType entryType,
                             BigDecimal amount, BigDecimal balanceAfter) {
        this.transaction = transaction;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = Instant.now();
    }

    public static TransactionEntry debit(LedgerTransaction transaction, Long accountId,
                                         BigDecimal amount, BigDecimal balanceAfter) {
        return new TransactionEntry(transaction, accountId, EntryType.DEBIT, amount, balanceAfter);
    }

    public static TransactionEntry credit(LedgerTransaction transaction, Long accountId,
                                          BigDecimal amount, BigDecimal balanceAfter) {
        return new TransactionEntry(transaction, accountId, EntryType.CREDIT, amount, balanceAfter);
    }
}
