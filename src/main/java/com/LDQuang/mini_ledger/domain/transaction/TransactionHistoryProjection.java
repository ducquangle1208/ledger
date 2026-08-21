package com.LDQuang.mini_ledger.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;

public interface TransactionHistoryProjection {

    Long getEntryId();

    Long getTransactionId();

    String getTransactionType();

    String getTransactionStatus();

    String getEntryType();

    BigDecimal getAmount();

    String getCurrency();

    String getDescription();

    BigDecimal getBalanceAfter();

    String getCounterpartyAccountNumber();

    Instant getCreatedAt();
}
