package org.example.matcheat.domain.chat.dto; // 패키지 경로 확인 필요

import lombok.Builder;
import lombok.Getter;
import org.example.matcheat.domain.chat.entity.ChatRoom;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatRoomResponse {

	private Long chatRoomId;
	private Long proposalId;
	private Long quoteId;
	private Long productId; // [추가]
	private ChatRoom.OriginType originType;
	private ChatRoom.Status status;
	private Long buyerId;
	private Long sellerId;
	private LocalDateTime createdAt;

	public static ChatRoomResponse from(ChatRoom chatRoom) {
		return ChatRoomResponse.builder()
				.chatRoomId(chatRoom.getId())
				.proposalId(chatRoom.getProposalId())
				.quoteId(chatRoom.getQuoteId())
				.productId(chatRoom.getProductId()) // [추가]
				.originType(chatRoom.getOriginType())
				.status(chatRoom.getStatus())
				.buyerId(chatRoom.getBuyerId())
				.sellerId(chatRoom.getSellerId())
				.createdAt(chatRoom.getCreatedAt())
				.build();
	}
}