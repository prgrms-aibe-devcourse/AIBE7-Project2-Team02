package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.entity.AccountPenaltyEntity;
import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.enums.UserRole;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.AccountPenaltyRepository;
import org.example.matcheat.domain.account.repository.UserCredentialRepository;
import org.example.matcheat.domain.account.security.AccessTokenIssuer;
import org.example.matcheat.domain.account.security.PasswordHasher;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSecurityFlowTest {
    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    void returnsTimedPenaltyReasonAndExpiryAfterCredentialVerification() {
        Fixture fixture = new Fixture();
        var penalty = AccountPenaltyEntity.issue(3L, 7L, "abusive messages", 1L,
                NOW.minusSeconds(60), NOW.plusSeconds(86400));
        when(fixture.penalties.findFirstByUserIdAndReleasedAtIsNullAndExpiresAtAfterOrderByExpiresAtDesc(7L, NOW))
                .thenReturn(Optional.of(penalty));

        var result = fixture.service.suspensionStatus("USER@example.com", "password1234");

        assertThat(result.reason()).isEqualTo("abusive messages");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(86400));
        assertThat(result.indefinite()).isFalse();
    }

    @Test
    void submitsVerifiedSuspensionAppealToExistingReportInbox() {
        Fixture fixture = new Fixture();

        fixture.service.submitSuspensionAppeal("user@example.com", "password1234", "Please review this decision.");

        verify(fixture.reports).create(org.mockito.ArgumentMatchers.eq(7L), argThat(request ->
                request.title().equals("Suspension appeal")
                        && request.message().equals("Please review this decision.")));
    }

    private static final class Fixture {
        private final UserCredentialRepository users = mock(UserCredentialRepository.class);
        private final PasswordHasher passwords = mock(PasswordHasher.class);
        private final AccountPenaltyRepository penalties = mock(AccountPenaltyRepository.class);
        private final AccountReportService reports = mock(AccountReportService.class);
        private final AccountAuthService service = new AccountAuthService(
                users, passwords, mock(AccessTokenIssuer.class), penalties, reports,
                Clock.fixed(NOW, ZoneOffset.UTC));

        private Fixture() {
            UserAccount account = UserAccount.restore(7L, "user@example.com", "hash", "user",
                    UserRole.USER, UserStatus.SUSPENDED, 1, null);
            when(users.findByEmail("user@example.com")).thenReturn(Optional.of(account));
            when(passwords.matches("password1234", "hash")).thenReturn(true);
        }
    }
}
