package org.example.matcheat.domain.account.repository;

import org.example.matcheat.domain.account.entity.AccountReportAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountReportAttachmentRepository extends JpaRepository<AccountReportAttachmentEntity, Long> {
    List<AccountReportAttachmentEntity> findByReportIdOrderByCreatedAtAsc(Long reportId);
    long countByReportId(Long reportId);
}
