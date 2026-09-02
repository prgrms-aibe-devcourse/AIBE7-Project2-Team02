package org.example.matcheat.domain.account.dto;

import org.example.matcheat.domain.account.entity.AccountReportHistoryEntity;
import org.example.matcheat.domain.account.enums.AccountReportStatus;

import java.time.Instant;

public record AccountReportHistoryResponse(
        Long historyId,
        AccountReportStatus previousStatus,
        AccountReportStatus newStatus,
        Long actorId,
        String adminResponse,
        Instant changedAt) {
    public static AccountReportHistoryResponse from(AccountReportHistoryEntity history) {
        return new AccountReportHistoryResponse(
                history.getId(), history.getPreviousStatus(), history.getNewStatus(), history.getActorId(),
                history.getAdminResponse(), history.getChangedAt());
    }
}
