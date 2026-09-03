package org.example.matcheat.domain.review.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.review.entity.ReviewEntity;
import org.example.matcheat.domain.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 리뷰(Review)의 순수 CRUD만 담당하는 서비스이다. Payment/Quote/ChatRoom/Proposal 같은
 * 다른 도메인은 전혀 모르며, 이미 검증과 상품명 해석이 끝난 값만 받아서 저장/조회한다.
 * 권한 검증과 상품명 해석은 {@link ReviewAccessService}가 담당한다.
 */
public class ReviewService {

    private final ReviewRepository reviewRepository;

    /**
     * 검증이 끝난 리뷰 정보를 그대로 저장한다.
     */
    @Transactional
    public ReviewEntity create(
            Long paymentId,
            Long buyerId,
            Long sellerId,
            Long productId,
            Integer rating,
            String content,
            String imageUrl
    ) {
        ReviewEntity review = ReviewEntity.create(
                paymentId, buyerId, sellerId, productId, rating, content, imageUrl
        );

        return reviewRepository.save(review);
    }

    /**
     * 리뷰 ID로 단건을 조회한다. 존재하지 않으면 예외를 던진다.
     */
    @Transactional(readOnly = true)
    public ReviewEntity findById(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리뷰입니다. id=" + id));
    }

    /**
     * 특정 판매자가 받은 리뷰 목록을 최신순으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<ReviewEntity> findBySellerId(Long sellerId) {
        return reviewRepository.findAllBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    /**
     * 이 결제 건에 이미 리뷰가 작성됐는지 확인한다.
     */
    @Transactional(readOnly = true)
    public boolean existsByPaymentId(Long paymentId) {
        return reviewRepository.existsByPaymentId(paymentId);
    }

    /**
     * 특정 상품에 달린 별점 목록(평점 재계산용)을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<Integer> findRatingsByProductId(Long productId) {
        return reviewRepository.findAllByProductId(productId).stream()
                .map(ReviewEntity::getRating)
                .toList();
    }

    /**
     * 주어진 결제 ID 목록 중, 이미 리뷰가 작성된 결제 ID만 골라 반환한다.
     */
    @Transactional(readOnly = true)
    public List<Long> findExistingPaymentIds(List<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return List.of();
        }

        return reviewRepository.findPaymentIdsIn(paymentIds);
    }
}
