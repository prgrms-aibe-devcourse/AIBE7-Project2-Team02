// domain/chat/entity/ChatRoom.java
package org.example.matcheat.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms", indexes = {
		@Index(name = "idx_chat_room_buyer_status_created", columnList = "buyer_id,status,created_at"),
		@Index(name = "idx_chat_room_seller_status_created", columnList = "seller_id,status,created_at")
})
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long proposalId;
	private Long quoteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OriginType originType;

	private Long buyerId;
	private Long sellerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.ACTIVE;

	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;

	public enum OriginType {
		INQUIRY,
		PROPOSAL
	}

	public enum Status {
		ACTIVE,
		CLOSED
	}

	@Builder
	public ChatRoom(Long proposalId, Long quoteId, OriginType originType, Long buyerId, Long sellerId, Status status) {
		this.proposalId = proposalId;
		this.quoteId = quoteId;
		this.originType = originType;
		this.buyerId = buyerId;
		this.sellerId = sellerId;
		this.status = status != null ? status : Status.ACTIVE;
	}

	// ---------------------------------------------------------------
	// 자율 검증 (객체지향 가드)
	// ---------------------------------------------------------------

	public boolean isParticipant(Long userId) {
		if (userId == null) return false;
		return userId.equals(this.buyerId) || userId.equals(this.sellerId);
	}

	public void validateParticipant(Long userId) {
		if (!isParticipant(userId)) {
			throw new AccessDeniedException("해당 채팅방에 접근 권한이 없습니다.");
		}
	}

	// 기존 메서드명 그대로 유지 (QuoteService 등 다른 도메인에서 이 이름으로 호출 중)
	public void updateQuoteId(Long quoteId) {
		this.quoteId = quoteId;
	}

	public void close() {
		this.status = Status.CLOSED;
	}

	public boolean isParticipant(Long userId, Long sellerProfileId) {
		if (userId == null) return false;
		if (userId.equals(this.buyerId)) return true;
		return sellerProfileId != null && sellerProfileId.equals(this.sellerId);
	}

	public void validateParticipant(Long userId, Long sellerProfileId) {
		if (!isParticipant(userId, sellerProfileId)) {
			throw new AccessDeniedException("해당 채팅방에 접근 권한이 없습니다.");
		}
	}

}
