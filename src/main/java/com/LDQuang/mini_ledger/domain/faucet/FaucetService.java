package com.LDQuang.mini_ledger.domain.faucet;

import com.LDQuang.mini_ledger.api.deposit.DepositRequest;
import com.LDQuang.mini_ledger.api.deposit.FaucetStatusResponse;
import com.LDQuang.mini_ledger.api.error.BusinessException;
import com.LDQuang.mini_ledger.api.error.ErrorCode;
import com.LDQuang.mini_ledger.api.transaction.MoneyMovementResponse;
import com.LDQuang.mini_ledger.domain.account.AccountRepository;
import com.LDQuang.mini_ledger.domain.idempotency.IdempotencyService;
import com.LDQuang.mini_ledger.domain.transaction.MoneyMovementService;
import com.LDQuang.mini_ledger.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FaucetService {

    public static final BigDecimal DAILY_AMOUNT = new BigDecimal("100000.00");

    private final FaucetClaimRepository faucetClaimRepository;
    private final AccountRepository accountRepository;
    private final MoneyMovementService moneyMovementService;
    private final UserService userService;

    @Transactional(rollbackFor = Exception.class)
    public IdempotencyService.IdempotencyResult<MoneyMovementResponse> claim(
            Long userId, Long accountId, String idempotencyKey) {
        // Always lock the user before checking quota or taking money-movement locks.
        userService.lockForQuota(userId);
        // Do not preload a managed Account with a balance that may change before lockPair.
        String currency = accountRepository.findOwnedCurrency(accountId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account not found", Map.of("accountId", accountId)));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        DepositRequest deposit = new DepositRequest(
                accountId, DAILY_AMOUNT, currency.trim(), "Daily demo funds");

        if (faucetClaimRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return moneyMovementService.depositInternal(deposit, idempotencyKey, "user:" + userId);
        }
        if (faucetClaimRepository.existsByUserIdAndClaimedOn(userId, today)) {
            throw new BusinessException(ErrorCode.FAUCET_LIMIT_REACHED,
                    "Demo funds can be claimed once per UTC day",
                    Map.of("nextAvailableAt", nextAvailableAt(today)));
        }

        // Reserve the global idempotency key before inserting the claim. A collision
        // across users must return IDEMPOTENCY_CONFLICT, not a unique-constraint error.
        IdempotencyService.IdempotencyResult<MoneyMovementResponse> result =
                moneyMovementService.depositInternal(deposit, idempotencyKey, "user:" + userId);
        FaucetClaim claim = new FaucetClaim(userId, accountId, idempotencyKey, DAILY_AMOUNT, today);
        claim.complete(result.response().transactionId());
        faucetClaimRepository.save(claim);
        return result;
    }

    @Transactional(readOnly = true)
    public FaucetStatusResponse status(Long userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return faucetClaimRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .filter(claim -> claim.getClaimedOn().equals(today))
                .map(claim -> new FaucetStatusResponse(
                        false, DAILY_AMOUNT, claim.getClaimedOn(), nextAvailableAt(today)))
                .orElseGet(() -> new FaucetStatusResponse(true, DAILY_AMOUNT, null, null));
    }

    private Instant nextAvailableAt(LocalDate day) {
        return day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
