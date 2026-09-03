package org.example.matcheat.domain.account.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "account_report_attachments", indexes =
        @Index(name = "idx_report_attachment_report", columnList = "report_id"))
public class AccountReportAttachmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long id;
    @Column(name = "report_id", nullable = false)
    private Long reportId;
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    @Column(name = "stored_name", nullable = false, unique = true, length = 100)
    private String storedName;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "file_size", nullable = false)
    private long fileSize;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountReportAttachmentEntity() {}

    public static AccountReportAttachmentEntity create(
            Long reportId, String originalName, String storedName, String contentType, long fileSize, Instant now) {
        var value = new AccountReportAttachmentEntity();
        value.reportId = reportId;
        value.originalName = originalName;
        value.storedName = storedName;
        value.contentType = contentType;
        value.fileSize = fileSize;
        value.createdAt = now;
        return value;
    }

    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public Instant getCreatedAt() { return createdAt; }
}
