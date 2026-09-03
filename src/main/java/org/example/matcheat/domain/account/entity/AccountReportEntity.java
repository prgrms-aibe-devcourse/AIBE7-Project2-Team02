package org.example.matcheat.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.enums.AccountReportTargetType;

import java.time.Instant;

@Entity
@Table(name = "account_reports", indexes = {
        @Index(name = "idx_account_reports_reporter_created", columnList = "reporter_id, created_at"),
        @Index(name = "idx_account_reports_status_created", columnList = "status, created_at")
})
public class AccountReportEntity {
    @Version
    @Column(nullable = false)
    private long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_user_id")
    private Long reportedUserId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private AccountReportTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_snapshot", columnDefinition = "TEXT")
    private String targetSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountReportStatus status;

    @Column(name = "admin_response", length = 2000)
    private String adminResponse;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected AccountReportEntity() {
    }

    private AccountReportEntity(
            Long reporterId,
            Long reportedUserId,
            String title,
            String message,
            AccountReportTargetType targetType,
            Long targetId,
            Instant now) {
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.title = title;
        this.message = message;
        this.targetType = targetType;
        this.targetId = targetId;
        this.status = AccountReportStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static AccountReportEntity create(
            Long reporterId,
            Long reportedUserId,
            String title,
            String message,
            AccountReportTargetType targetType,
            Long targetId,
            Instant now) {
        return new AccountReportEntity(reporterId, reportedUserId, title, message, targetType, targetId, now);
    }

    public void review(AccountReportStatus targetStatus, String response, Long adminId, Instant now) {
        if (status == AccountReportStatus.RESOLVED || status == AccountReportStatus.REJECTED) {
            throw new IllegalStateException("이미 처리가 완료된 신고입니다.");
        }
        if (targetStatus == AccountReportStatus.PENDING) {
            throw new IllegalArgumentException("신고를 대기 상태로 되돌릴 수 없습니다.");
        }
        status = targetStatus;
        adminResponse = response;
        reviewedBy = adminId;
        reviewedAt = now;
        updatedAt = now;
    }

    public void captureSnapshot(String targetSnapshot) {
        this.targetSnapshot = targetSnapshot;
    }

    public Long getId() {
        return id;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public Long getReportedUserId() {
        return reportedUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public AccountReportTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getTargetSnapshot() {
        return targetSnapshot;
    }

    public AccountReportStatus getStatus() {
        return status;
    }

    public String getAdminResponse() {
        return adminResponse;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
