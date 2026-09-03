package org.example.matcheat.config;

import org.example.matcheat.domain.account.security.AccountSecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigMvcTest.TestController.class)
@Import({
        SecurityConfig.class,
        AccountSecurityErrorHandler.class,
        SecurityConfigMvcTest.TestController.class,
        SecurityConfigMvcTest.SecurityTestBeans.class
})
class SecurityConfigMvcTest {
    private final MockMvc mockMvc;

    @Autowired
    SecurityConfigMvcTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void permitsPublicAuthenticationApi() throws Exception {
        mockMvc.perform(get("/api/v1/auth/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void permitsOnlyPublicProductReadsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/search"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnauthenticatedAccountApiWithCommonError() throws Exception {
        mockMvc.perform(get("/api/v1/account/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void protectsChatAndPaymentApis() throws Exception {
        mockMvc.perform(get("/api/v1/chat-rooms"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/chat-files/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/quotes/1/payments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders/purchases"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/chat-rooms")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/chat-files/1")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/quotes/1/payments")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/purchases")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void permitsAuthenticatedMemberApi() throws Exception {
        mockMvc.perform(get("/api/v1/account/ping")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/account/ping")
                        .with(jwt().authorities(() -> "ROLE_USER", () -> "ROLE_SELLER")))
                .andExpect(status().isOk());
    }

    @Test
    void restrictsSellerApiByRole() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/products")
                        .with(jwt().authorities(() -> "ROLE_USER", () -> "ROLE_SELLER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/1/order-requests/recommendations")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/products/1/order-requests/recommendations")
                        .with(jwt().authorities(() -> "ROLE_USER", () -> "ROLE_SELLER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/requests")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/requests")
                        .with(jwt().authorities(() -> "ROLE_USER", () -> "ROLE_SELLER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/sales")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/sales")
                        .with(jwt().authorities(() -> "ROLE_USER", () -> "ROLE_SELLER")))
                .andExpect(status().isOk());
    }

    @Test
    void restrictsAdminApiByRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/ping")
                        .with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestController {
        @GetMapping({
                "/api/v1/auth/ping",
                "/api/v1/account/ping",
                "/api/v1/admin/ping"
        })
        String ping() {
            return "ok";
        }

        @PostMapping("/api/v1/products")
        String createProduct() {
            return "ok";
        }

        @GetMapping("/api/products/{productId}/order-requests/recommendations")
        String recommendations() {
            return "ok";
        }

        @GetMapping({
                "/api/v1/products",
                "/api/v1/products/search",
                "/api/v1/products/{id}",
                "/api/v1/products/mine"
        })
        String readProduct() {
            return "ok";
        }

        @GetMapping({"/api/v1/requests", "/api/v1/requests/search"})
        String readRequests() {
            return "ok";
        }

        @GetMapping({
                "/api/v1/chat-rooms",
                "/api/v1/chat-files/{fileId}",
                "/api/v1/quotes/{quoteId}/payments",
                "/api/v1/orders/purchases",
                "/api/v1/orders/sales"
        })
        String readProtectedTradeResource() {
            return "ok";
        }
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
