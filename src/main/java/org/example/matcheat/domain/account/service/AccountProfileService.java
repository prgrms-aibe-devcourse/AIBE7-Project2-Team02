package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.enums.UserRole;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.SellerApplicationRepository;
import org.example.matcheat.domain.account.repository.UserCredentialRepository;
import org.example.matcheat.domain.account.security.PasswordHasher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional(readOnly = true)
public class AccountProfileService {
    private final UserCredentialRepository users;
    private final SellerApplicationRepository sellerApplications;
    private final PasswordHasher passwordHasher;
    private final AccountTradeActivityPort tradeActivity;
    private final Clock clock;

    public AccountProfileService(
            UserCredentialRepository users,
            SellerApplicationRepository sellerApplications,
            PasswordHasher passwordHasher,
            AccountTradeActivityPort tradeActivity,
            @Qualifier("accountClock") Clock clock) {
        this.users = users;
        this.sellerApplications = sellerApplications;
        this.passwordHasher = passwordHasher;
        this.tradeActivity = tradeActivity;
        this.clock = clock;
    }

    public ProfileResult getCurrentUser(long userId) {
        return toResult(requireActiveUser(userId));
    }

    @Transactional
    public ProfileResult updateName(long userId, String rawName) {
        requireActiveUser(userId);
        UserAccount updated = users.updateName(userId, NameNormalizer.normalize(rawName))
                .orElseThrow(AccountProfileService::userNotFound);
        return toResult(updated);
    }

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword, String confirmation) {
        UserAccount account = requireActiveUser(userId);
        if (currentPassword == null || account.passwordHash() == null
                || !passwordHasher.matches(currentPassword, account.passwordHash())) {
            throw new AccountApplicationException(
                    AccountErrorCode.CURRENT_PASSWORD_MISMATCH, "Current password does not match.");
        }
        PasswordPolicy.validate(newPassword);
        if (!newPassword.equals(confirmation)) {
            throw new AccountApplicationException(
                    AccountErrorCode.PASSWORD_CONFIRM_MISMATCH, "Password confirmation does not match.");
        }
        if (passwordHasher.matches(newPassword, account.passwordHash())) {
            throw new AccountApplicationException(
                    AccountErrorCode.VALIDATION_FAILED, "New password must differ from the current password.");
        }
        users.updatePassword(userId, passwordHasher.hash(newPassword))
                .orElseThrow(AccountProfileService::userNotFound);
    }

    @Transactional
    public void withdraw(long userId, String currentPassword) {
        UserAccount account = requireActiveUser(userId);
        if (account.passwordHash() == null
                || currentPassword == null
                || !passwordHasher.matches(currentPassword, account.passwordHash())) {
            throw new AccountApplicationException(
                    AccountErrorCode.CURRENT_PASSWORD_MISMATCH,
                    "현재 비밀번호가 일치하지 않습니다.");
        }
        if (tradeActivity.hasActiveTrade(userId)) {
            throw new AccountApplicationException(
                    AccountErrorCode.ACTIVE_TRANSACTION_EXISTS,
                    "진행 중인 거래가 있어 회원 탈퇴를 할 수 없습니다.");
        }
        users.withdraw(userId, clock.instant()).orElseThrow(AccountProfileService::userNotFound);
    }

    private ProfileResult toResult(UserAccount account) {
        SellerVerificationStatus sellerStatus = sellerApplications.findStatusByUserId(account.id()).orElse(null);
        return new ProfileResult(
                account.id(),
                account.email(),
                account.name(),
                account.role(),
                account.status(),
                sellerStatus);
    }

    private UserAccount requireActiveUser(long userId) {
        UserAccount account = users.findById(userId).orElseThrow(AccountProfileService::userNotFound);
        if (account.status() == UserStatus.SUSPENDED) {
            throw new AccountApplicationException(AccountErrorCode.ACCOUNT_SUSPENDED, "정지된 계정입니다.");
        }
        if (account.status() == UserStatus.WITHDRAWN) {
            throw new AccountApplicationException(AccountErrorCode.ACCOUNT_WITHDRAWN, "탈퇴한 계정입니다.");
        }
        return account;
    }

    private static AccountApplicationException userNotFound() {
        return new AccountApplicationException(AccountErrorCode.USER_NOT_FOUND, "회원을 찾을 수 없습니다.");
    }

    public record ProfileResult(
            Long userId,
            String email,
            String name,
            UserRole role,
            UserStatus status,
            SellerVerificationStatus sellerStatus) {
    }
}
