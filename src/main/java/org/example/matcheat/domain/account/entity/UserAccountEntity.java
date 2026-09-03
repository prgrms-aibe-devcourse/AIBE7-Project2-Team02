package org.example.matcheat.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.example.matcheat.domain.account.enums.UserRole;
import org.example.matcheat.domain.account.enums.UserStatus;

import java.time.Instant;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class UserAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "manual_suspension", nullable = false)
    private boolean manualSuspension;

    @Column(name = "manual_suspension_reason", length = 500)
    private String manualSuspensionReason;

    protected UserAccountEntity() {
    }

    private UserAccountEntity(UserAccount account) {
        this.id = account.id();
        this.email = account.email();
        this.passwordHash = account.passwordHash();
        this.name = account.name();
        this.role = account.role();
        this.status = account.status();
        this.tokenVersion = account.tokenVersion();
        this.withdrawnAt = account.withdrawnAt();
    }

    public static UserAccountEntity fromDomain(UserAccount account) {
        return new UserAccountEntity(account);
    }

    public UserAccount toDomain() {
        return UserAccount.restore(id, email, passwordHash, name, role, status, tokenVersion, withdrawnAt);
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        tokenVersion++;
    }

    public void withdraw(Instant withdrawnAt) {
        status = UserStatus.WITHDRAWN;
        this.withdrawnAt = withdrawnAt;
        tokenVersion++;
    }

    public void changeStatus(UserStatus targetStatus, String suspensionReason) {
        if (targetStatus == UserStatus.SUSPENDED && status != UserStatus.SUSPENDED) {
            tokenVersion++;
        }
        status = targetStatus;
        manualSuspension = targetStatus == UserStatus.SUSPENDED;
        manualSuspensionReason = manualSuspension ? suspensionReason : null;
    }

    public void changeStatus(UserStatus targetStatus) {
        changeStatus(targetStatus, targetStatus == UserStatus.SUSPENDED ? "관리자 수동 정지" : null);
    }

    public void suspendForPenalty() {
        if (status != UserStatus.SUSPENDED) {
            tokenVersion++;
        }
        status = UserStatus.SUSPENDED;
    }

    public void activateAfterPenalty() {
        if (!manualSuspension && status == UserStatus.SUSPENDED) {
            status = UserStatus.ACTIVE;
        }
    }

    public boolean manualSuspension() {
        return manualSuspension;
    }

    public String manualSuspensionReason() { return manualSuspensionReason; }

    public void promoteToSeller() {
        if (role == UserRole.SELLER) {
            return;
        }
        if (role != UserRole.USER) {
            throw new IllegalStateException("일반 회원만 판매자로 승격할 수 있습니다.");
        }
        role = UserRole.SELLER;
        tokenVersion++;
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    public UserRole role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }

    public int tokenVersion() {
        return tokenVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
