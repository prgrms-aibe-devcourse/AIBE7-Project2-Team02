package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.dto.AccountReportCreateRequest;
import org.example.matcheat.domain.account.dto.AccountReportResponse;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountReportSubmissionServiceTest {
    private final AccountReportService reports = mock(AccountReportService.class);
    private final AccountReportAttachmentService attachments = mock(AccountReportAttachmentService.class);
    private final AccountReportSubmissionService service = new AccountReportSubmissionService(reports, attachments);

    @Test
    void submitsReportAndEvidenceAsOneFlow() throws Exception {
        var request = new AccountReportCreateRequest("report", "message", null, null);
        var response = new AccountReportResponse(3L, "report", "message", null, null,
                AccountReportStatus.PENDING, null, Instant.now(), Instant.now(), null);
        var image = new MockMultipartFile("files", "proof.png", "image/png", new byte[]{1});
        when(reports.create(7L, request)).thenReturn(response);

        assertThat(service.submit(7L, request, List.of(image))).isEqualTo(response);

        verify(attachments).upload(7L, 3L, image);
    }

    @Test
    void rejectsMoreThanThreeImagesBeforeCreatingReport() {
        var request = new AccountReportCreateRequest("report", "message", null, null);
        var image = new MockMultipartFile("files", "proof.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.submit(7L, request, List.of(image, image, image, image)))
                .isInstanceOf(AccountApplicationException.class);
        verifyNoInteractions(reports, attachments);
    }
}
