package com.LDQuang.mini_ledger.api.deposit;

import com.LDQuang.mini_ledger.api.transaction.MoneyMovementResponse;
import com.LDQuang.mini_ledger.domain.faucet.FaucetService;
import com.LDQuang.mini_ledger.domain.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/faucet")
@RequiredArgsConstructor
public class DepositController {

    private final FaucetService faucetService;

    @GetMapping("/status")
    public FaucetStatusResponse status(Authentication authentication) {
        return faucetService.status(Long.valueOf(authentication.getName()));
    }

    @PostMapping("/claims")
    public ResponseEntity<MoneyMovementResponse> claim(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FaucetRequest request) {
        IdempotencyService.IdempotencyResult<MoneyMovementResponse> result = faucetService.claim(
                Long.valueOf(authentication.getName()), request.accountId(), idempotencyKey);
        MoneyMovementResponse response = result.replayed()
                ? result.response().asReplayed()
                : result.response();
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
