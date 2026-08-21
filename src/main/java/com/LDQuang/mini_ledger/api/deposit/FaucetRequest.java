package com.LDQuang.mini_ledger.api.deposit;

import jakarta.validation.constraints.NotNull;

public record FaucetRequest(@NotNull Long accountId) {
}
