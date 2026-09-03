package org.example.matcheat.domain.account.dto;

import org.example.matcheat.domain.account.entity.AccountReportEntity;
import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.enums.AccountReportTargetType;

import java.time.Instant;

public record AdminAccountReportResponse(
        Long reportId,
        Long reporterId,
        String reporterName,
        String reporterEmail,
        Long reportedUserId,
        String title,
        String message,
        AccountReportTargetType targetType,
        Long targetId,
        String targetSnapshot,
        AccountReportStatus status,
        String adminResponse,
        Long reviewedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant reviewedAt) {
    public static AdminAccountReportResponse from(AccountReportEntity report, UserAccount reporter) {
        return new AdminAccountReportResponse(
                report.getId(), report.getReporterId(), reporter.name(), reporter.email(), report.getReportedUserId(),
                report.getTitle(), report.getMessage(), report.getTargetType(), report.getTargetId(), report.getTargetSnapshot(),
                report.getStatus(), report.getAdminResponse(),
                report.getReviewedBy(), report.getCreatedAt(), report.getUpdatedAt(), report.getReviewedAt());
    }
}
