package org.example.matcheat.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.matcheat.domain.order.enums.BudgetType;
import org.example.matcheat.domain.order.enums.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderRequest {

    // Id
    @Id // PK 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB에서 자동 증가
    private Long id;

    /**
     * 주문 요청을 등록한 구매자의 사용자 ID
     */
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /**
     * 판매자가 주문 요청을 빠르게 구분할 수 있는 제목
     */
    @Column(length = 100)
    private String title;

    /**
     * 주문 목적이나 추가 요청사항을 설명하는 상세 내용
     */
    @Column(length = 1000)
    private String description;

    /**
     * 구매자가 주문 이해를 돕기 위해 등록한 참고 이미지
     */
    @Column(name = "reference_image_url", columnDefinition = "TEXT")
    private String referenceImageUrl;

    // 행사/배송 예정 일시
    @Column(nullable = false) // DB 필수값으로 지정
    private LocalDateTime eventDateTime;

    // 필요한 주문 수량 또는 인원 수
    @Column(nullable = false)
    private Integer quantity;

    // 예산이 1인당 기준인지, 총액 기준인지 구분
    @Enumerated(EnumType.STRING) // enum을 문자열로 저장
    @Column(nullable = false)
    private BudgetType budgetType;

    // 실제 예산 금액
    @Column(nullable = false)
    private BigDecimal budget;

    // 원하는 음식 카테고리
    @Column(nullable = false)
    private String category;

    // 거리 계산에 사용하는 배송지 도로명 주소
    @Column(nullable = false)
    private String deliveryAddress;

    /**
     * 실제 배송 위치를 확인하기 위한 상세 주소.
     * 기존 주문 데이터와의 호환을 위해 DB 컬럼은 nullable로 유지한다.
     */
    @Column(name = "delivery_address_detail")
    private String deliveryAddressDetail;

    // 배송지 위도
    @Column(nullable = false)
    private Double latitude;

    // 배송지 경도
    @Column(nullable = false)
    private Double longitude;

    // 주문 요청의 현재 진행 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /// Setter를 쓰지 않는 이유는 Setter는 객체 상태를 아무 곳에서나 변경 가능하기 때문
    /// 생성자는 생성 규칙을 강제하고 상태를 보호하기 쉬움
    private OrderRequest(
            Long buyerId,
            String title,
            String description,
            LocalDateTime eventDateTime,
            Integer quantity,
            BudgetType budgetType,
            BigDecimal budget,
            String category,
            String deliveryAddress,
            String deliveryAddressDetail,
            Double latitude,
            Double longitude,
            String referenceImageUrl
    ) {
        this.buyerId = buyerId;
        this.title = title;
        this.description = description;
        this.eventDateTime = eventDateTime;
        this.quantity = quantity;
        this.budgetType = budgetType;
        this.budget = budget;
        this.category = category;
        this.deliveryAddress = deliveryAddress;
        this.deliveryAddressDetail =
                deliveryAddressDetail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.referenceImageUrl = referenceImageUrl;
        this.status = RequestStatus.MATCHING;
    }

    /**
     * 새로운 주문 요청 Entity를 생성
     */
    public static OrderRequest create(
            Long buyerId,
            String title,
            String description,
            LocalDateTime eventDateTime,
            Integer quantity,
            BudgetType budgetType,
            BigDecimal budget,
            String category,
            String deliveryAddress,
            String deliveryAddressDetail,
            Double latitude,
            Double longitude
    ) {
        return
                new OrderRequest(
                        buyerId,
                        title,
                        description,
                        eventDateTime,
                        quantity,
                        budgetType,
                        budget,
                        category,
                        deliveryAddress,
                        deliveryAddressDetail,
                        latitude,
                        longitude,
                        null
                );
    }

    /**
     * 참고 이미지를 포함한 새로운 주문 요청 Entity를 생성
     */
    public static OrderRequest create(
            Long buyerId,
            String title,
            String description,
            LocalDateTime eventDateTime,
            Integer quantity,
            BudgetType budgetType,
            BigDecimal budget,
            String category,
            String deliveryAddress,
            String deliveryAddressDetail,
            Double latitude,
            Double longitude,
            String referenceImageUrl
    ) {
        return new OrderRequest(
                buyerId,
                title,
                description,
                eventDateTime,
                quantity,
                budgetType,
                budget,
                category,
                deliveryAddress,
                deliveryAddressDetail,
                latitude,
                longitude,
                referenceImageUrl
        );
    }

    /**
     * 전달된 값이 있는 항목만 주문 요청 정보를 수정
     */
    public void update(
            String title,
            String description,
            LocalDateTime eventDateTime,
            Integer quantity,
            BudgetType budgetType,
            BigDecimal budget,
            String category,
            String deliveryAddress,
            String deliveryAddressDetail,
            Double latitude,
            Double longitude
    ) {
        update(
                title,
                description,
                eventDateTime,
                quantity,
                budgetType,
                budget,
                category,
                deliveryAddress,
                deliveryAddressDetail,
                latitude,
                longitude,
                this.referenceImageUrl
        );
    }

    /**
     * 참고 이미지를 포함해 전달된 주문 요청 정보를 수정
     */
    public void update(
            String title,
            String description,
            LocalDateTime eventDateTime,
            Integer quantity,
            BudgetType budgetType,
            BigDecimal budget,
            String category,
            String deliveryAddress,
            String deliveryAddressDetail,
            Double latitude,
            Double longitude,
            String referenceImageUrl
    ) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }

        if (description != null) {
            this.description = description;
        }

        if (eventDateTime != null) {
            this.eventDateTime = eventDateTime;
        }

        if (quantity != null) {
            this.quantity = quantity;
        }

        if (budgetType != null) {
            this.budgetType = budgetType;
        }

        if (budget != null) {
            this.budget = budget;
        }

        if (category != null && !category.isBlank()) {
            this.category = category;
        }

        if (deliveryAddress != null && !deliveryAddress.isBlank()) {
            this.deliveryAddress = deliveryAddress;
        }

        if (deliveryAddressDetail != null
                && !deliveryAddressDetail.isBlank()) {
            this.deliveryAddressDetail =
                    deliveryAddressDetail;
        }

        if (latitude != null) {
            this.latitude = latitude;
        }

        if (longitude != null) {
            this.longitude = longitude;
        }

        this.referenceImageUrl = referenceImageUrl;
    }


    /**
     * MATCHING 상태의 주문 요청을 취소 상태로 변경
     */
    public void cancel() {
        if (this.status != RequestStatus.MATCHING) {
            throw new IllegalStateException(
                    "MATCHING 상태의 주문 요청만 취소할 수 있습니다."
            );
        }
        this.status = RequestStatus.CANCELLED;
    }
}
