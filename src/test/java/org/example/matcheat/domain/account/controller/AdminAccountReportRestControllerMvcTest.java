package org.example.matcheat.domain.account.controller;

import org.example.matcheat.config.SecurityConfig;
import org.example.matcheat.domain.account.dto.AccountReportHistoryResponse;
import org.example.matcheat.domain.account.dto.AdminAccountReportResponse;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.security.AccountSecurityErrorHandler;
import org.example.matcheat.domain.account.service.AccountReportService;
import org.example.matcheat.domain.account.service.AccountReportAttachmentService;
import org.example.matcheat.domain.account.service.AccountPenaltyService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAccountReportRestController.class)
@Import({SecurityConfig.class, AccountApiExceptionHandler.class, AccountSecurityErrorHandler.class,
        AdminAccountReportRestControllerMvcTest.SecurityTestBeans.class})
class AdminAccountReportRestControllerMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountReportService service;

    @MockitoBean
    private AccountReportAttachmentService attachments;

    @MockitoBean
    private AccountPenaltyService penalties;

    @Test
    void rejectsRegularMemberFromAdminReports() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports")
                        .with(jwt().jwt(token -> token.subject("7")).authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReviewsReport() throws Exception {
        when(service.review(eq(1L), eq(3L), eq(AccountReportStatus.RESOLVED), any()))
                .thenReturn(response());

        mockMvc.perform(patch("/api/v1/admin/reports/3")
                        .with(jwt().jwt(token -> token.subject("1")).authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminResponse\":\"처리했습니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.adminResponse").value("처리했습니다."));

        verify(service).review(1L, 3L, AccountReportStatus.RESOLVED, "처리했습니다.");
    }

    @Test
    void adminReadsReportHistory() throws Exception {
        Instant now = Instant.parse("2026-09-02T03:00:00Z");
        when(service.history(3L)).thenReturn(List.of(new AccountReportHistoryResponse(
                10L, AccountReportStatus.PENDING, AccountReportStatus.IN_REVIEW, 1L, null, now)));

        mockMvc.perform(get("/api/v1/admin/reports/3/history")
                        .with(jwt().jwt(token -> token.subject("1")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].historyId").value(10L))
                .andExpect(jsonPath("$[0].previousStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].newStatus").value("IN_REVIEW"));

        verify(service).history(3L);
    }

    private static AdminAccountReportResponse response() {
        Instant now = Instant.parse("2026-09-02T03:00:00Z");
        return new AdminAccountReportResponse(
                3L, 7L, "사용자", "user@example.com", 9L, "신고", "내용",
                null, null, null, AccountReportStatus.RESOLVED, "처리했습니다.", 1L, now, now, now);
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
