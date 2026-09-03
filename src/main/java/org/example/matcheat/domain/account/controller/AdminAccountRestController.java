package org.example.matcheat.domain.account.controller;

import jakarta.validation.Valid;
import org.example.matcheat.domain.account.dto.AdminDashboardResponse;
import org.example.matcheat.domain.account.dto.AdminPageResponse;
import org.example.matcheat.domain.account.dto.AdminSellerApplicationResponse;
import org.example.matcheat.domain.account.dto.AdminSellerReviewRequest;
import org.example.matcheat.domain.account.dto.AdminUserResponse;
import org.example.matcheat.domain.account.dto.AdminUserStatusRequest;
import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.AdminAccountRepository;
import org.example.matcheat.domain.account.service.AdminAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccountRestController {
    private final AdminAccountService service;

    public AdminAccountRestController(AdminAccountService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard() {
        return AdminDashboardResponse.from(service.dashboard());
    }

    @GetMapping("/users")
    public AdminPageResponse<AdminUserResponse> users(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminAccountRepository.PageResult<AdminAccountRepository.UserSummary> result =
                service.searchUsers(keyword, status, page, size);
        return AdminPageResponse.from(result, AdminUserResponse::from);
    }

    @PatchMapping("/users/{userId}/status")
    public AdminUserResponse changeUserStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long userId,
            @Valid @RequestBody AdminUserStatusRequest request) {
        return AdminUserResponse.from(service.changeUserStatus(
                userId(jwt), userId, request.status(), request.reason()));
    }

    @GetMapping("/seller-applications")
    public AdminPageResponse<AdminSellerApplicationResponse> sellerApplications(
            @RequestParam(required = false) SellerVerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminAccountRepository.PageResult<AdminAccountRepository.SellerSummary> result =
                service.searchSellerApplications(status, page, size);
        return AdminPageResponse.from(result, AdminSellerApplicationResponse::from);
    }

    @PatchMapping("/seller-applications/{sellerId}")
    public AdminSellerApplicationResponse reviewSellerApplication(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sellerId,
            @Valid @RequestBody AdminSellerReviewRequest request) {
        return AdminSellerApplicationResponse.from(service.reviewSellerApplication(
                userId(jwt), sellerId, request.status(), request.rejectionReason()));
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
