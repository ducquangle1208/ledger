package com.LDQuang.mini_ledger.domain.faucet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "faucet_claims")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaucetClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "transaction_id", unique = true)
    private Long transactionId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "claimed_on", nullable = false)
    private LocalDate claimedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FaucetClaim(Long userId, Long accountId, String idempotencyKey, BigDecimal amount, LocalDate claimedOn) {
        this.userId = userId;
        this.accountId = accountId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.claimedOn = claimedOn;
        this.createdAt = Instant.now();
    }

    public void complete(Long transactionId) {
        this.transactionId = transactionId;
    }
}
