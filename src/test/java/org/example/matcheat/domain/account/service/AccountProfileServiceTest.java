package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.enums.UserRole;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.SellerApplicationRepository;
import org.example.matcheat.domain.account.repository.UserCredentialRepository;
import org.example.matcheat.domain.account.security.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountProfileServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    @Mock
    private UserCredentialRepository users;
    @Mock
    private SellerApplicationRepository sellerApplications;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private AccountTradeActivityPort tradeActivity;

    private AccountProfileService service;

    @BeforeEach
    void setUp() {
        service = new AccountProfileService(
                users,
                sellerApplications,
                passwordHasher,
                tradeActivity,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsCurrentProfileWithSellerStatus() {
        when(users.findById(7L)).thenReturn(Optional.of(activeAccount("홍길동")));
        when(sellerApplications.findStatusByUserId(7L))
                .thenReturn(Optional.of(SellerVerificationStatus.PENDING));

        AccountProfileService.ProfileResult result = service.getCurrentUser(7L);

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.sellerStatus()).isEqualTo(SellerVerificationStatus.PENDING);
    }

    @Test
    void trimsAndUpdatesName() {
        when(users.findById(7L)).thenReturn(Optional.of(activeAccount("홍길동")));
        when(users.updateName(7L, "새 이름")).thenReturn(Optional.of(activeAccount("새 이름")));
        when(sellerApplications.findStatusByUserId(7L)).thenReturn(Optional.empty());

        AccountProfileService.ProfileResult result = service.updateName(7L, "  새 이름  ");

        assertThat(result.name()).isEqualTo("새 이름");
        assertThat(result.sellerStatus()).isNull();
        verify(users).updateName(7L, "새 이름");
    }

    @Test
    void withdrawsAfterPasswordCheckAtServiceClock() {
        UserAccount account = activeAccount("홍길동");
        when(users.findById(7L)).thenReturn(Optional.of(account));
        when(passwordHasher.matches("password1234", account.passwordHash())).thenReturn(true);
        when(users.withdraw(7L, NOW)).thenReturn(Optional.of(UserAccount.restore(
                7L,
                account.email(),
                account.passwordHash(),
                account.name(),
                account.role(),
                UserStatus.WITHDRAWN,
                1,
                NOW)));

        service.withdraw(7L, "password1234");

        verify(users).withdraw(7L, NOW);
    }

    @Test
    void rejectsWrongCurrentPassword() {
        UserAccount account = activeAccount("홍길동");
        when(users.findById(7L)).thenReturn(Optional.of(account));
        when(passwordHasher.matches("wrong-password", account.passwordHash())).thenReturn(false);

        assertThatThrownBy(() -> service.withdraw(7L, "wrong-password"))
                .isInstanceOf(AccountApplicationException.class)
                .extracting(exception -> ((AccountApplicationException) exception).code())
                .isEqualTo(AccountErrorCode.CURRENT_PASSWORD_MISMATCH);
    }

    @Test
    void changesPasswordAndInvalidatesExistingTokens() {
        UserAccount account = activeAccount("user");
        when(users.findById(7L)).thenReturn(Optional.of(account));
        when(passwordHasher.matches("password1234", account.passwordHash())).thenReturn(true);
        when(passwordHasher.matches("newPassword5678", account.passwordHash())).thenReturn(false);
        when(passwordHasher.hash("newPassword5678")).thenReturn("{bcrypt}new-hash");
        when(users.updatePassword(7L, "{bcrypt}new-hash")).thenReturn(Optional.of(account));

        service.changePassword(7L, "password1234", "newPassword5678", "newPassword5678");

        verify(users).updatePassword(7L, "{bcrypt}new-hash");
    }

    @Test
    void rejectsPasswordChangeWithWrongCurrentPassword() {
        UserAccount account = activeAccount("user");
        when(users.findById(7L)).thenReturn(Optional.of(account));
        when(passwordHasher.matches("wrongPassword1", account.passwordHash())).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                7L, "wrongPassword1", "newPassword5678", "newPassword5678"))
                .isInstanceOf(AccountApplicationException.class)
                .extracting(exception -> ((AccountApplicationException) exception).code())
                .isEqualTo(AccountErrorCode.CURRENT_PASSWORD_MISMATCH);
    }

    @Test
    void rejectsWithdrawalWhenAccountHasActiveTrade() {
        UserAccount account = activeAccount("홍길동");
        when(users.findById(7L)).thenReturn(Optional.of(account));
        when(passwordHasher.matches("password1234", account.passwordHash())).thenReturn(true);
        when(tradeActivity.hasActiveTrade(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.withdraw(7L, "password1234"))
                .isInstanceOf(AccountApplicationException.class)
                .extracting(exception -> ((AccountApplicationException) exception).code())
                .isEqualTo(AccountErrorCode.ACTIVE_TRANSACTION_EXISTS);

        verify(users, never()).withdraw(7L, NOW);
    }

    private static UserAccount activeAccount(String name) {
        return UserAccount.restore(
                7L,
                "user@example.com",
                "{bcrypt}hash",
                name,
                UserRole.USER,
                UserStatus.ACTIVE,
                0,
                null);
    }
}
