package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.AdminAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;

@Service
public class AdminAccountService {
    private final AdminAccountRepository repository;
    private final org.example.matcheat.domain.account.repository.AccountPenaltyRepository penalties;
    private final Clock clock;

    public AdminAccountService(AdminAccountRepository repository,
            org.example.matcheat.domain.account.repository.AccountPenaltyRepository penalties,
            Clock accountClock) {
        this.repository = repository;
        this.penalties = penalties;
        this.clock = accountClock;
    }

    @Transactional(readOnly = true)
    public DashboardResult dashboard() {
        return new DashboardResult(repository.countUsers(), repository.countPendingSellerApplications());
    }

    @Transactional(readOnly = true)
    public AdminAccountRepository.PageResult<AdminAccountRepository.UserSummary> searchUsers(
            String keyword, UserStatus status, int page, int size) {
        validatePage(page, size);
        return repository.searchUsers(keyword == null ? "" : keyword.trim(), status, page, size);
    }

    @Transactional
    public AdminAccountRepository.UserSummary changeUserStatus(
            long adminId, long userId, UserStatus targetStatus, String reason) {
        if (targetStatus == null || targetStatus == UserStatus.WITHDRAWN) {
            throw validation("회원 상태는 ACTIVE 또는 SUSPENDED만 지정할 수 있습니다.");
        }
        if (adminId == userId && targetStatus == UserStatus.SUSPENDED) {
            throw new AccountApplicationException(
                    AccountErrorCode.CANNOT_SUSPEND_SELF,
                    "관리자는 자신의 계정을 정지할 수 없습니다.");
        }

        AdminAccountRepository.UserSummary current = repository.findUser(userId)
                .orElseThrow(AdminAccountService::userNotFound);
        if (current.status() == UserStatus.WITHDRAWN) {
            throw validation("탈퇴한 계정의 상태는 변경할 수 없습니다.");
        }
        if (targetStatus == UserStatus.ACTIVE
                && penalties.existsByUserIdAndReleasedAtIsNullAndExpiresAtAfter(userId, clock.instant())) {
            throw validation("기간 제재가 남아 있는 계정은 활성화할 수 없습니다.");
        }
        String normalizedReason = null;
        if (targetStatus == UserStatus.SUSPENDED) {
            normalizedReason = reason == null ? "" : reason.trim();
            if (normalizedReason.isEmpty() || normalizedReason.length() > 500) {
                throw validation("정지 사유는 1~500자로 입력해야 합니다.");
            }
        }
        return repository.changeUserStatus(userId, targetStatus, normalizedReason)
                .orElseThrow(AdminAccountService::userNotFound);
    }

    public AdminAccountRepository.UserSummary changeUserStatus(long adminId, long userId, UserStatus targetStatus) {
        return changeUserStatus(adminId, userId, targetStatus,
                targetStatus == UserStatus.SUSPENDED ? "관리자 수동 정지" : null);
    }

    @Transactional(readOnly = true)
    public AdminAccountRepository.PageResult<AdminAccountRepository.SellerSummary> searchSellerApplications(
            SellerVerificationStatus status, int page, int size) {
        validatePage(page, size);
        return repository.searchSellerApplications(status, page, size);
    }

    @Transactional
    public AdminAccountRepository.SellerSummary reviewSellerApplication(
            long adminId,
            long sellerId,
            SellerVerificationStatus targetStatus,
            String rejectionReason) {
        if (targetStatus != SellerVerificationStatus.APPROVED
                && targetStatus != SellerVerificationStatus.REJECTED) {
            throw validation("판매자 심사 상태는 APPROVED 또는 REJECTED만 지정할 수 있습니다.");
        }

        AdminAccountRepository.SellerSummary current = repository.findSellerApplication(sellerId)
                .orElseThrow(AdminAccountService::sellerNotFound);
        if (current.status() != SellerVerificationStatus.PENDING) {
            throw new AccountApplicationException(
                    AccountErrorCode.SELLER_APPLICATION_ALREADY_REVIEWED,
                    "이미 처리된 판매자 신청입니다.");
        }

        String normalizedReason = normalizeReason(targetStatus, rejectionReason);
        try {
            return repository.reviewSellerApplication(
                            sellerId, adminId, targetStatus, normalizedReason, clock.instant())
                    .orElseThrow(AdminAccountService::sellerNotFound);
        } catch (OptimisticLockingFailureException exception) {
            throw new AccountApplicationException(
                    AccountErrorCode.SELLER_APPLICATION_ALREADY_REVIEWED,
                    "이미 처리된 판매자 신청입니다.");
        }
    }

    private static String normalizeReason(
            SellerVerificationStatus targetStatus, String rejectionReason) {
        if (targetStatus == SellerVerificationStatus.APPROVED) {
            return null;
        }
        String reason = rejectionReason == null ? "" : rejectionReason.trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw validation("판매자 신청 거부 사유는 1~500자로 입력해야 합니다.");
        }
        return reason;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw validation("page는 0 이상, size는 1~100이어야 합니다.");
        }
    }

    private static AccountApplicationException validation(String message) {
        return new AccountApplicationException(AccountErrorCode.VALIDATION_FAILED, message);
    }

    private static AccountApplicationException userNotFound() {
        return new AccountApplicationException(AccountErrorCode.USER_NOT_FOUND, "회원을 찾을 수 없습니다.");
    }

    private static AccountApplicationException sellerNotFound() {
        return new AccountApplicationException(
                AccountErrorCode.SELLER_APPLICATION_NOT_FOUND,
                "판매자 신청을 찾을 수 없습니다.");
    }

    public record DashboardResult(long totalUsers, long pendingSellerApplications) {
    }
}
