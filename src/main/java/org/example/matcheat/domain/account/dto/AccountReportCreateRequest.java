package org.example.matcheat.domain.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.matcheat.domain.account.enums.AccountReportTargetType;

public record AccountReportCreateRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title,
        @NotBlank(message = "신고 내용을 입력해 주세요.")
        @Size(max = 2000, message = "신고 내용은 2000자 이하여야 합니다.")
        String message,
        AccountReportTargetType targetType,
        @Positive(message = "신고 대상 ID는 양수여야 합니다.")
        Long targetId) {
}
