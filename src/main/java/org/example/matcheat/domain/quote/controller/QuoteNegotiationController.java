package org.example.matcheat.domain.quote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.quote.dto.QuoteNegotiationCreateRequest;
import org.example.matcheat.domain.quote.dto.QuoteNegotiationEditRequest;
import org.example.matcheat.domain.quote.dto.QuoteNegotiationResponse;
import org.example.matcheat.domain.quote.service.QuoteNegotiationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quote Negotiation API", description = "채팅방 1차 견적서(협상형) 생성/조회/수정/AI요약/잠금 API")
@RestController
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}/quote-negotiations")
@RequiredArgsConstructor
public class QuoteNegotiationController {

	private final QuoteNegotiationService quoteNegotiationService;

	@Operation(summary = "1차 견적서 생성", description = "필요할 때 별도로 요청해서 생성한다. 이미 있으면 기존 것을 그대로 반환한다(멱등). 수량/단가는 선택값 — 주문요청/상품 모듈이 준비되면 그쪽 값을 채워서 호출하면 된다.")
	@PostMapping
	public ResponseEntity<QuoteNegotiationResponse> create(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long chatRoomId,
			@RequestBody(required = false) QuoteNegotiationCreateRequest request) {
		Long currentUserId = Long.valueOf(jwt.getSubject());
		QuoteNegotiationResponse response = quoteNegotiationService.createInitialNegotiation(chatRoomId, currentUserId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "1차 견적서 조회", description = "채팅방 참여자만 조회할 수 있다.")
	@GetMapping
	public ResponseEntity<QuoteNegotiationResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long chatRoomId) {
		return ResponseEntity.ok(quoteNegotiationService.getNegotiation(chatRoomId, Long.valueOf(jwt.getSubject())));
	}

	@Operation(summary = "협상 중 자유 수정", description = "NEGOTIATING 상태에서 양쪽 참여자 누구나 수정할 수 있다.")
	@PutMapping
	public ResponseEntity<QuoteNegotiationResponse> editDuringNegotiation(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long chatRoomId,
			@Valid @RequestBody QuoteNegotiationEditRequest request) {
		Long currentUserId = Long.valueOf(jwt.getSubject());
		return ResponseEntity.ok(quoteNegotiationService.editDuringNegotiation(
				chatRoomId, currentUserId, request.getQuantity(), request.getUnitPrice(), request.getDeliveryFee(),
				request.getAdditionalNotes()));
	}

	@Operation(summary = "AI 요약 실행 (채팅방당 1회 제한)", description = "채팅 협의 내용을 AI로 요약해 견적서에 반영한다. 이미 사용했다면 예외가 발생한다.")
	@PostMapping("/ai-summary")
	public ResponseEntity<QuoteNegotiationResponse> triggerAiSummary(@AuthenticationPrincipal Jwt jwt, @PathVariable Long chatRoomId) {
		return ResponseEntity.ok(quoteNegotiationService.triggerAiSummary(chatRoomId, Long.valueOf(jwt.getSubject())));
	}

	@Operation(summary = "AI 요약 이후 마지막 수정", description = "AI_SUMMARIZED 상태에서만 가능한 마지막 1회 수정.")
	@PutMapping("/final")
	public ResponseEntity<QuoteNegotiationResponse> editAfterAiSummary(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long chatRoomId,
			@Valid @RequestBody QuoteNegotiationEditRequest request) {
		Long currentUserId = Long.valueOf(jwt.getSubject());
		return ResponseEntity.ok(quoteNegotiationService.editAfterAiSummary(
				chatRoomId, currentUserId, request.getQuantity(), request.getUnitPrice(), request.getDeliveryFee(),
				request.getAdditionalNotes()));
	}

	@Operation(summary = "최종 확인 및 잠금", description = "이후로는 완전히 수정 불가.")
	@PostMapping("/lock")
	public ResponseEntity<QuoteNegotiationResponse> lock(@AuthenticationPrincipal Jwt jwt, @PathVariable Long chatRoomId) {
		return ResponseEntity.ok(quoteNegotiationService.lockNegotiation(chatRoomId, Long.valueOf(jwt.getSubject())));
	}

}
