package org.example.matcheat.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import org.example.matcheat.domain.review.entity.ReviewEntity;

import java.time.LocalDateTime;

@Getter
@Builder
/**
 * 리뷰 조회 응답에 사용하는 DTO이다.
 * buyerId/sellerId(seller_profiles PK)는 내부 검증·집계 용도로만 쓰고 JSON에는 노출하지 않는다
 * — Product/Estimate와 같은 이유이다. 대신 화면에 보여줄 itemName(상품명 또는 대체 문구)을 담는다.
 */
public class ReviewResponseDTO {

    /** 리뷰 ID */
    private Long id;

    @JsonIgnore
    private Long paymentId;

    @JsonIgnore
    private Long buyerId;

    @JsonIgnore
    private Long sellerId;

    @JsonIgnore
    private Long productId;

    /** 화면에 표시할 상품명. 등록 상품과 연결이 안 되면 대체 문구가 담긴다 */
    private String itemName;

    /** 별점(1~5) */
    private Integer rating;

    /** 리뷰 내용 */
    private String content;

    /** 리뷰 이미지 URL */
    private String imageUrl;

    /** 리뷰 작성 일시 */
    private LocalDateTime createdAt;

    /** 조회하는 사람이 이 리뷰의 작성자(구매자) 본인인지 여부 */
    private boolean owner;

    /**
     * 엔티티와 화면 표시용 상품명을 응답 DTO로 변환한다. owner 여부는 아직 모르는 상태(false)이며,
     * 필요하면 {@link #withOwner(boolean)}로 나중에 채운다.
     */
    public static ReviewResponseDTO from(ReviewEntity entity, String itemName) {
        return ReviewResponseDTO.builder()
                .id(entity.getId())
                .paymentId(entity.getPaymentId())
                .buyerId(entity.getBuyerId())
                .sellerId(entity.getSellerId())
                .productId(entity.getProductId())
                .itemName(itemName)
                .rating(entity.getRating())
                .content(entity.getContent())
                .imageUrl(entity.getImageUrl())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * 조회하는 사람이 작성자 본인인지를 채운 뒤 자기 자신을 반환한다.
     */
    public ReviewResponseDTO withOwner(boolean isOwner) {
        this.owner = isOwner;
        return this;
    }
}
