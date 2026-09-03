package org.example.matcheat.domain.quote.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 채팅방 생성 시점에 자동 생성되는 "1차 견적서(협상형)".
 * 기존 Quote(제안형: SENT→ACCEPTED/REJECTED)와는 의도적으로 분리했다.
 * - Quote: 한쪽이 보내고 상대가 수락/거절 (다건 가능)
 * - QuoteNegotiation: 채팅방 1개당 1건, 양쪽이 같이 편집하다가 AI 요약 → 잠금
 *
 * 잠긴(LOCKED) 뒤에는 QuoteService.createFromLockedNegotiation()이 이 결과를
 * 별도의 확정 Quote(status=ACCEPTED)로 발행한다 — 이후 Order 생성/조회는
 * 제안형이든 협상형이든 항상 Quote 하나만 기준으로 삼는다.
 */
@Entity
@Table(name = "quote_negotiations", uniqueConstraints = @UniqueConstraint(columnNames = "chat_room_id"))
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteNegotiation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// ChatRoom 1개당 1건 (1:1). Quote/ChatRoom처럼 서로 FK를 주고받지 않고
	// 이쪽에서만 chatRoomId를 갖는다 — 양방향 동기화 버그(updateQuoteId 누락 등)를
	// 애초에 만들지 않기 위함. 조회는 findByChatRoomId로 충분하다.
	@Column(nullable = false, unique = true)
	private Long chatRoomId;

	@Column(nullable = false)
	private Long buyerId;

	@Column(nullable = false)
	private Long sellerId;

	private Integer quantity;
	private Long unitPrice;
	private Long deliveryFee;
	private Long totalAmount;

	// 견적 항목(수량/단가/배송비)으로 표현하기 어려운 조건 — AI 요약 시 채워짐.
	// 사람이 직접 쓸 수도 있어야 하는지는 미정 (지금은 AI만 씀).
	@Column(columnDefinition = "TEXT")
	private String additionalNotes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NegotiationStatus status;

	// 요구사항 4: AI 요약은 협상(채팅방)당 딱 1회 — 클라이언트 신호를 믿지 않고
	// 서버가 이 플래그로 강제한다.
	@Column(nullable = false)
	private boolean aiSummaryUsed;

	private LocalDateTime aiSummaryUsedAt;

	// [장난질 방지 1단계] 동시 수정 충돌만 막는 낙관적 락. 상대가 반복적으로
	// 고치거나 악의적으로 여러 번 수정하는 것 자체를 막지는 못한다 —
	// 보안/어뷰징 방지 관련 추가 논의 필요.
	@Version
	private Long version;

	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime lockedAt;

	// [추가] 잠금 시 QuoteService.createFromLockedNegotiation()이 만든
	// 확정 Quote의 ID. LOCKED 이전에는 null이다. 이후 이 협상 건이
	// 어떤 Quote/Order로 이어졌는지 추적하는 용도.
	private Long resultingQuoteId;

	public enum NegotiationStatus {
		NEGOTIATING,    // 양쪽 다 자유 수정 가능
		AI_SUMMARIZED,  // AI 요약 완료 — 마지막 1회 수정만 가능
		LOCKED          // 최종 확정 — 이후 전부 불가
	}

	@Builder
	public QuoteNegotiation(Long chatRoomId, Long buyerId, Long sellerId,
	                        Integer quantity, Long unitPrice, Long deliveryFee) {
		if (chatRoomId == null) {
			throw new IllegalArgumentException("chatRoomId는 필수입니다.");
		}
		if (buyerId == null || sellerId == null) {
			throw new IllegalArgumentException("buyerId와 sellerId는 필수입니다.");
		}
		this.chatRoomId = chatRoomId;
		this.buyerId = buyerId;
		this.sellerId = sellerId;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.deliveryFee = deliveryFee;
		this.totalAmount = Quote.calculateTotalAmount(quantity, unitPrice, deliveryFee); // 기존 Quote 계산 로직 재사용
		this.status = NegotiationStatus.NEGOTIATING;
	}

	/**
	 * [수정] AI 요약 실제 호출(비용 발생) 이전에 먼저 부르는 검증.
	 * 기존에는 이 상태/사용여부 체크가 applyAiSummaryResult/markAiSummaryUsed
	 * 안에만 있어서, 이미 AI_SUMMARIZED인 상태에서 다시 호출해도 예외가 나기
	 * 전에 실제 AI 호출이 먼저 나가는 순서 버그가 있었다 (테스트로 확인됨).
	 */
	public void validateBeforeAiSummary(Long userId, Long sellerProfileId) {
		validateParticipant(userId, sellerProfileId);
		if (status != NegotiationStatus.NEGOTIATING) {
			throw new IllegalStateException("AI 요약은 협상 중 상태에서만 실행할 수 있습니다. 현재 상태: " + status);
		}
		if (this.aiSummaryUsed) {
			throw new IllegalStateException("AI 요약은 이미 1회 사용되었습니다.");
		}
	}

	/** AI 요약 결과를 반영한다. (validateBeforeAiSummary를 먼저 통과했다는 전제) */
	public void applyAiSummaryResult(Integer quantity, Long unitPrice, Long deliveryFee, String additionalNotes) {
		if (status != NegotiationStatus.NEGOTIATING) {
			throw new IllegalStateException("AI 요약은 협상 중 상태에서만 적용할 수 있습니다. 현재 상태: " + status);
		}
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.deliveryFee = deliveryFee;
		this.totalAmount = Quote.calculateTotalAmount(quantity, unitPrice, deliveryFee);
		this.additionalNotes = additionalNotes;
	}

	// ---------------------------------------------------------------
	// 자율 검증 (Quote와 동일한 패턴 유지)
	// ---------------------------------------------------------------

	public boolean isParticipant(Long userId, Long sellerProfileId) {
		if (userId == null) return false;
		if (userId.equals(buyerId)) return true;
		return sellerProfileId != null && sellerProfileId.equals(sellerId);
	}

	public void validateParticipant(Long userId, Long sellerProfileId) {
		if (!isParticipant(userId, sellerProfileId)) {
			throw new AccessDeniedException("해당 견적 협상에 접근 권한이 없습니다.");
		}
	}

	/** 요구사항 3: 협상 중(NEGOTIATING)에는 양쪽 다 자유 수정 가능 */
	public void validateFreeEdit(Long userId, Long sellerProfileId) {
		validateParticipant(userId, sellerProfileId);
		if (status != NegotiationStatus.NEGOTIATING) {
			throw new IllegalStateException("협상 중 상태에서만 자유롭게 수정할 수 있습니다. 현재 상태: " + status);
		}
	}

	/** 요구사항 5: AI 요약 이후 "마지막 한 번" 수정 — 테스트 결과에 따라 지금은
	 *  상태 기반 자유수정(횟수 제한 없음)으로 유지하기로 결정됨. */
	public void validateFinalEdit(Long userId, Long sellerProfileId) {
		validateParticipant(userId, sellerProfileId);
		if (status != NegotiationStatus.AI_SUMMARIZED) {
			throw new IllegalStateException("AI 요약 이후에만 마지막 수정이 가능합니다. 현재 상태: " + status);
		}
	}

	public void applyEdit(Integer quantity, Long unitPrice, Long deliveryFee) {
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.deliveryFee = deliveryFee;
		this.totalAmount = Quote.calculateTotalAmount(quantity, unitPrice, deliveryFee);
	}

	/** 요구사항 4: AI 요약 1회 제한 — 서버가 강제, 재호출 자체를 막는다 */
	public void markAiSummaryUsed() {
		if (this.aiSummaryUsed) {
			throw new IllegalStateException("AI 요약은 이미 1회 사용되었습니다.");
		}
		this.aiSummaryUsed = true;
		this.aiSummaryUsedAt = LocalDateTime.now();
		this.status = NegotiationStatus.AI_SUMMARIZED;
	}

	/** 요구사항 5: 최종 확인 → 잠금, 이후 수정 완전 불가 */
	public void lock(Long userId, Long sellerProfileId) {
		validateParticipant(userId, sellerProfileId);
		if (status != NegotiationStatus.AI_SUMMARIZED) {
			throw new IllegalStateException("AI 요약 완료 상태에서만 잠글 수 있습니다. 현재 상태: " + status);
		}
		this.status = NegotiationStatus.LOCKED;
		this.lockedAt = LocalDateTime.now();
	}
  
	/** lock() 직후, 확정 Quote가 발행되면 그 ID를 기록해둔다. */
	public void markQuoteCreated(Long quoteId) {
		this.resultingQuoteId = quoteId;
	}
}
