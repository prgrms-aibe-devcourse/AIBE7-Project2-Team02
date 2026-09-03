// domain/quote/entity/Quote.java
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

@Entity
@Table(name = "quotes", indexes = {
		@Index(name = "idx_quote_buyer_status_created", columnList = "buyer_id,status,created_at"),
		@Index(name = "idx_quote_seller_status_created", columnList = "seller_id,status,created_at")
})
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// [설계] 채팅 없이 견적만 존재할 수 있어야 하므로 nullable 유지.
	// 실제 DB 컬럼도 nullable = true (제약 추가하지 않음).
	private Long chatRoomId;

	private Long buyerId;
	private Long sellerId;

	@Enumerated(EnumType.STRING)
	private SenderRole senderRole;

	private Integer quantity;
	private Long unitPrice;
	private Long deliveryFee;
	private Long totalAmount;

	// [추가] QuoteNegotiation(협상형) 결과를 확정할 때, 수량/단가/배송비로
	// 표현 안 되는 조건(배송시간대, 알레르기 등)을 잃지 않기 위해 추가.
	// 기존 "제안형" 생성 경로(direct/to-buyer/to-seller 등)는 이 필드를 안 채워도 됨.
	@Column(columnDefinition = "TEXT")
	private String additionalNotes;

	@Enumerated(EnumType.STRING)
	private QuoteStatus status;

	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;

	public enum SenderRole {
		BUYER,
		SELLER
	}

	public enum QuoteStatus {
		SENT,
		ACCEPTED,
		REJECTED,
		WITHDRAWN
	}

	@Builder
	public Quote(Long chatRoomId, Long buyerId, Long sellerId, SenderRole senderRole,
	             Integer quantity, Long unitPrice, Long deliveryFee, Long totalAmount,
	             String additionalNotes, QuoteStatus status) {
		if (buyerId == null || sellerId == null) {
			throw new IllegalArgumentException("buyerId와 sellerId는 필수입니다.");
		}
		if (senderRole == null) {
			throw new IllegalArgumentException("senderRole은 필수입니다.");
		}
		this.chatRoomId = chatRoomId;
		this.buyerId = buyerId;
		this.sellerId = sellerId;
		this.senderRole = senderRole;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.deliveryFee = deliveryFee;
		this.totalAmount = totalAmount;
		this.additionalNotes = additionalNotes;
		this.status = status != null ? status : QuoteStatus.SENT;
	}

	/**
	 * 금액은 클라이언트가 보낸 값을 그대로 믿지 않고 서버가 계산한다.
	 * (기존 QuoteService.calculateTotalAmount와 동일한 규칙을 엔티티로 이동해
	 *  생성/수정 경로 어디서 호출해도 같은 계산 로직을 타도록 한다.)
	 */
	public static long calculateTotalAmount(Integer quantity, Long unitPrice, Long deliveryFee) {
		long qty = (quantity != null) ? quantity : 0;
		long price = (unitPrice != null) ? unitPrice : 0L;
		long fee = (deliveryFee != null) ? deliveryFee : 0L;
		return (qty * price) + fee;
	}

	// ---------------------------------------------------------------
	// 자율 검증 (객체지향 가드)
	// 서비스 레이어는 이 메서드들만 호출하고, 파라미터 출처(하드코딩 or JWT)는
	// 신경 쓰지 않는다. 인증이 붙으면 서비스에서 넘기는 userId 값만 바뀌면 된다.
	// ---------------------------------------------------------------

	/** 이 견적서의 구매자 또는 판매자인지 (조회 권한) */
	public boolean isParticipant(Long userId, Long sellerProfileId) {
		if (userId == null) return false;
		if (userId.equals(this.buyerId)) return true;
		return sellerProfileId != null && sellerProfileId.equals(this.sellerId);
	}

	public void validateParticipant(Long userId, Long sellerProfileId) {
		if (!isParticipant(userId, sellerProfileId)) {
			throw new AccessDeniedException("해당 견적서에 접근 권한이 없습니다.");
		}
	}

	public boolean isSender(Long userId, Long sellerProfileId) {
		if (userId == null) return false;
		if (this.senderRole == SenderRole.SELLER) {
			return sellerProfileId != null && sellerProfileId.equals(this.sellerId);
		}
		return userId.equals(this.buyerId);
	}

	public void validateSenderOnly(Long userId, Long sellerProfileId) {
		if (!isSender(userId, sellerProfileId)) {
			throw new AccessDeniedException("견적서를 보낸 당사자만 수행할 수 있습니다.");
		}
	}

	public boolean isCounterparty(Long userId, Long sellerProfileId) {
		if (userId == null) return false;
		if (this.senderRole == SenderRole.SELLER) {
			return userId.equals(this.buyerId);
		}
		return sellerProfileId != null && sellerProfileId.equals(this.sellerId);
	}

	public void validateCounterpartyOnly(Long userId, Long sellerProfileId) {
		if (!isCounterparty(userId, sellerProfileId)) {
			throw new AccessDeniedException("견적서를 받은 상대방만 수행할 수 있습니다.");
		}
	}

	// ---------------------------------------------------------------
	// 상태 전이 (기존 로직 유지)
	// ---------------------------------------------------------------

	public void updateStatus(QuoteStatus newStatus) {
		if (this.status != QuoteStatus.SENT) {
			throw new IllegalStateException("이미 처리 완료된 견적서의 상태는 변경할 수 없습니다. 현재 상태: " + this.status);
		}
		if (newStatus == QuoteStatus.SENT) {
			throw new IllegalArgumentException("동일한 SENT 상태로 전이할 수 없습니다.");
		}
		this.status = newStatus;
	}

	public void updateQuoteDetails(Integer quantity, Long unitPrice, Long deliveryFee, Long totalAmount) {
		if (this.status != QuoteStatus.SENT) {
			throw new IllegalStateException("SENT 상태의 견적서만 수정할 수 있습니다. 현재 상태: " + this.status);
		}
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.deliveryFee = deliveryFee;
		this.totalAmount = totalAmount;
	}
}
