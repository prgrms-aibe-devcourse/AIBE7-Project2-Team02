package org.example.matcheat.domain.account.dto;

import org.example.matcheat.domain.account.entity.AccountReportEntity;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import java.time.Instant;

public record AccountReportResultResponse(
        Long reportId,
        AccountReportStatus status,
        String adminResponse,
        Instant reviewedAt) {
    public static AccountReportResultResponse from(AccountReportEntity report) {
        return new AccountReportResultResponse(
                report.getId(), report.getStatus(), report.getAdminResponse(), report.getReviewedAt());
    }
}
