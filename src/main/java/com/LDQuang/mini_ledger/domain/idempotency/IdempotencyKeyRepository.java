package com.LDQuang.mini_ledger.domain.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (key, request_hash, created_at)
            VALUES (:key, :requestHash, now())
            ON CONFLICT (key) DO NOTHING
            """, nativeQuery = true)
    int reserve(@Param("key") String key, @Param("requestHash") String requestHash);
}
