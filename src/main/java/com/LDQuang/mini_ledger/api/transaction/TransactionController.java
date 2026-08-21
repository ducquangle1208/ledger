package com.LDQuang.mini_ledger.api.transaction;

import com.LDQuang.mini_ledger.domain.transaction.MoneyMovementService;
import com.LDQuang.mini_ledger.domain.transaction.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final MoneyMovementService moneyMovementService;
    private final TransactionHistoryService transactionHistoryService;

    @GetMapping("/api/v1/transactions/{transactionId}")
    public TransactionDetailResponse getById(@PathVariable Long transactionId, Authentication authentication) {
        return TransactionDetailResponse.from(
                transactionHistoryService.getOwnedTransaction(userId(authentication), transactionId),
                moneyMovementService.getEntries(transactionId));
    }

    @GetMapping("/api/v1/accounts/{accountId}/transactions")
    public TransactionHistoryResponse history(
            @PathVariable Long accountId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            Authentication authentication) {
        return transactionHistoryService.history(userId(authentication), accountId, cursor, limit);
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
