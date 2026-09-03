package org.example.matcheat.domain.review.dto;

/**
 * 특정 결제 건에 리뷰를 작성할 수 있는 상태인지를 담는 응답 DTO이다.
 * 화면에서 "리뷰 작성" 버튼을 보여줄지 판단하는 데 쓰인다.
 *
 * @param eligible       지금 리뷰를 작성할 수 있는지 여부(결제 완료 && 아직 미작성)
 * @param paymentCompleted 결제가 완료(COMPLETED) 상태인지 여부
 * @param alreadyReviewed 이미 이 결제 건에 리뷰를 작성했는지 여부
 */
public record ReviewEligibilityDTO(boolean eligible, boolean paymentCompleted, boolean alreadyReviewed) {
}
