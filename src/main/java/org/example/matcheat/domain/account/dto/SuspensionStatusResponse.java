package org.example.matcheat.domain.account.dto;

import java.time.Instant;

public record SuspensionStatusResponse(
        String code,
        String reason,
        Instant expiresAt,
        boolean indefinite) {
}
