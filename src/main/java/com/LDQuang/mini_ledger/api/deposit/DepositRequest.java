package com.LDQuang.mini_ledger.api.deposit;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull Long accountId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 500) String description
) {
}
