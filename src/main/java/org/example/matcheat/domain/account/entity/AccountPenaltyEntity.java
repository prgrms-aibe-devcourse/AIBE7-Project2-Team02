package org.example.matcheat.domain.account.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "account_penalties", indexes = {
        @Index(name = "idx_penalty_user_expires", columnList = "user_id, expires_at"),
        @Index(name = "idx_penalty_report", columnList = "report_id")
})
public class AccountPenaltyEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "penalty_id") private Long id;
    @Column(name = "report_id", nullable = false) private Long reportId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "issued_by", nullable = false) private Long issuedBy;
    @Column(name = "issued_at", nullable = false, updatable = false) private Instant issuedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "released_at") private Instant releasedAt;

    protected AccountPenaltyEntity() {}
    public static AccountPenaltyEntity issue(long reportId, long userId, String reason,
            long issuedBy, Instant issuedAt, Instant expiresAt) {
        var value = new AccountPenaltyEntity();
        value.reportId = reportId; value.userId = userId; value.reason = reason;
        value.issuedBy = issuedBy; value.issuedAt = issuedAt; value.expiresAt = expiresAt;
        return value;
    }
    public void release(Instant now) { releasedAt = now; }
    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public Long getUserId() { return userId; }
    public String getReason() { return reason; }
    public Long getIssuedBy() { return issuedBy; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getReleasedAt() { return releasedAt; }
}
