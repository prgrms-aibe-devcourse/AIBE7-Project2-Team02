package org.example.matcheat.domain.estimate.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.matcheat.domain.estimate.enums.EstimateStatus;
import org.example.matcheat.domain.order.enums.BudgetType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "estimates")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * 견적(Estimate) 정보를 DB에 저장하는 JPA 엔티티이다.
 * request_id는 OrderRequest를 가리키는 FK가 아니라 요청자(구매자) 본인의 계정 ID이고,
 * seller_id는 계정 ID가 아니라 seller_profiles 테이블의 PK이다.
 */
public class EstimateEntity {

    /**
     * 견적 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 견적 생성 일시
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 구매자가 직접 작성한 상세 설명
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 요청자(구매자) 본인의 계정 ID. OrderRequest를 가리키는 FK가 아니다
     */
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /**
     * 견적을 받는 판매자의 seller_profiles PK (계정 ID가 아니다)
     */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /**
     * 등록 상품을 근거로 요청하면 상품 ID가 들어가고, 직접 입력이면 null이다.
     */
    @Column(name = "product_id")
    private Long productId;

    /**
     * 예산 금액
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal budget;

    /**
     * 예산 유형(1인당/총액)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "budget_type", nullable = false)
    private BudgetType budgetType;

    /**
     * 상품/항목명
     */
    @Column(name = "item_name", nullable = false)
    private String itemName;

    /**
     * 주문 수량(인분 수). budgetType이 TOTAL일 때 1인당 금액을 계산하는 데 쓰인다.
     */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * 행사/이용 일시
     */
    @Column(name = "event_date_time", nullable = false)
    private LocalDateTime eventDateTime;

    /**
     * 견적 이미지 URL
     */
    @Column(name = "estimate_image", columnDefinition = "TEXT")
    private String estimateImage;

    /**
     * 배송(행사) 주소. 카카오 지오코딩으로 위경도를 함께 저장한다.
     */
    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    /**
     * 실제 배송 위치를 확인하기 위한 상세 주소이다.
     * 기존 견적 데이터와의 호환을 위해 DB 컬럼 자체는 nullable로 유지한다.
     */
    @Column(name = "delivery_address_detail")
    private String deliveryAddressDetail;

    /**
     * 배송(행사) 주소를 지오코딩한 위도
     */
    @Column(nullable = true)
    private Double latitude;

    /**
     * 배송(행사) 주소를 지오코딩한 경도
     */
    @Column(nullable = true)
    private Double longitude;

    /**
     * 견적 진행 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstimateStatus status;

    /**
     * 견적 요청을 생성하는 내부 생성자이다. 필수값 검증 후 상태를 REQUESTED로 초기화한다.
     */
    private EstimateEntity(
            String description,
            Long requestId,
            Long sellerId,
            Long productId,
            BigDecimal budget,
            BudgetType budgetType,
            String itemName,
            Integer quantity,
            LocalDateTime eventDateTime,
            String estimateImage,
            String deliveryAddress,
            String deliveryAddressDetail,
            Double latitude,
            Double longitude
    ) {
        validateRequiredFields(
                requestId,
                budget,
                budgetType,
                itemName,
                quantity,
                eventDateTime,
                sellerId,
                deliveryAddress,
                deliveryAddressDetail
        );
        this.description = description;
        this.requestId = requestId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.budget = budget;
        this.budgetType = budgetType;
        this.itemName = itemName;
        this.quantity = quantity;
        this.eventDateTime = eventDateTime;
        this.estimateImage = estimateImage;
        this.deliveryAddress = deliveryAddress;
        this.deliveryAddressDetail = deliveryAddressDetail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = EstimateStatus.REQUESTED;
    }

    /**
     * 새로운 견적 요청을 생성한다. 구매자가 직접 입력한 값만으로 채워지며,
     * requestId에는 요청자(구매자) 본인의 계정 ID가 그대로 들어간다.
     */
    public static EstimateEntity create(
            String description,
            Long requestId,
            Long sellerId,
            Long productId,
            BigDecimal budget,
            BudgetType budgetType,
            String itemName,
            Integer quantity,
            LocalDateTime eventDateTime,
            String estimateImage,
            String deliveryAddress,
            String deliveryAddressDetail,
            Double latitude,
            Double longitude
    ) {
        return new EstimateEntity(
                description,
                requestId,
                sellerId,
                productId,
                budget,
                budgetType,
                itemName,
                quantity,
                eventDateTime,
                estimateImage,
                deliveryAddress,
                deliveryAddressDetail,
                latitude,
                longitude
        );
    }

    /**
     * 견적의 필수 필드(requestId, budget, budgetType, itemName, quantity, eventDateTime, sellerId, deliveryAddress)가
     * 모두 채워져 있는지 검증한다. 하나라도 비어 있으면 예외를 던진다.
     */
    private static void validateRequiredFields(
            Long requestId,
            BigDecimal budget,
            BudgetType budgetType,
            String itemName,
            Integer quantity,
            LocalDateTime eventDateTime,
            Long sellerId,
            String deliveryAddress,
            String deliveryAddressDetail
    ) {
        if (requestId == null) {
            throw new IllegalArgumentException("requestId는 필수입니다.");
        }
        if (budget == null) {
            throw new IllegalArgumentException("budget는 필수입니다.");
        }
        if (budgetType == null) {
            throw new IllegalArgumentException("budgetType은 필수입니다.");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName은 필수입니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity는 0보다 큰 값이 필수입니다.");
        }
        if (eventDateTime == null) {
            throw new IllegalArgumentException("eventDateTime은 필수입니다.");
        }
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId는 필수입니다.");
        }
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new IllegalArgumentException("deliveryAddress는 필수입니다.");
        }
        if (deliveryAddressDetail == null || deliveryAddressDetail.isBlank()) {
            throw new IllegalArgumentException(
                    "deliveryAddressDetail은 필수입니다."
            );
        }
    }
}
