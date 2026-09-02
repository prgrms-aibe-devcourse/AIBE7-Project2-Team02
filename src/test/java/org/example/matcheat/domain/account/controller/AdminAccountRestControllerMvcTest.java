package org.example.matcheat.domain.account.controller;

import org.example.matcheat.config.SecurityConfig;
import org.example.matcheat.domain.account.enums.UserRole;
import org.example.matcheat.domain.account.enums.UserStatus;
import org.example.matcheat.domain.account.repository.AdminAccountRepository;
import org.example.matcheat.domain.account.security.AccountSecurityErrorHandler;
import org.example.matcheat.domain.account.service.AdminAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAccountRestController.class)
@Import({
        SecurityConfig.class,
        AccountApiExceptionHandler.class,
        AccountSecurityErrorHandler.class,
        AdminAccountRestControllerMvcTest.SecurityTestBeans.class
})
class AdminAccountRestControllerMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccountService service;

    @Test
    void rejectsRegularMember() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void returnsDashboardForAdmin() throws Exception {
        when(service.dashboard()).thenReturn(new AdminAccountService.DashboardResult(21, 4));

        mockMvc.perform(get("/api/v1/admin/dashboard").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(21))
                .andExpect(jsonPath("$.pendingSellerApplications").value(4));
    }

    @Test
    void usesJwtSubjectWhenChangingUserStatus() throws Exception {
        AdminAccountRepository.UserSummary changed = new AdminAccountRepository.UserSummary(
                9L, "user@example.com", "사용자", UserRole.USER, UserStatus.SUSPENDED,
                2, Instant.parse("2026-08-28T00:00:00Z"));
        when(service.changeUserStatus(7L, 9L, UserStatus.SUSPENDED, "policy violation")).thenReturn(changed);

        mockMvc.perform(patch("/api/v1/admin/users/9/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"policy violation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(9))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(service).changeUserStatus(7L, 9L, UserStatus.SUSPENDED, "policy violation");
    }

    @Test
    void validatesStatusRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/9/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.subject("7").claim("role", "ADMIN"))
                .authorities(() -> "ROLE_ADMIN");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestBeans {
        @Bean("accountJwtDecoder")
        JwtDecoder accountJwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean("accountJwtAuthenticationConverter")
        Converter<Jwt, AbstractAuthenticationToken> accountJwtAuthenticationConverter() {
            return JwtAuthenticationToken::new;
        }
    }
}
