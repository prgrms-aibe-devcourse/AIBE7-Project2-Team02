package org.example.matcheat.domain.order.controller;

import org.example.matcheat.domain.order.service.TradeActivityQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeActivityControllerTest {
    private final TradeActivityQueryService service = mock(TradeActivityQueryService.class);
    private final TradeActivityController controller = new TradeActivityController(service);

    @Test
    void usesJwtSubjectForPurchaseAndSaleQueries() {
        when(service.findPurchases(41L)).thenReturn(List.of());
        when(service.findSales(41L)).thenReturn(List.of());

        controller.findPurchases(jwt("41"));
        controller.findSales(jwt("41"));

        verify(service).findPurchases(41L);
        verify(service).findSales(41L);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
    }
}
