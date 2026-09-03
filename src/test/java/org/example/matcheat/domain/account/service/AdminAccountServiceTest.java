package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.enums.UserRole;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.AdminAccountRepository;
import org.example.matcheat.domain.account.repository.AccountPenaltyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminAccountServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    private AdminAccountRepository repository;
    private AdminAccountService service;
    private AccountPenaltyRepository penalties;

    @BeforeEach
    void setUp() {
        repository = mock(AdminAccountRepository.class);
        penalties = mock(AccountPenaltyRepository.class);
        service = new AdminAccountService(repository, penalties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsDashboardCounts() {
        when(repository.countUsers()).thenReturn(17L);
        when(repository.countPendingSellerApplications()).thenReturn(3L);

        assertThat(service.dashboard()).isEqualTo(new AdminAccountService.DashboardResult(17, 3));
    }

    @Test
    void normalizesUserSearchAndValidatesPaging() {
        AdminAccountRepository.PageResult<AdminAccountRepository.UserSummary> empty =
                new AdminAccountRepository.PageResult<>(List.of(), 0, 20, 0, 0);
        when(repository.searchUsers("kim", UserStatus.ACTIVE, 0, 20)).thenReturn(empty);

        assertThat(service.searchUsers("  kim  ", UserStatus.ACTIVE, 0, 20)).isEqualTo(empty);
        assertThatThrownBy(() -> service.searchUsers("", null, -1, 20))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.VALIDATION_FAILED));
    }

    @Test
    void preventsAdminFromSuspendingSelf() {
        assertThatThrownBy(() -> service.changeUserStatus(7L, 7L, UserStatus.SUSPENDED))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.CANNOT_SUSPEND_SELF));
        verifyNoInteractions(repository);
    }

    @Test
    void preventsChangingWithdrawnAccount() {
        when(repository.findUser(8L)).thenReturn(Optional.of(user(8L, UserStatus.WITHDRAWN)));

        assertThatThrownBy(() -> service.changeUserStatus(7L, 8L, UserStatus.ACTIVE))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.VALIDATION_FAILED));
    }

    @Test
    void changesMemberStatus() {
        AdminAccountRepository.UserSummary active = user(8L, UserStatus.ACTIVE);
        AdminAccountRepository.UserSummary suspended = user(8L, UserStatus.SUSPENDED);
        when(repository.findUser(8L)).thenReturn(Optional.of(active));
        when(repository.changeUserStatus(8L, UserStatus.SUSPENDED, "policy violation"))
                .thenReturn(Optional.of(suspended));

        assertThat(service.changeUserStatus(7L, 8L, UserStatus.SUSPENDED, "policy violation"))
                .isEqualTo(suspended);
    }

    @Test
    void preventsActivationWhileTimedPenaltyRemains() {
        when(repository.findUser(8L)).thenReturn(Optional.of(user(8L, UserStatus.SUSPENDED)));
        when(penalties.existsByUserIdAndReleasedAtIsNullAndExpiresAtAfter(8L, NOW)).thenReturn(true);

        assertThatThrownBy(() -> service.changeUserStatus(7L, 8L, UserStatus.ACTIVE))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.VALIDATION_FAILED));
    }

    @Test
    void requiresRejectionReasonAndRejectsDuplicateReview() {
        when(repository.findSellerApplication(11L)).thenReturn(Optional.of(seller(SellerVerificationStatus.PENDING)));

        assertThatThrownBy(() -> service.reviewSellerApplication(
                7L, 11L, SellerVerificationStatus.REJECTED, "  "))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.VALIDATION_FAILED));

        when(repository.findSellerApplication(11L)).thenReturn(Optional.of(seller(SellerVerificationStatus.APPROVED)));
        assertThatThrownBy(() -> service.reviewSellerApplication(
                7L, 11L, SellerVerificationStatus.REJECTED, "정보 불일치"))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(AccountErrorCode.SELLER_APPLICATION_ALREADY_REVIEWED));
    }

    @Test
    void reviewsPendingSellerAtServiceClock() {
        AdminAccountRepository.SellerSummary pending = seller(SellerVerificationStatus.PENDING);
        AdminAccountRepository.SellerSummary approved = seller(SellerVerificationStatus.APPROVED);
        when(repository.findSellerApplication(11L)).thenReturn(Optional.of(pending));
        when(repository.reviewSellerApplication(
                11L, 7L, SellerVerificationStatus.APPROVED, null, NOW))
                .thenReturn(Optional.of(approved));

        assertThat(service.reviewSellerApplication(
                7L, 11L, SellerVerificationStatus.APPROVED, "ignored")).isEqualTo(approved);
        verify(repository).reviewSellerApplication(
                11L, 7L, SellerVerificationStatus.APPROVED, null, NOW);
    }

    @Test
    void mapsConcurrentSellerReviewToConflict() {
        when(repository.findSellerApplication(11L)).thenReturn(Optional.of(seller(SellerVerificationStatus.PENDING)));
        when(repository.reviewSellerApplication(
                11L, 7L, SellerVerificationStatus.APPROVED, null, NOW))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> service.reviewSellerApplication(
                7L, 11L, SellerVerificationStatus.APPROVED, null))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(AccountErrorCode.SELLER_APPLICATION_ALREADY_REVIEWED));
    }

    private static AdminAccountRepository.UserSummary user(Long id, UserStatus status) {
        return new AdminAccountRepository.UserSummary(
                id, "user@example.com", "사용자", UserRole.USER, status, 0, NOW);
    }

    private static AdminAccountRepository.SellerSummary seller(SellerVerificationStatus status) {
        return new AdminAccountRepository.SellerSummary(
                11L, 8L, "user@example.com", "사용자", "매치잇 상회", "1234567890",
                null, null, null, status, null, NOW, null);
    }
}
