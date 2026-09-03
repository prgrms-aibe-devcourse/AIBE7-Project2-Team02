package org.example.matcheat.domain.chat.dto; // 패키지 경로 확인 필요

import lombok.Builder;
import lombok.Getter;
import org.example.matcheat.domain.chat.entity.ChatMessage;
import org.example.matcheat.domain.chat.entity.ChatRoom;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatRoomResponse {

	private Long chatRoomId;
	private Long proposalId;
	private Long quoteId;
	private ChatRoom.OriginType originType;
	private ChatRoom.Status status; // 👈 1. status 필드 추가
	private Long buyerId;
	private Long sellerId;
	private LocalDateTime createdAt;
	private String lastMessage;
	private ChatMessage.MessageType lastMessageType;
	private LocalDateTime lastMessageAt;

	public static ChatRoomResponse from(ChatRoom chatRoom) {
		return ChatRoomResponse.builder()
				.chatRoomId(chatRoom.getId())
				.proposalId(chatRoom.getProposalId())
				.quoteId(chatRoom.getQuoteId())
				.originType(chatRoom.getOriginType())
				.status(chatRoom.getStatus()) // 👈 2. status 값 매핑 추가
				.buyerId(chatRoom.getBuyerId())
				.sellerId(chatRoom.getSellerId())
				.createdAt(chatRoom.getCreatedAt())
				.build();
	}

	public static ChatRoomResponse from(ChatRoom chatRoom, ChatMessage latestMessage) {
		ChatRoomResponse response = from(chatRoom);
		if (latestMessage == null) {
			return response;
		}
		response.lastMessage = preview(latestMessage);
		response.lastMessageType = latestMessage.getMessageType();
		response.lastMessageAt = latestMessage.getCreatedAt();
		return response;
	}

	private static String preview(ChatMessage message) {
		if (message.getMessageType() == ChatMessage.MessageType.IMAGE) {
			return "이미지를 보냈습니다.";
		}
		if (message.getMessageType() == ChatMessage.MessageType.PDF) {
			return message.getChatFile() == null
					? "파일을 보냈습니다."
					: message.getChatFile().getOriginalFileName();
		}
		String content = message.getContent();
		if (content == null || content.isBlank()) {
			return "새 메시지가 있습니다.";
		}
		String normalized = content.replaceAll("\\s+", " ").trim();
		return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
	}
}
