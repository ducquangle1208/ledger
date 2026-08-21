package com.LDQuang.mini_ledger.api.transfer;

import com.LDQuang.mini_ledger.api.transaction.MoneyMovementResponse;
import com.LDQuang.mini_ledger.domain.idempotency.IdempotencyService;
import com.LDQuang.mini_ledger.domain.transaction.MoneyMovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final MoneyMovementService moneyMovementService;

    @PostMapping
    public ResponseEntity<MoneyMovementResponse> transfer(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        IdempotencyService.IdempotencyResult<MoneyMovementResponse> result =
                moneyMovementService.transfer(Long.valueOf(authentication.getName()), request, idempotencyKey);
        MoneyMovementResponse response = result.replayed()
                ? result.response().asReplayed()
                : result.response();
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
