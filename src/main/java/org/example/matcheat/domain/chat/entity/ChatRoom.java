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
@Table(name = "chat_rooms")
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long proposalId;
	private Long quoteId;

	/**
	 * [추가] 이 방이 현재 어떤 상품을 기준으로 대화 중인지. nullable —
	 * sellerId를 직접 지정해서 만든 방(상품 미연결)은 null일 수 있다.
	 * 재사용(getOrCreateChatRoomEntity) 시에는 자동으로 바뀌지 않는다 —
	 * 오직 changeProduct()를 통한 명시적 호출로만 바뀐다 (ChatService 참고).
	 */
	private Long productId;

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
	public ChatRoom(Long proposalId, Long quoteId, Long productId, OriginType originType,
	                Long buyerId, Long sellerId, Status status) {
		this.proposalId = proposalId;
		this.quoteId = quoteId;
		this.productId = productId;
		this.originType = originType;
		this.buyerId = buyerId;
		this.sellerId = sellerId;
		this.status = status != null ? status : Status.ACTIVE;
	}

	// ---------------------------------------------------------------
	// 자율 검증 (객체지향 가드)
	// ---------------------------------------------------------------

	// [주의] 아래 1-인자 버전은 userId만으로 sellerId(=seller_profiles.seller_id)와
	// 비교하는 예전 버그를 그대로 갖고 있다. ChatService는 이미 2-인자 버전으로
	// 전환했지만 다른 호출부(ChatMessageService 등)가 아직 이걸 쓰고 있다면
	// 그쪽도 전환이 필요하다 — 이번 변경 범위 밖이라 삭제하지 않고 남겨둠.
	public boolean isParticipant(Long userId) {
		if (userId == null) return false;
		return userId.equals(this.buyerId) || userId.equals(this.sellerId);
	}

	public void validateParticipant(Long userId) {
		if (!isParticipant(userId)) {
			throw new AccessDeniedException("해당 채팅방에 접근 권한이 없습니다.");
		}
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

	// 기존 메서드명 그대로 유지 (QuoteService 등 다른 도메인에서 이 이름으로 호출 중)
	public void updateQuoteId(Long quoteId) {
		this.quoteId = quoteId;
	}

	/**
	 * [추가] 이 방이 가리키는 상품을 명시적으로 바꾼다 (A안: 방은 유지, 상품만 교체).
	 * 재사용 로직에서 자동으로 호출되지 않고, ChatService.changeChatRoomProduct()를
	 * 통해서만 호출된다 — 항상 사용자가 확인한 뒤에만 실행되도록 하기 위함.
	 */
	public void changeProduct(Long newProductId) {
		if (this.status != Status.ACTIVE) {
			throw new IllegalStateException("종료된 채팅방의 상품은 변경할 수 없습니다.");
		}
		this.productId = newProductId;
	}

	public void close() {
		this.status = Status.CLOSED;
	}
}