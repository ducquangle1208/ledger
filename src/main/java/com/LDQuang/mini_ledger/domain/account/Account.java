package com.LDQuang.mini_ledger.domain.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Account(Long userId, String accountNumber, String currency) {
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.balance = BigDecimal.ZERO.setScale(2);
        this.status = AccountStatus.ACTIVE;
        this.version = 0L;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void assignAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        touch();
    }

    public void changeStatus(AccountStatus status) {
        this.status = status;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
