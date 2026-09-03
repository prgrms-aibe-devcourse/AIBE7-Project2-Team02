package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.dto.AccountReportCreateRequest;
import org.example.matcheat.domain.account.dto.AccountReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class AccountReportSubmissionService {
    private static final int MAX_ATTACHMENTS = 3;

    private final AccountReportService reports;
    private final AccountReportAttachmentService attachments;

    public AccountReportSubmissionService(
            AccountReportService reports,
            AccountReportAttachmentService attachments) {
        this.reports = reports;
        this.attachments = attachments;
    }

    @Transactional(rollbackFor = Exception.class)
    public AccountReportResponse submit(
            long reporterId,
            AccountReportCreateRequest request,
            List<MultipartFile> files) throws IOException {
        List<MultipartFile> evidence = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (evidence.size() > MAX_ATTACHMENTS) {
            throw new AccountApplicationException(
                    AccountErrorCode.VALIDATION_FAILED, "증거 이미지는 최대 3개까지 첨부할 수 있습니다.");
        }

        AccountReportResponse report = reports.create(reporterId, request);
        try {
            for (MultipartFile file : evidence) {
                attachments.upload(reporterId, report.reportId(), file);
            }
            return report;
        } catch (IOException | RuntimeException exception) {
            attachments.removeStoredFiles(report.reportId());
            throw exception;
        }
    }
}
