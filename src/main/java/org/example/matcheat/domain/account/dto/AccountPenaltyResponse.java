package org.example.matcheat.domain.account.dto;

import org.example.matcheat.domain.account.entity.AccountPenaltyEntity;
import java.time.Instant;

public record AccountPenaltyResponse(Long penaltyId, Long reportId, Long userId, String reason,
        Long issuedBy, Instant issuedAt, Instant expiresAt, Instant releasedAt) {
    public static AccountPenaltyResponse from(AccountPenaltyEntity value) {
        return new AccountPenaltyResponse(value.getId(), value.getReportId(), value.getUserId(), value.getReason(),
                value.getIssuedBy(), value.getIssuedAt(), value.getExpiresAt(), value.getReleasedAt());
    }
}
