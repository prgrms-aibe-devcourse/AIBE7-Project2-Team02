package org.example.matcheat.domain.order.controller;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.order.dto.TradeActivityResponse;
import org.example.matcheat.domain.order.service.TradeActivityQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.example.matcheat.global.dto.PageResponse;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class TradeActivityController {
    private final TradeActivityQueryService tradeActivities;

    ResponseEntity<List<TradeActivityResponse>> findPurchases(Jwt jwt) {
        return ResponseEntity.ok(tradeActivities.findPurchases(currentAccountId(jwt)));
    }

    ResponseEntity<List<TradeActivityResponse>> findSales(Jwt jwt) {
        return ResponseEntity.ok(tradeActivities.findSales(currentAccountId(jwt)));
    }

    @GetMapping("/purchases")
    public ResponseEntity<?> findPurchases(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status
    ) {
        List<TradeActivityResponse> values = tradeActivities.findPurchases(currentAccountId(jwt));
        return page == null && size == null && status == null ? ResponseEntity.ok(values)
                : ResponseEntity.ok(page(values, page == null ? 0 : page, size == null ? 20 : size, status));
    }

    @GetMapping("/sales")
    public ResponseEntity<?> findSales(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status
    ) {
        List<TradeActivityResponse> values = tradeActivities.findSales(currentAccountId(jwt));
        return page == null && size == null && status == null ? ResponseEntity.ok(values)
                : ResponseEntity.ok(page(values, page == null ? 0 : page, size == null ? 20 : size, status));
    }

    private static PageResponse<TradeActivityResponse> page(
            List<TradeActivityResponse> values, int page, int size, String status) {
        return PageResponse.from(values, page, size, value -> status == null || status.isBlank()
                || status.equalsIgnoreCase(value.sourceStatus())
                || status.equalsIgnoreCase(value.paymentStatus()));
    }

    private static long currentAccountId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
