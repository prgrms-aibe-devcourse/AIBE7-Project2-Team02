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
import org.example.matcheat.domain.account.enums.AccountReportStatus;

import java.time.Instant;

@Entity
@Table(name = "account_report_histories", indexes =
        @Index(name = "idx_account_report_history_report_changed", columnList = "report_id, changed_at"))
public class AccountReportHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private AccountReportStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private AccountReportStatus newStatus;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "admin_response", length = 2000)
    private String adminResponse;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected AccountReportHistoryEntity() {
    }

    private AccountReportHistoryEntity(
            Long reportId,
            AccountReportStatus previousStatus,
            AccountReportStatus newStatus,
            Long actorId,
            String adminResponse,
            Instant changedAt) {
        this.reportId = reportId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.actorId = actorId;
        this.adminResponse = adminResponse;
        this.changedAt = changedAt;
    }

    public static AccountReportHistoryEntity record(
            Long reportId,
            AccountReportStatus previousStatus,
            AccountReportStatus newStatus,
            Long actorId,
            String adminResponse,
            Instant changedAt) {
        return new AccountReportHistoryEntity(
                reportId, previousStatus, newStatus, actorId, adminResponse, changedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getReportId() {
        return reportId;
    }

    public AccountReportStatus getPreviousStatus() {
        return previousStatus;
    }

    public AccountReportStatus getNewStatus() {
        return newStatus;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getAdminResponse() {
        return adminResponse;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
