package org.example.matcheat.domain.account.dto;

import org.example.matcheat.domain.account.entity.AccountReportEntity;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.enums.AccountReportTargetType;

import java.time.Instant;

public record AccountReportResponse(
        Long reportId,
        String title,
        String message,
        AccountReportTargetType targetType,
        Long targetId,
        AccountReportStatus status,
        String adminResponse,
        Instant createdAt,
        Instant updatedAt,
        Instant reviewedAt) {
    public static AccountReportResponse from(AccountReportEntity report) {
        return new AccountReportResponse(
                report.getId(), report.getTitle(), report.getMessage(), report.getTargetType(), report.getTargetId(), report.getStatus(),
                report.getAdminResponse(), report.getCreatedAt(), report.getUpdatedAt(), report.getReviewedAt());
    }
}
