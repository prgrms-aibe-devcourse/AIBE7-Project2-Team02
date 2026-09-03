package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.dto.AccountReportCreateRequest;
import org.example.matcheat.domain.account.entity.AccountReportEntity;
import org.example.matcheat.domain.account.entity.AccountReportHistoryEntity;
import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.enums.AccountReportTargetType;
import org.example.matcheat.domain.account.enums.UserRole;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.AccountReportRepository;
import org.example.matcheat.domain.account.repository.AccountReportHistoryRepository;
import org.example.matcheat.domain.account.repository.UserCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-02T03:00:00Z");

    private AccountReportRepository reports;
    private AccountReportHistoryRepository histories;
    private UserCredentialRepository users;
    private AccountReportTargetResolver targetResolver;
    private AccountReportSnapshotService snapshotService;
    private AccountReportService service;

    @BeforeEach
    void setUp() {
        reports = mock(AccountReportRepository.class);
        histories = mock(AccountReportHistoryRepository.class);
        users = mock(UserCredentialRepository.class);
        targetResolver = mock(AccountReportTargetResolver.class);
        snapshotService = mock(AccountReportSnapshotService.class);
        service = new AccountReportService(
                reports, histories, users, targetResolver, snapshotService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsReportForAuthenticatedUserAndNormalizesMessage() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7L)));
        when(reports.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(7L, new AccountReportCreateRequest(
                "  거래 문의  ", "  확인해 주세요.  ", null, null));

        ArgumentCaptor<AccountReportEntity> captor = ArgumentCaptor.forClass(AccountReportEntity.class);
        verify(reports).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReporterId()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("거래 문의");
        assertThat(response.message()).isEqualTo("확인해 주세요.");
        assertThat(response.status()).isEqualTo(AccountReportStatus.PENDING);
        verify(histories).save(any());
    }

    @Test
    void storesOptionalReportTarget() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7L)));
        when(reports.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(targetResolver.resolveReportedUser(7L, AccountReportTargetType.CHAT_ROOM, 12L)).thenReturn(9L);
        when(users.findById(9L)).thenReturn(Optional.of(user(9L)));

        var response = service.create(7L, new AccountReportCreateRequest(
                "채팅 신고", "확인해 주세요.", AccountReportTargetType.CHAT_ROOM, 12L));

        assertThat(response.targetType()).isEqualTo(AccountReportTargetType.CHAT_ROOM);
        assertThat(response.targetId()).isEqualTo(12L);
        ArgumentCaptor<AccountReportEntity> captor = ArgumentCaptor.forClass(AccountReportEntity.class);
        verify(reports).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReportedUserId()).isEqualTo(9L);
    }

    @Test
    void rejectsIncompleteReportTarget() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7L)));

        assertThatThrownBy(() -> service.create(7L, new AccountReportCreateRequest(
                "채팅 신고", "확인해 주세요.", AccountReportTargetType.CHAT_ROOM, null)))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.VALIDATION_FAILED));
    }

    @Test
    void rejectsDuplicateOpenReportForSameTarget() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7L)));
        when(reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(AccountReportTargetType.CHAT_ROOM),
                org.mockito.ArgumentMatchers.eq(12L), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(7L, new AccountReportCreateRequest(
                "Chat report", "Already submitted", AccountReportTargetType.CHAT_ROOM, 12L)))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.REPORT_ALREADY_EXISTS));
    }

    @Test
    void rateLimitsExcessiveReports() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7L)));
        when(reports.countByReporterIdAndCreatedAtAfter(7L, NOW.minusSeconds(3600))).thenReturn(10L);

        assertThatThrownBy(() -> service.create(7L, new AccountReportCreateRequest(
                "report", "message", null, null)))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.REPORT_RATE_LIMITED));
    }

    @Test
    void requiresAdminResponseWhenClosingReport() {
        AccountReportEntity report = AccountReportEntity.create(7L, null, "신고", "내용", null, null, NOW);
        when(reports.findById(3L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.review(1L, 3L, AccountReportStatus.RESOLVED, " "))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.INVALID_REPORT_STATUS));
    }

    @Test
    void preventsChangingTerminalReport() {
        AccountReportEntity report = AccountReportEntity.create(7L, null, "신고", "내용", null, null, NOW);
        report.review(AccountReportStatus.RESOLVED, "처리했습니다.", 1L, NOW);
        when(reports.findById(3L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.review(1L, 3L, AccountReportStatus.IN_REVIEW, null))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.INVALID_REPORT_STATUS));
    }

    @Test
    void recordsEveryAdminStatusChange() {
        AccountReportEntity report = AccountReportEntity.create(7L, 9L, "신고", "내용", null, null, NOW);
        ReflectionTestUtils.setField(report, "id", 3L);
        when(reports.findById(3L)).thenReturn(Optional.of(report));
        when(users.findById(7L)).thenReturn(Optional.of(user(7L)));

        service.review(1L, 3L, AccountReportStatus.IN_REVIEW, null);

        ArgumentCaptor<AccountReportHistoryEntity> captor =
                ArgumentCaptor.forClass(AccountReportHistoryEntity.class);
        verify(histories).save(captor.capture());
        assertThat(captor.getValue().getReportId()).isEqualTo(3L);
        assertThat(captor.getValue().getPreviousStatus()).isEqualTo(AccountReportStatus.PENDING);
        assertThat(captor.getValue().getNewStatus()).isEqualTo(AccountReportStatus.IN_REVIEW);
        assertThat(captor.getValue().getActorId()).isEqualTo(1L);
        assertThat(captor.getValue().getChangedAt()).isEqualTo(NOW);
    }

    @Test
    void returnsNotFoundForMissingReport() {
        when(reports.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(1L, 99L, AccountReportStatus.IN_REVIEW, null))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.REPORT_NOT_FOUND));
    }

    private static UserAccount user(long id) {
        return UserAccount.restore(
                id, "user@example.com", "hash", "사용자", UserRole.USER, UserStatus.ACTIVE, 0, null);
    }
}
