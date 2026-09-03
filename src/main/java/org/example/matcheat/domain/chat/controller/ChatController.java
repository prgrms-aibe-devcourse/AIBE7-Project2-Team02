package org.example.matcheat.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.chat.dto.ChatRoomCreateRequest;
import org.example.matcheat.domain.chat.dto.ChatRoomResponse;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.global.dto.PageResponse;
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
	public ResponseEntity<?> getChatRooms(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) ChatRoom.Status status) {
		List<ChatRoomResponse> values = chatService.getChatRooms(Long.valueOf(jwt.getSubject()));
		if (page == null && size == null && status == null) return ResponseEntity.ok(values);
		return ResponseEntity.ok(PageResponse.from(values, page == null ? 0 : page, size == null ? 20 : size,
				room -> status == null || status == room.getStatus()));
	}

}
