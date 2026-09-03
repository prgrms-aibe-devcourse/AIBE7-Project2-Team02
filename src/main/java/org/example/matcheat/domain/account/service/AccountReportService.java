package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.dto.AccountReportCreateRequest;
import org.example.matcheat.domain.account.dto.AccountReportResponse;
import org.example.matcheat.domain.account.dto.AccountReportHistoryResponse;
import org.example.matcheat.domain.account.dto.AccountReportResultResponse;
import org.example.matcheat.domain.account.dto.AdminAccountReportResponse;
import org.example.matcheat.domain.account.dto.AdminPageResponse;
import org.example.matcheat.domain.account.entity.AccountReportEntity;
import org.example.matcheat.domain.account.entity.AccountReportHistoryEntity;
import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.repository.AccountReportRepository;
import org.example.matcheat.domain.account.repository.AccountReportHistoryRepository;
import org.example.matcheat.domain.account.repository.UserCredentialRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AccountReportService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REPORTS_PER_HOUR = 10;
    private static final List<AccountReportStatus> OPEN_STATUSES =
            List.of(AccountReportStatus.PENDING, AccountReportStatus.IN_REVIEW);

    private final AccountReportRepository reports;
    private final AccountReportHistoryRepository histories;
    private final UserCredentialRepository users;
    private final AccountReportTargetResolver targetResolver;
    private final AccountReportSnapshotService snapshotService;
    private final Clock clock;

    public AccountReportService(
            AccountReportRepository reports,
            AccountReportHistoryRepository histories,
            UserCredentialRepository users,
            AccountReportTargetResolver targetResolver,
            AccountReportSnapshotService snapshotService,
            Clock accountClock) {
        this.reports = reports;
        this.histories = histories;
        this.users = users;
        this.targetResolver = targetResolver;
        this.snapshotService = snapshotService;
        this.clock = accountClock;
    }

    @Transactional
    public AccountReportResponse create(long reporterId, AccountReportCreateRequest request) {
        requireUser(reporterId);
        Instant now = clock.instant();
        if (reports.countByReporterIdAndCreatedAtAfter(reporterId, now.minusSeconds(3600)) >= MAX_REPORTS_PER_HOUR) {
            throw new AccountApplicationException(
                    AccountErrorCode.REPORT_RATE_LIMITED, "신고 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        if ((request.targetType() == null) != (request.targetId() == null)) {
            throw new AccountApplicationException(
                    AccountErrorCode.VALIDATION_FAILED, "신고 대상 유형과 ID를 함께 입력해 주세요.");
        }
        if (request.targetType() != null && reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                reporterId, request.targetType(), request.targetId(), OPEN_STATUSES)) {
            throw new AccountApplicationException(
                    AccountErrorCode.REPORT_ALREADY_EXISTS, "이미 신고된 사항입니다.");
        }
        Long reportedUserId = request.targetType() == null
                ? null
                : targetResolver.resolveReportedUser(reporterId, request.targetType(), request.targetId());
        if (reportedUserId != null) {
            requireUser(reportedUserId);
        }
        AccountReportEntity report = AccountReportEntity.create(
                reporterId, reportedUserId, request.title().trim(), request.message().trim(),
                request.targetType(), request.targetId(), now);
        if (request.targetType() != null) {
            report.captureSnapshot(snapshotService.capture(request.targetType(), request.targetId()));
        }
        AccountReportEntity saved;
        try {
            saved = reports.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            throw new AccountApplicationException(
                    AccountErrorCode.REPORT_ALREADY_EXISTS, "이미 신고된 사항입니다.");
        }
        histories.save(AccountReportHistoryEntity.record(
                saved.getId(), null, AccountReportStatus.PENDING, reporterId, null, now));
        return AccountReportResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AccountReportResultResponse> mine(long reporterId, int page, int size) {
        Page<AccountReportEntity> result = reports.findByReporterId(reporterId, pageRequest(page, size));
        return pageResponse(result.map(AccountReportResultResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminAccountReportResponse> search(
            AccountReportStatus status, int page, int size) {
        Page<AccountReportEntity> result = reports.search(status, pageRequest(page, size));
        Page<AdminAccountReportResponse> responses = result.map(report ->
                AdminAccountReportResponse.from(report, requireUser(report.getReporterId())));
        return pageResponse(responses);
    }

    @Transactional
    public AdminAccountReportResponse review(
            long adminId, long reportId, AccountReportStatus status, String adminResponse) {
        if (status == null) {
            throw new AccountApplicationException(
                    AccountErrorCode.INVALID_REPORT_STATUS, "처리 상태를 선택해 주세요.");
        }
        AccountReportEntity report = reports.findById(reportId)
                .orElseThrow(() -> new AccountApplicationException(
                        AccountErrorCode.REPORT_NOT_FOUND, "신고를 찾을 수 없습니다."));
        String normalizedResponse = normalize(adminResponse);
        if ((status == AccountReportStatus.RESOLVED || status == AccountReportStatus.REJECTED)
                && normalizedResponse == null) {
            throw new AccountApplicationException(
                    AccountErrorCode.INVALID_REPORT_STATUS, "처리 완료 또는 반려 시 관리자 답변이 필요합니다.");
        }
        AccountReportStatus previousStatus = report.getStatus();
        Instant now = clock.instant();
        try {
            report.review(status, normalizedResponse, adminId, now);
            reports.flush();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new AccountApplicationException(AccountErrorCode.INVALID_REPORT_STATUS, exception.getMessage());
        } catch (OptimisticLockingFailureException exception) {
            throw new AccountApplicationException(
                    AccountErrorCode.REPORT_ALREADY_REVIEWED, "다른 관리자가 이미 신고를 처리했습니다.");
        }
        histories.save(AccountReportHistoryEntity.record(
                report.getId(), previousStatus, status, adminId, normalizedResponse, now));
        return AdminAccountReportResponse.from(report, requireUser(report.getReporterId()));
    }

    @Transactional(readOnly = true)
    public List<AccountReportHistoryResponse> history(long reportId) {
        if (!reports.existsById(reportId)) {
            throw new AccountApplicationException(AccountErrorCode.REPORT_NOT_FOUND, "신고를 찾을 수 없습니다.");
        }
        return histories.findByReportIdOrderByChangedAtAscIdAsc(reportId).stream()
                .map(AccountReportHistoryResponse::from)
                .toList();
    }

    private UserAccount requireUser(long userId) {
        return users.findById(userId).orElseThrow(() -> new AccountApplicationException(
                AccountErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private static PageRequest pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private static <T> AdminPageResponse<T> pageResponse(Page<T> page) {
        return new AdminPageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
