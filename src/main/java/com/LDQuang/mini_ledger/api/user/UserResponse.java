package com.LDQuang.mini_ledger.api.user;

import com.LDQuang.mini_ledger.domain.user.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
