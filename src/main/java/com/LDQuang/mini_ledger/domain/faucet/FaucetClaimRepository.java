package com.LDQuang.mini_ledger.domain.faucet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface FaucetClaimRepository extends JpaRepository<FaucetClaim, Long> {

    boolean existsByUserIdAndClaimedOn(Long userId, LocalDate claimedOn);

    Optional<FaucetClaim> findByIdempotencyKey(String idempotencyKey);

    Optional<FaucetClaim> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
