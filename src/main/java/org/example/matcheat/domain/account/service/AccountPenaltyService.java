package org.example.matcheat.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.account.dto.AccountPenaltyResponse;
import org.example.matcheat.domain.account.entity.AccountPenaltyEntity;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccountPenaltyService {
    private static final Set<Integer> ALLOWED_DAYS = Set.of(1, 3, 7, 15, 30);
    private final AccountPenaltyRepository penalties;
    private final AccountReportRepository reports;
    private final AdminAccountRepository users;
    private final Clock accountClock;

    @Transactional
    public AccountPenaltyResponse issue(long adminId, long reportId, int days, String reason) {
        if (!ALLOWED_DAYS.contains(days)) throw validation("제재 기간은 1, 3, 7, 15, 30일 중 하나여야 합니다.");
        var report = reports.findById(reportId).orElseThrow(() ->
                new AccountApplicationException(AccountErrorCode.REPORT_NOT_FOUND, "신고를 찾을 수 없습니다."));
        if (report.getStatus() != AccountReportStatus.RESOLVED || report.getReportedUserId() == null) {
            throw validation("처리 완료된 대상 지정 신고에만 제재를 부과할 수 있습니다.");
        }
        if (penalties.existsByReportId(reportId)) throw validation("이 신고에는 이미 제재가 부과되었습니다.");
        if (report.getReportedUserId() == adminId) throw validation("관리자는 자신을 제재할 수 없습니다.");
        var user = users.findUser(report.getReportedUserId()).orElseThrow(() ->
                new AccountApplicationException(AccountErrorCode.USER_NOT_FOUND, "회원을 찾을 수 없습니다."));
        if (user.status() == UserStatus.WITHDRAWN) throw validation("탈퇴한 계정은 제재할 수 없습니다.");
        var now = accountClock.instant();
        users.suspendForPenalty(userId(user)).orElseThrow();
        AccountPenaltyEntity saved;
        try {
            saved = penalties.saveAndFlush(AccountPenaltyEntity.issue(reportId, userId(user), reason.trim(), adminId,
                    now, now.plusSeconds(days * 86400L)));
        } catch (DataIntegrityViolationException exception) {
            throw new AccountApplicationException(
                    AccountErrorCode.PENALTY_ALREADY_EXISTS, "이 신고에는 이미 제재가 부과되었습니다.");
        }
        return AccountPenaltyResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountPenaltyResponse> history(long userId) {
        return penalties.findByUserIdOrderByIssuedAtDesc(userId).stream().map(AccountPenaltyResponse::from).toList();
    }

    @Scheduled(fixedDelayString = "${app.account.penalty-release-interval-ms:60000}")
    @Transactional
    public void releaseExpired() {
        var now = accountClock.instant();
        for (var penalty : penalties.findByReleasedAtIsNullAndExpiresAtLessThanEqual(now)) {
            penalty.release(now);
            if (!penalties.existsByUserIdAndReleasedAtIsNullAndExpiresAtAfter(penalty.getUserId(), now)) {
                users.activateAfterPenalty(penalty.getUserId());
            }
        }
    }

    private static AccountApplicationException validation(String message) {
        return new AccountApplicationException(AccountErrorCode.VALIDATION_FAILED, message);
    }

    private static long userId(AdminAccountRepository.UserSummary user) {
        return user.userId();
    }
}
