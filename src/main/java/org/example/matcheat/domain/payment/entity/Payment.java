package org.example.matcheat.domain.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * ACCEPTED 상태의 Quote 1건에 대한 결제 기록. 실제 PG 연동 전이라 항상
 * MockPaymentGatewayClient를 통해 처리된다. quantity/unitPrice/deliveryFee를
 * 결제 시점 값으로 스냅샷해둔다 — Quote는 ACCEPTED 이후 수정 API 자체가 없어
 * 사실상 불변이지만, 영수증이 Quote 조회에 의존하지 않게 하기 위함이다.
 */
@Entity
@Table(
		name = "payments",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_payments_quote_id",
				columnNames = "quote_id"
		)
)
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "quote_id", nullable = false)
	private Long quoteId;

	@Column(nullable = false)
	private Long buyerId;

	@Column(nullable = false)
	private Long sellerId;

	private Integer quantity;
	private Long unitPrice;
	private Long deliveryFee;

	@Column(nullable = false)
	private Long amount; // 결제 시점의 quote.totalAmount 스냅샷

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	private String pgTransactionId; // Mock PG가 발급한 가짜 거래번호
	private String failureReason;

	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime paidAt;

	public enum PaymentStatus {
		PENDING, COMPLETED, FAILED, CANCELLED
	}

	@Builder
	public Payment(Long quoteId, Long buyerId, Long sellerId,
	               Integer quantity, Long unitPrice, Long deliveryFee, Long amount) {
		if (quoteId == null || buyerId == null || sellerId == null) {
			throw new IllegalArgumentException("quoteId, buyerId, sellerId는 필수입니다.");
		}
		this.quoteId = quoteId;
		this.buyerId = buyerId;
		this.sellerId = sellerId;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.deliveryFee = deliveryFee;
		this.amount = amount;
		this.status = PaymentStatus.PENDING;
	}

	public boolean isPayer(Long userId) {
		return userId != null && userId.equals(buyerId);
	}

	public void validatePayer(Long userId) {
		if (!isPayer(userId)) {
			throw new IllegalArgumentException("결제는 구매자 본인만 할 수 있습니다.");
		}
	}

	public boolean isParticipant(Long userId) {
		return userId != null && (userId.equals(buyerId) || userId.equals(sellerId));
	}

	public void validateParticipant(Long userId) {
		if (!isParticipant(userId)) {
			throw new IllegalArgumentException("해당 결제 건에 접근 권한이 없습니다.");
		}
	}

	public void markCompleted(String pgTransactionId) {
		if (this.status == PaymentStatus.COMPLETED) {
			throw new IllegalStateException("이미 완료된 결제입니다.");
		}
		this.status = PaymentStatus.COMPLETED;
		this.pgTransactionId = pgTransactionId;
		this.paidAt = LocalDateTime.now();
	}

	public void markFailed(String reason) {
		this.status = PaymentStatus.FAILED;
		this.failureReason = reason;
	}
}
