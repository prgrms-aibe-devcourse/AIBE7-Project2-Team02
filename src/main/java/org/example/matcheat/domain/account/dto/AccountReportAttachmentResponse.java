package org.example.matcheat.domain.account.dto;

import org.example.matcheat.domain.account.entity.AccountReportAttachmentEntity;
import java.time.Instant;

public record AccountReportAttachmentResponse(
        Long attachmentId, String originalName, String contentType, long fileSize, Instant createdAt) {
    public static AccountReportAttachmentResponse from(AccountReportAttachmentEntity value) {
        return new AccountReportAttachmentResponse(
                value.getId(), value.getOriginalName(), value.getContentType(), value.getFileSize(), value.getCreatedAt());
    }
}
