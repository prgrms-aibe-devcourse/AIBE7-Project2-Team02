package org.example.matcheat.domain.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * 결제가 완료(Payment.COMPLETED)된 거래 1건에 대해 구매자가 남기는 판매자 리뷰이다.
 * payment_id는 유니크 — 결제 1건당 리뷰 1개만 작성할 수 있다.
 * seller_id는 계정 ID가 아니라 seller_profiles PK(Payment.sellerId를 그대로 저장한 값)이고,
 * product_id는 이 거래가 등록 상품에서 시작된 경우에만 채워지며(Proposal.productId 경유),
 * 채팅/직접입력 제안으로 성사된 거래는 null일 수 있다 — 그 경우 상품 평점 갱신 대상에서 제외된다.
 */
public class ReviewEntity {

    /** 리뷰 PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 근거가 된 결제 ID. 결제 1건당 리뷰 1개만 허용(유니크) */
    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    /** 리뷰 작성자(구매자)의 계정 ID */
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /** 리뷰 대상 판매자의 seller_profiles PK */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** 근거가 된 상품 ID. 등록 상품 기반 거래가 아니면 null */
    @Column(name = "product_id")
    private Long productId;

    /** 별점(1~5) */
    @Column(nullable = false)
    private Integer rating;

    /** 리뷰 내용 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 리뷰 이미지 URL(선택) */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** 리뷰 작성 일시 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ReviewEntity(
            Long paymentId,
            Long buyerId,
            Long sellerId,
            Long productId,
            Integer rating,
            String content,
            String imageUrl
    ) {
        validate(paymentId, buyerId, sellerId, rating, content);

        this.paymentId = paymentId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.rating = rating;
        this.content = content;
        this.imageUrl = imageUrl;
    }

    /**
     * 새로운 리뷰를 생성한다. 결제 완료 여부, 중복 작성 여부, 본인 확인은
     * ReviewAccessService가 먼저 검증한 뒤 호출한다.
     */
    public static ReviewEntity create(
            Long paymentId,
            Long buyerId,
            Long sellerId,
            Long productId,
            Integer rating,
            String content,
            String imageUrl
    ) {
        return new ReviewEntity(paymentId, buyerId, sellerId, productId, rating, content, imageUrl);
    }

    /**
     * 필수값과 별점 범위(1~5)를 검증한다.
     */
    private static void validate(Long paymentId, Long buyerId, Long sellerId, Integer rating, String content) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId는 필수입니다.");
        }
        if (buyerId == null) {
            throw new IllegalArgumentException("buyerId는 필수입니다.");
        }
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId는 필수입니다.");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating은 1~5 사이여야 합니다.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content는 필수입니다.");
        }
    }
}
