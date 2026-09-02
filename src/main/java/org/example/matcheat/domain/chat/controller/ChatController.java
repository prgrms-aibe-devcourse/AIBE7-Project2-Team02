package org.example.matcheat.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.chat.dto.ChatRoomCreateRequest;
import org.example.matcheat.domain.chat.dto.ChatRoomProductUpdateRequest;
import org.example.matcheat.domain.chat.dto.ChatRoomResponse;
import org.example.matcheat.domain.chat.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	@PostMapping
	public ResponseEntity<ChatRoomResponse> createChatRoom(
			@AuthenticationPrincipal Jwt jwt,
			@RequestBody ChatRoomCreateRequest request
	) {
		// 행위자 ID는 JWT subject에서만 결정한다.
		// 인증 붙으면 이 메서드 하나만 바꾸면 이 클래스의 모든 엔드포인트에 적용된다.
		Long currentUserId = Long.valueOf(jwt.getSubject());

		ChatRoomResponse response = chatService.createChatRoom(request, currentUserId);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{chatRoomId}")
	public ResponseEntity<ChatRoomResponse> getChatRoom(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long chatRoomId) {
		Long currentUserId = Long.valueOf(jwt.getSubject());
		ChatRoomResponse response = chatService.getChatRoom(chatRoomId, currentUserId);
		return ResponseEntity.ok(response);
	}

	@GetMapping
	public ResponseEntity<List<ChatRoomResponse>> getChatRooms(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(chatService.getChatRooms(Long.valueOf(jwt.getSubject())));
	}

	@Operation(summary = "채팅방 연결 상품 전환", description = "이 방이 가리키는 상품을 바꾼다. 참여자만 가능하며, 클라이언트에서 경고/확인을 거친 뒤 호출해야 한다.")
	@PatchMapping("/{chatRoomId}/product")
	public ResponseEntity<ChatRoomResponse> changeProduct(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long chatRoomId,
			@RequestBody ChatRoomProductUpdateRequest request) {
		Long currentUserId = Long.valueOf(jwt.getSubject());
		ChatRoomResponse response = chatService.changeChatRoomProduct(chatRoomId, currentUserId, request.getProductId());
		return ResponseEntity.ok(response);
	}

}
