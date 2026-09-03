package org.example.matcheat.domain.account.controller;

import jakarta.validation.Valid;
import org.example.matcheat.domain.account.dto.AccountReportReviewRequest;
import org.example.matcheat.domain.account.dto.AccountReportHistoryResponse;
import org.example.matcheat.domain.account.dto.AdminAccountReportResponse;
import org.example.matcheat.domain.account.dto.AdminPageResponse;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.service.AccountReportService;
import org.example.matcheat.domain.account.service.AccountReportAttachmentService;
import org.example.matcheat.domain.account.dto.AccountReportAttachmentResponse;
import org.example.matcheat.domain.account.dto.AccountPenaltyRequest;
import org.example.matcheat.domain.account.dto.AccountPenaltyResponse;
import org.example.matcheat.domain.account.service.AccountPenaltyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminAccountReportRestController {
    private final AccountReportService service;
    private final AccountReportAttachmentService attachments;
    private final AccountPenaltyService penalties;

    public AdminAccountReportRestController(AccountReportService service,
            AccountReportAttachmentService attachments, AccountPenaltyService penalties) {
        this.service = service;
        this.attachments = attachments;
        this.penalties = penalties;
    }

    @GetMapping
    public AdminPageResponse<AdminAccountReportResponse> reports(
            @RequestParam(required = false) AccountReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(status, page, size);
    }

    @PatchMapping("/{reportId}")
    public AdminAccountReportResponse review(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long reportId,
            @Valid @RequestBody AccountReportReviewRequest request) {
        return service.review(
                Long.parseLong(jwt.getSubject()), reportId, request.status(), request.adminResponse());
    }

    @GetMapping("/{reportId}/history")
    public List<AccountReportHistoryResponse> history(@PathVariable long reportId) {
        return service.history(reportId);
    }

    @GetMapping("/{reportId}/attachments")
    public List<AccountReportAttachmentResponse> attachments(@PathVariable long reportId) {
        return attachments.list(reportId);
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<org.springframework.core.io.Resource> attachment(@PathVariable long attachmentId)
            throws IOException {
        var value = attachments.download(attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"evidence\"")
                .body(value.resource());
    }

    @PostMapping("/{reportId}/penalties")
    public AccountPenaltyResponse penalize(@AuthenticationPrincipal Jwt jwt, @PathVariable long reportId,
            @Valid @RequestBody AccountPenaltyRequest request) {
        return penalties.issue(Long.parseLong(jwt.getSubject()), reportId, request.days(), request.reason());
    }

    @GetMapping("/users/{userId}/penalties")
    public List<AccountPenaltyResponse> penaltyHistory(@PathVariable long userId) {
        return penalties.history(userId);
    }
}
