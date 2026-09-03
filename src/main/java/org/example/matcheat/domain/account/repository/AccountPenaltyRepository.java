package org.example.matcheat.domain.account.repository;

import org.example.matcheat.domain.account.entity.AccountPenaltyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface AccountPenaltyRepository extends JpaRepository<AccountPenaltyEntity, Long> {
    boolean existsByReportId(Long reportId);
    boolean existsByUserIdAndReleasedAtIsNullAndExpiresAtAfter(Long userId, Instant now);
    List<AccountPenaltyEntity> findByReleasedAtIsNullAndExpiresAtLessThanEqual(Instant now);
    List<AccountPenaltyEntity> findByUserIdOrderByIssuedAtDesc(Long userId);
    java.util.Optional<AccountPenaltyEntity> findFirstByUserIdAndReleasedAtIsNullAndExpiresAtAfterOrderByExpiresAtDesc(
            Long userId, Instant now);
}
