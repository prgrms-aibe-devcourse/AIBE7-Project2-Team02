package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.entity.IssuedAccessToken;
import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.enums.UserRole;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.security.AccessTokenIssuer;
import org.example.matcheat.domain.account.repository.DuplicateUserEmailException;
import org.example.matcheat.domain.account.security.PasswordHasher;
import org.example.matcheat.domain.account.repository.UserCredentialRepository;
import org.example.matcheat.domain.account.repository.AccountPenaltyRepository;
import org.example.matcheat.domain.account.dto.AccountReportCreateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountAuthService {
    private final UserCredentialRepository repository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer tokenIssuer;
    private final AccountPenaltyRepository penalties;
    private final AccountReportService reports;
    private final Clock clock;

    @Autowired
    public AccountAuthService(
            UserCredentialRepository repository,
            PasswordHasher passwordHasher,
            AccessTokenIssuer tokenIssuer,
            AccountPenaltyRepository penalties,
            AccountReportService reports,
            Clock accountClock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.penalties = penalties;
        this.reports = reports;
        this.clock = accountClock;
    }

    AccountAuthService(
            UserCredentialRepository repository,
            PasswordHasher passwordHasher,
            AccessTokenIssuer tokenIssuer) {
        this(repository, passwordHasher, tokenIssuer, null, null, Clock.systemUTC());
    }

    public SuspensionResult suspensionStatus(String rawEmail, String password) {
        UserAccount account = requireCredentials(rawEmail, password);
        if (account.status() != UserStatus.SUSPENDED) {
            throw new AccountApplicationException(AccountErrorCode.VALIDATION_FAILED, "Account is not suspended.");
        }
        Instant now = clock.instant();
        return penalties.findFirstByUserIdAndReleasedAtIsNullAndExpiresAtAfterOrderByExpiresAtDesc(account.id(), now)
                .map(penalty -> new SuspensionResult(penalty.getReason(), penalty.getExpiresAt(), false))
                .orElseGet(() -> new SuspensionResult(
                        repository.findManualSuspensionReason(account.id()).orElse("관리자 수동 정지"), null, true));
    }

    @Transactional
    public void submitSuspensionAppeal(String rawEmail, String password, String message) {
        UserAccount account = requireCredentials(rawEmail, password);
        if (account.status() != UserStatus.SUSPENDED) {
            throw new AccountApplicationException(AccountErrorCode.VALIDATION_FAILED, "Account is not suspended.");
        }
        reports.create(account.id(), new AccountReportCreateRequest(
                "Suspension appeal", message, null, null));
    }

    private UserAccount requireCredentials(String rawEmail, String password) {
        UserAccount account = repository.findByEmail(EmailNormalizer.normalize(rawEmail))
                .orElseThrow(AccountAuthService::invalidCredentials);
        if (account.passwordHash() == null || !passwordHasher.matches(password, account.passwordHash())) {
            throw invalidCredentials();
        }
        return account;
    }

    @Transactional
    public SignUpResult signUp(String rawEmail, String password, String passwordConfirm, String rawName) {
        String email = EmailNormalizer.normalize(rawEmail);
        String name = NameNormalizer.normalize(rawName);
        PasswordPolicy.validate(password);
        if (!password.equals(passwordConfirm)) {
            throw new AccountApplicationException(
                    AccountErrorCode.PASSWORD_CONFIRM_MISMATCH,
                    "비밀번호 확인이 일치하지 않습니다.");
        }
        if (repository.existsByEmail(email)) {
            throw emailAlreadyExists();
        }

        UserAccount account = UserAccount.registerUser(email, passwordHasher.hash(password), name);
        try {
            UserAccount saved = repository.save(account);
            return new SignUpResult(saved.id(), saved.email(), saved.name(), saved.role(), saved.status());
        } catch (DuplicateUserEmailException exception) {
            throw emailAlreadyExists();
        }
    }

    public LoginResult login(String rawEmail, String password) {
        String email = EmailNormalizer.normalize(rawEmail);
        UserAccount account = repository.findByEmail(email)
                .orElseThrow(AccountAuthService::invalidCredentials);

        if (account.passwordHash() == null || !passwordHasher.matches(password, account.passwordHash())) {
            throw invalidCredentials();
        }
        ensureActive(account.status());

        IssuedAccessToken token = tokenIssuer.issue(account);
        return new LoginResult(
                token.value(),
                "Bearer",
                token.expiresInSeconds(),
                new UserSummary(account.id(), account.email(), account.name(), account.role()));
    }

    public EmailAvailability checkEmailAvailability(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        return new EmailAvailability(email, !repository.existsByEmail(email));
    }

    private static void ensureActive(UserStatus status) {
        if (status == UserStatus.SUSPENDED) {
            throw new AccountApplicationException(AccountErrorCode.ACCOUNT_SUSPENDED, "정지된 계정입니다.");
        }
        if (status == UserStatus.WITHDRAWN) {
            throw new AccountApplicationException(AccountErrorCode.ACCOUNT_WITHDRAWN, "탈퇴한 계정입니다.");
        }
    }

    private static AccountApplicationException invalidCredentials() {
        return new AccountApplicationException(
                AccountErrorCode.INVALID_CREDENTIALS,
                "이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private static AccountApplicationException emailAlreadyExists() {
        return new AccountApplicationException(
                AccountErrorCode.EMAIL_ALREADY_EXISTS,
                "이미 사용 중인 이메일입니다.");
    }

    public record SignUpResult(Long userId, String email, String name, UserRole role, UserStatus status) {
    }

    public record LoginResult(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserSummary user) {
    }

    public record UserSummary(Long userId, String email, String name, UserRole role) {
    }

    public record EmailAvailability(String email, boolean available) {
    }

    public record SuspensionResult(String reason, Instant expiresAt, boolean indefinite) {
    }
}
