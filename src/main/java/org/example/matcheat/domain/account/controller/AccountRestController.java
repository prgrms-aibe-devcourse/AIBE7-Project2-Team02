package org.example.matcheat.domain.account.controller;

import jakarta.validation.Valid;
import org.example.matcheat.domain.account.dto.AccountProfileResponse;
import org.example.matcheat.domain.account.dto.SellerApplicationRequest;
import org.example.matcheat.domain.account.dto.SellerApplicationResponse;
import org.example.matcheat.domain.account.dto.UpdateAccountNameRequest;
import org.example.matcheat.domain.account.dto.WithdrawAccountRequest;
import org.example.matcheat.domain.account.dto.ChangePasswordRequest;
import org.example.matcheat.domain.account.service.AccountProfileService;
import org.example.matcheat.domain.account.service.SellerApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
public class AccountRestController {
    private final AccountProfileService accountProfileService;
    private final SellerApplicationService sellerApplicationService;

    public AccountRestController(
            AccountProfileService accountProfileService,
            SellerApplicationService sellerApplicationService) {
        this.accountProfileService = accountProfileService;
        this.sellerApplicationService = sellerApplicationService;
    }

    @GetMapping("/me")
    public AccountProfileResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return AccountProfileResponse.from(accountProfileService.getCurrentUser(userId(jwt)));
    }

    @PatchMapping("/me")
    public AccountProfileResponse updateName(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateAccountNameRequest request) {
        return AccountProfileResponse.from(accountProfileService.updateName(userId(jwt), request.name()));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        accountProfileService.changePassword(userId(jwt), request.currentPassword(),
                request.newPassword(), request.newPasswordConfirm());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WithdrawAccountRequest request) {
        accountProfileService.withdraw(userId(jwt), request.currentPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/seller-applications")
    public ResponseEntity<SellerApplicationResponse> applyForSeller(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SellerApplicationRequest request) {
        SellerApplicationService.ApplicationResult result = sellerApplicationService.apply(
                userId(jwt),
                request.businessName(),
                request.businessNumber(),
                request.latitude(),
                request.longitude(),
                request.deliveryRadiusKm());
        return ResponseEntity.status(HttpStatus.CREATED).body(SellerApplicationResponse.from(result));
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
