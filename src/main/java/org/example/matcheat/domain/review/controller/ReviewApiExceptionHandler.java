package org.example.matcheat.domain.review.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 리뷰(Review) API에서 발생하는 주요 예외를 사람이 읽을 수 있는 HTTP 응답으로 변환한다.
 * ProductApiExceptionHandler와 같은 패턴이다.
 */
@RestControllerAdvice(basePackages = "org.example.matcheat.domain.review.controller")
public class ReviewApiExceptionHandler {

    /**
     * 본인 결제 건이 아니거나 로그인하지 않은 요청을 처리한다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    /**
     * 존재하지 않는 결제/리뷰 ID, 잘못된 입력값을 처리한다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        String message = exception.getMessage();
        HttpStatus status = message != null
                && (message.contains("존재하지") || message.contains("찾을 수 없"))
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;

        return error(status, message);
    }

    /**
     * 결제가 아직 완료되지 않았거나 이미 리뷰를 작성한 상태 등, 지금 상태로는 할 수 없는 요청을 처리한다.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * 리뷰 입력값(@Valid) 검증 실패를 처리한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(fieldError ->
                        fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage())
                );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "리뷰 입력값을 확인해주세요.");
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
