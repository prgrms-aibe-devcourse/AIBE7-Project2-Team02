package org.example.matcheat.domain.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.matcheat.domain.account.enums.AccountReportStatus;

public record AccountReportReviewRequest(
        @NotNull(message = "처리 상태를 선택해 주세요.")
        AccountReportStatus status,
        @Size(max = 2000, message = "관리자 답변은 2000자 이하여야 합니다.")
        String adminResponse) {
}
