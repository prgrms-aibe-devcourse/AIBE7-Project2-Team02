package org.example.matcheat.domain.account.repository;

import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.enums.UserRole;
import org.example.matcheat.domain.account.enums.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AdminAccountRepository {
    PageResult<UserSummary> searchUsers(String keyword, UserStatus status, int page, int size);

    Optional<UserSummary> findUser(long userId);

    Optional<UserSummary> changeUserStatus(long userId, UserStatus status, String suspensionReason);

    default Optional<UserSummary> changeUserStatus(long userId, UserStatus status) {
        return changeUserStatus(userId, status, status == UserStatus.SUSPENDED ? "관리자 수동 정지" : null);
    }

    Optional<UserSummary> suspendForPenalty(long userId);

    Optional<UserSummary> activateAfterPenalty(long userId);

    PageResult<SellerSummary> searchSellerApplications(
            SellerVerificationStatus status, int page, int size);

    Optional<SellerSummary> findSellerApplication(long sellerId);

    Optional<SellerSummary> reviewSellerApplication(
            long sellerId,
            long reviewerId,
            SellerVerificationStatus status,
            String rejectionReason,
            Instant reviewedAt);

    long countUsers();

    long countPendingSellerApplications();

    record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    record UserSummary(
            Long userId,
            String email,
            String name,
            UserRole role,
            UserStatus status,
            int tokenVersion,
            Instant createdAt) {
    }

    record SellerSummary(
            Long sellerId,
            Long userId,
            String userEmail,
            String userName,
            String businessName,
            String businessNumber,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal deliveryRadiusKm,
            SellerVerificationStatus status,
            String rejectionReason,
            Instant appliedAt,
            Instant reviewedAt) {
    }
}
