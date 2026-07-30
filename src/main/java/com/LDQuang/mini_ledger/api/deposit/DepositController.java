package com.LDQuang.mini_ledger.api.deposit;

import com.LDQuang.mini_ledger.api.transaction.MoneyMovementResponse;
import com.LDQuang.mini_ledger.domain.idempotency.IdempotencyService;
import com.LDQuang.mini_ledger.domain.transaction.MoneyMovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deposits")
@RequiredArgsConstructor
public class DepositController {

    private final MoneyMovementService moneyMovementService;

    @PostMapping
    public ResponseEntity<MoneyMovementResponse> deposit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        IdempotencyService.IdempotencyResult<MoneyMovementResponse> result =
                moneyMovementService.deposit(request, idempotencyKey);
        MoneyMovementResponse response = result.replayed()
                ? result.response().asReplayed()
                : result.response();
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
