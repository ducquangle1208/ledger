package com.LDQuang.mini_ledger.api.transaction;

import com.LDQuang.mini_ledger.domain.transaction.MoneyMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final MoneyMovementService moneyMovementService;

    @GetMapping("/{transactionId}")
    public TransactionDetailResponse getById(@PathVariable Long transactionId) {
        return TransactionDetailResponse.from(
                moneyMovementService.getTransaction(transactionId),
                moneyMovementService.getEntries(transactionId));
    }
}
