package org.example.matcheat.domain.account.controller;

import jakarta.validation.Valid;
import org.example.matcheat.domain.account.dto.AccountReportCreateRequest;
import org.example.matcheat.domain.account.dto.AccountReportResponse;
import org.example.matcheat.domain.account.dto.AdminPageResponse;
import org.example.matcheat.domain.account.dto.AccountReportResultResponse;
import org.example.matcheat.domain.account.service.AccountReportService;
import org.example.matcheat.domain.account.service.AccountReportAttachmentService;
import org.example.matcheat.domain.account.service.AccountReportSubmissionService;
import org.example.matcheat.domain.account.dto.AccountReportAttachmentResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class AccountReportRestController {
    private final AccountReportService service;
    private final AccountReportAttachmentService attachments;
    private final AccountReportSubmissionService submissions;

    public AccountReportRestController(AccountReportService service, AccountReportAttachmentService attachments,
            AccountReportSubmissionService submissions) {
        this.service = service;
        this.attachments = attachments;
        this.submissions = submissions;
    }

    @PostMapping(path = "/{reportId}/attachments", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountReportAttachmentResponse attach(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long reportId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return attachments.upload(userId(jwt), reportId, file);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountReportResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountReportCreateRequest request) {
        return service.create(userId(jwt), request);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountReportResponse createWithEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("report") AccountReportCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {
        return submissions.submit(userId(jwt), request, files);
    }

    @GetMapping("/mine")
    public AdminPageResponse<AccountReportResultResponse> mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.mine(userId(jwt), page, size);
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
