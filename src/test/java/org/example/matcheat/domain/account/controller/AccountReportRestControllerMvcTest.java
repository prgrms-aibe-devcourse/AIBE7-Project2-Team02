package org.example.matcheat.domain.account.controller;

import org.example.matcheat.config.SecurityConfig;
import org.example.matcheat.domain.account.dto.AccountReportResponse;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.security.AccountSecurityErrorHandler;
import org.example.matcheat.domain.account.service.AccountReportService;
import org.example.matcheat.domain.account.service.AccountReportAttachmentService;
import org.example.matcheat.domain.account.service.AccountReportSubmissionService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountReportRestController.class)
@Import({SecurityConfig.class, AccountApiExceptionHandler.class, AccountSecurityErrorHandler.class,
        AccountReportRestControllerMvcTest.SecurityTestBeans.class})
class AccountReportRestControllerMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountReportService service;

    @MockitoBean
    private AccountReportAttachmentService attachments;

    @MockitoBean
    private AccountReportSubmissionService submissions;

    @Test
    void rejectsUnauthenticatedReport() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"신고\",\"message\":\"내용\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsReportUsingJwtSubject() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(new AccountReportResponse(
                3L, "신고", "확인해 주세요.", null, null, AccountReportStatus.PENDING, null,
                Instant.parse("2026-09-02T03:00:00Z"), Instant.parse("2026-09-02T03:00:00Z"), null));

        mockMvc.perform(post("/api/v1/reports")
                        .with(jwt().jwt(token -> token.subject("7")).authorities(() -> "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"신고\",\"message\":\"확인해 주세요.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value(3))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(service).create(org.mockito.ArgumentMatchers.eq(7L), any());
    }

    @Test
    void createsReportAndEvidenceUsingSingleMultipartRequest() throws Exception {
        var response = new AccountReportResponse(
                3L, "report", "message", null, null, AccountReportStatus.PENDING, null,
                Instant.parse("2026-09-02T03:00:00Z"), Instant.parse("2026-09-02T03:00:00Z"), null);
        when(submissions.submit(org.mockito.ArgumentMatchers.eq(7L), any(), any())).thenReturn(response);
        var report = new org.springframework.mock.web.MockMultipartFile(
                "report", "", "application/json", "{\"title\":\"report\",\"message\":\"message\"}".getBytes());
        var evidence = new org.springframework.mock.web.MockMultipartFile(
                "files", "proof.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});

        mockMvc.perform(multipart("/api/v1/reports")
                        .file(report).file(evidence)
                        .with(jwt().jwt(token -> token.subject("7")).authorities(() -> "ROLE_USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value(3));

        verify(submissions).submit(org.mockito.ArgumentMatchers.eq(7L), any(), any());
    }

    @Test
    void validatesReportBody() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .with(jwt().jwt(token -> token.subject("7")).authorities(() -> "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.message").exists());
    }

    @Test
    void validatesPositiveReportTargetId() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .with(jwt().jwt(token -> token.subject("7")).authorities(() -> "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"채팅 신고","message":"확인해 주세요.",
                                 "targetType":"CHAT_ROOM","targetId":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.targetId").exists());
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
