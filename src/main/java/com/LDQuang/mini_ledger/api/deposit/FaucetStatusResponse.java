package com.LDQuang.mini_ledger.api.deposit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FaucetStatusResponse(
        boolean available,
        BigDecimal amount,
        LocalDate claimedOn,
        Instant nextAvailableAt
) {
}
