package org.example.matcheat.config;

import org.example.matcheat.domain.quote.exception.AiSummaryFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = {
        "org.example.matcheat.domain.order.controller",
        "org.example.matcheat.domain.chat.controller",
        "org.example.matcheat.domain.quote.controller",
        "org.example.matcheat.domain.estimate.controller",
        "org.example.matcheat.domain.payment.controller"
})
public class TradeApiExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> handleForbidden(AccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> handleConflict(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        String message = exception.getMessage();
        HttpStatus status = message != null
                && (message.contains("존재하지") || message.contains("찾을 수 없"))
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return error(status, message);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "code", status.name(),
                "message", message == null ? status.getReasonPhrase() : message));
    }

    @ExceptionHandler(AiSummaryFailedException.class)
    ResponseEntity<Map<String, String>> handleAiSummaryFailed(AiSummaryFailedException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
}
