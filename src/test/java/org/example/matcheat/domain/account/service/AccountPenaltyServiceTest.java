package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.entity.AccountReportEntity;
import org.example.matcheat.domain.account.entity.AccountPenaltyEntity;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountPenaltyServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-02T03:00:00Z");
    private AccountPenaltyRepository penalties;
    private AccountReportRepository reports;
    private AdminAccountRepository users;
    private AccountPenaltyService service;

    @BeforeEach
    void setUp() {
        penalties = mock(AccountPenaltyRepository.class);
        reports = mock(AccountReportRepository.class);
        users = mock(AdminAccountRepository.class);
        service = new AccountPenaltyService(penalties, reports, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void suspendsReportedUserForAllowedPeriod() {
        var report = AccountReportEntity.create(7L, 9L, "report", "message", null, null, NOW);
        report.review(AccountReportStatus.RESOLVED, "confirmed", 1L, NOW);
        ReflectionTestUtils.setField(report, "id", 3L);
        var user = mock(AdminAccountRepository.UserSummary.class);
        when(user.userId()).thenReturn(9L);
        when(user.status()).thenReturn(UserStatus.ACTIVE);
        when(reports.findById(3L)).thenReturn(Optional.of(report));
        when(users.findUser(9L)).thenReturn(Optional.of(user));
        when(users.suspendForPenalty(9L)).thenReturn(Optional.of(user));
        when(penalties.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.issue(1L, 3L, 7, "policy violation");

        verify(users).suspendForPenalty(9L);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void releasesExpiredPenaltyWhenNoOtherPenaltyRemains() {
        var penalty = AccountPenaltyEntity.issue(3L, 9L, "reason", 1L, NOW.minusSeconds(90000), NOW.minusSeconds(1));
        var user = mock(AdminAccountRepository.UserSummary.class);
        when(user.userId()).thenReturn(9L);
        when(user.status()).thenReturn(UserStatus.SUSPENDED);
        when(penalties.findByReleasedAtIsNullAndExpiresAtLessThanEqual(NOW)).thenReturn(java.util.List.of(penalty));
        when(users.findUser(9L)).thenReturn(Optional.of(user));

        service.releaseExpired();

        assertThat(penalty.getReleasedAt()).isEqualTo(NOW);
        verify(users).activateAfterPenalty(9L);
    }
}
