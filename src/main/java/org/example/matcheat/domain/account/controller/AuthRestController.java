package org.example.matcheat.domain.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.matcheat.domain.account.dto.EmailAvailabilityResponse;
import org.example.matcheat.domain.account.dto.LoginRequest;
import org.example.matcheat.domain.account.dto.LoginResponse;
import org.example.matcheat.domain.account.dto.SignUpRequest;
import org.example.matcheat.domain.account.dto.SignUpResponse;
import org.example.matcheat.domain.account.dto.SuspensionAppealRequest;
import org.example.matcheat.domain.account.dto.SuspensionStatusResponse;
import org.example.matcheat.domain.account.service.AccountApplicationException;
import org.example.matcheat.domain.account.service.AccountErrorCode;
import org.example.matcheat.domain.account.service.AccountAuthService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "회원가입, 이메일 확인, JWT 로그인 API")
public class AuthRestController {
    private final AccountAuthService accountAuthService;

    public AuthRestController(AccountAuthService accountAuthService) {
        this.accountAuthService = accountAuthService;
    }

    @GetMapping("/email-availability")
    public EmailAvailabilityResponse checkEmail(@RequestParam("email") String email) {
        return EmailAvailabilityResponse.from(accountAuthService.checkEmailAvailability(email));
    }

    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        AccountAuthService.SignUpResult result = accountAuthService.signUp(
                request.email(), request.password(), request.passwordConfirm(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(SignUpResponse.from(result));
    }

    @PostMapping("/login")
    @Operation(
            summary = "로그인 및 Access Token 발급",
            description = "반환된 accessToken을 Swagger UI의 Authorize에 입력하면 보호 API를 호출할 수 있습니다.")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = LoginResponse.from(accountAuthService.login(request.email(), request.password()));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
        } catch (AccountApplicationException exception) {
            if (exception.code() != AccountErrorCode.ACCOUNT_SUSPENDED) throw exception;
            var status = accountAuthService.suspensionStatus(request.email(), request.password());
            return ResponseEntity.status(HttpStatus.LOCKED).cacheControl(CacheControl.noStore()).body(
                    new SuspensionStatusResponse("ACCOUNT_SUSPENDED", status.reason(),
                            status.expiresAt(), status.indefinite()));
        }
    }

    @PostMapping("/suspension/appeals")
    public ResponseEntity<Void> submitSuspensionAppeal(@Valid @RequestBody SuspensionAppealRequest request) {
        accountAuthService.submitSuspensionAppeal(request.email(), request.password(), request.message());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
