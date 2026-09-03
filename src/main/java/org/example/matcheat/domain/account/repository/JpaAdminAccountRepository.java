package org.example.matcheat.domain.account.repository;

import org.example.matcheat.domain.account.entity.SellerProfileEntity;
import org.example.matcheat.domain.account.entity.UserAccountEntity;
import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaAdminAccountRepository implements AdminAccountRepository {
    private final UserAccountJpaRepository users;
    private final SellerProfileJpaRepository sellers;

    public JpaAdminAccountRepository(
            UserAccountJpaRepository users,
            SellerProfileJpaRepository sellers) {
        this.users = users;
        this.sellers = sellers;
    }

    @Override
    public PageResult<UserSummary> searchUsers(String keyword, UserStatus status, int page, int size) {
        Page<UserAccountEntity> result = users.search(
                keyword,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResult<>(
                result.stream().map(JpaAdminAccountRepository::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public Optional<UserSummary> findUser(long userId) {
        return users.findById(userId).map(JpaAdminAccountRepository::toSummary);
    }

    @Override
    public Optional<UserSummary> changeUserStatus(long userId, UserStatus status, String suspensionReason) {
        return users.findById(userId).map(user -> {
            user.changeStatus(status, suspensionReason);
            return toSummary(user);
        });
    }

    @Override
    public Optional<UserSummary> suspendForPenalty(long userId) {
        return users.findById(userId).map(user -> {
            user.suspendForPenalty();
            return toSummary(user);
        });
    }

    @Override
    public Optional<UserSummary> activateAfterPenalty(long userId) {
        return users.findById(userId).map(user -> {
            user.activateAfterPenalty();
            return toSummary(user);
        });
    }

    @Override
    public PageResult<SellerSummary> searchSellerApplications(
            SellerVerificationStatus status, int page, int size) {
        Page<SellerProfileEntity> result = sellers.search(
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appliedAt")));
        return new PageResult<>(
                result.stream().map(JpaAdminAccountRepository::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public Optional<SellerSummary> findSellerApplication(long sellerId) {
        return sellers.findById(sellerId).map(JpaAdminAccountRepository::toSummary);
    }

    @Override
    public Optional<SellerSummary> reviewSellerApplication(
            long sellerId,
            long reviewerId,
            SellerVerificationStatus status,
            String rejectionReason,
            Instant reviewedAt) {
        Optional<UserAccountEntity> reviewer = users.findById(reviewerId);
        if (reviewer.isEmpty()) {
            return Optional.empty();
        }
        return sellers.findById(sellerId).map(seller -> {
            if (status == SellerVerificationStatus.APPROVED) {
                seller.approve(reviewer.get(), reviewedAt);
            } else {
                seller.reject(reviewer.get(), rejectionReason, reviewedAt);
            }
            sellers.flush();
            return toSummary(seller);
        });
    }

    @Override
    public long countUsers() {
        return users.count();
    }

    @Override
    public long countPendingSellerApplications() {
        return sellers.countByVerificationStatus(SellerVerificationStatus.PENDING);
    }

    private static UserSummary toSummary(UserAccountEntity user) {
        return new UserSummary(
                user.id(), user.email(), user.name(), user.role(), user.status(),
                user.tokenVersion(), user.createdAt());
    }

    private static SellerSummary toSummary(SellerProfileEntity seller) {
        return new SellerSummary(
                seller.id(), seller.userId(), seller.userEmail(), seller.userName(),
                seller.businessName(), seller.businessNumber(), seller.latitude(), seller.longitude(),
                seller.deliveryRadiusKm(), seller.verificationStatus(), seller.rejectionReason(),
                seller.appliedAt(), seller.reviewedAt());
    }
}
