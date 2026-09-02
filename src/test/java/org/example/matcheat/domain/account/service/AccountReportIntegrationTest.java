package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.dto.AccountReportCreateRequest;
import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.repository.AccountReportHistoryRepository;
import org.example.matcheat.domain.account.repository.UserCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-report;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.account.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
@Transactional
class AccountReportIntegrationTest {
    @Autowired
    private UserCredentialRepository users;

    @Autowired
    private AccountReportService reports;

    @Autowired
    private AccountReportHistoryRepository histories;

    @Test
    void persistsReportAndEveryStatusChangeAsHistory() {
        UserAccount reporter = users.save(UserAccount.registerUser(
                "reporter@example.com", "{bcrypt}hash", "Reporter"));
        UserAccount admin = users.save(UserAccount.registerAdmin(
                "admin-report@example.com", "{bcrypt}hash", "Admin"));

        var created = reports.create(reporter.id(), new AccountReportCreateRequest(
                "Transaction report", "Please review this transaction.", null, null));
        reports.review(admin.id(), created.reportId(), AccountReportStatus.IN_REVIEW, null);
        reports.review(admin.id(), created.reportId(), AccountReportStatus.RESOLVED, "Reviewed and resolved.");

        var history = reports.history(created.reportId());
        assertThat(history).hasSize(3);
        assertThat(history).extracting(item -> item.newStatus())
                .containsExactly(AccountReportStatus.PENDING, AccountReportStatus.IN_REVIEW,
                        AccountReportStatus.RESOLVED);
        assertThat(history.get(0).previousStatus()).isNull();
        assertThat(history.get(0).actorId()).isEqualTo(reporter.id());
        assertThat(history.get(2).actorId()).isEqualTo(admin.id());
        assertThat(history.get(2).adminResponse()).isEqualTo("Reviewed and resolved.");
        assertThat(histories.findByReportIdOrderByChangedAtAscIdAsc(created.reportId())).hasSize(3);
    }
}
