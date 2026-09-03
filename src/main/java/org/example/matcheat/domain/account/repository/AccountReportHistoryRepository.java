package org.example.matcheat.domain.account.repository;

import org.example.matcheat.domain.account.entity.AccountReportHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountReportHistoryRepository extends JpaRepository<AccountReportHistoryEntity, Long> {
    List<AccountReportHistoryEntity> findByReportIdOrderByChangedAtAscIdAsc(Long reportId);
}
