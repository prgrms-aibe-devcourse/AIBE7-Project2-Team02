package org.example.matcheat.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.security.access.AccessDeniedException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "seller_conditions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * 판매 조건 정보를 DB에 저장하는 JPA 엔티티이다.
 */
public class ProductEntity {

    // Id
    @Id // PK
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 최소 수주(주문) 수량
     */
    @Column(name = "min_headcount", nullable = false)
    private Integer minHeadcount;

    /**
     * 최대 수주(주문) 수량
     */
    @Column(name = "max_headcount", nullable = false)
    private Integer maxHeadcount;

    /**
     * 1인분 가격
     */
    @Column(name = "serving_price", nullable = false)
    private Integer servingPrice;

    /**
     * 최대 배달(배송) 반경
     */
    @Column(name = "delivery_radius_km", nullable = false)
    private Double deliveryRadiusKm;

    /**
     * 거리 계산에 사용하는 가게 도로명 주소
     */
    @Column(name = "store_address", nullable = false)
    private String storeAddress;

    /**
     * 실제 가게 위치를 확인하기 위한 상세 주소.
     * 기존 상품 데이터와의 호환을 위해 nullable로 유지한다.
     */
    @Column(name = "store_address_detail")
    private String storeAddressDetail;

    /**
     * 상품/메뉴명
     */
    @Column(name = "product_name", nullable = false)
    private String productName;

    /**
     * 가게 위도
     */
    @Column(name = "latitude", nullable = true)
    private Double latitude;

    /**
     * 가게 경도
     */
    @Column(name = "longitude", nullable = true)
    private Double longitude;

    /**
     * 상품(음식) 카테고리
     */
    @Column(name = "category", nullable = false)
    private String category;

    /**
     * 상품(음식) 설명
     */
    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    /**
     * 가게 정기 휴무 요일
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = true)
    private DayOfWeek dayOfWeek;

    /**
     * 가게 평점
     */
    @Column(name = "rating_avg", nullable = true)
    @ColumnDefault("0.0")
    private Double ratingAvg;

    /**
     * 가게 특정 불가 날짜
     */
    @Convert(converter = LocalDateListConverter.class)
    @Column(name = "unavailable_dates", nullable = true, columnDefinition = "TEXT")
    private List<LocalDate> unavailableDates;

    /**
     * 가게 또는 상품(음식) 이미지
     */
    @Column(name = "image_url", nullable = true, columnDefinition = "TEXT")
    private String imageUrl;


    /**
     * 이 판매 조건을 등록한 사용자(판매자)의 계정 ID
     */
    @Column(name = "owner_account_id")
    private Long ownerAccountId;

    /**
     * 상품 Soft Delete 옵션
     */
    @Column(name = "hidden", nullable = false)
    @ColumnDefault("false")
    private boolean hidden;

    /**
     * 데이터 수정 일자 등록
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 정적 팩토리 메소드를 통해서만 생성되도록 하는 생성자이다.
     */
    private ProductEntity(
            String productName,
            Integer minHeadcount,
            Integer maxHeadcount,
            Integer servingPrice,
            Double deliveryRadiusKm,
            String storeAddress,
            String storeAddressDetail,
            Double latitude,
            Double longitude,
            String category,
            String description,
            DayOfWeek dayOfWeek,
            List<LocalDate> unavailableDates,
            String imageUrl,
            Long ownerAccountId
    ) {
        validateHeadcountRange(minHeadcount, maxHeadcount);

        this.minHeadcount = minHeadcount;
        this.maxHeadcount = maxHeadcount;
        this.servingPrice = servingPrice;
        this.deliveryRadiusKm = deliveryRadiusKm;
        this.storeAddress = storeAddress;
        this.storeAddressDetail =
                storeAddressDetail;
        this.productName = productName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
        this.description = description;
        this.dayOfWeek = dayOfWeek;
        this.unavailableDates = normalizeUnavailableDates(unavailableDates);
        this.imageUrl = imageUrl;
        this.ownerAccountId = ownerAccountId;
        this.hidden = false;
    }

    /**
     * 새 판매 조건 엔티티를 생성한다.
     */
    public static ProductEntity create(
            String productName,
            Integer minHeadcount,
            Integer maxHeadcount,
            Integer servingPrice,
            Double deliveryRadiusKm,
            String storeAddress,
            String storeAddressDetail,
            Double latitude,
            Double longitude,
            String category,
            String description,
            DayOfWeek dayOfWeek,
            List<LocalDate> unavailableDates,
            String imageUrl,
            Long ownerAccountId
    ) {
        return new ProductEntity(
                productName,
                minHeadcount,
                maxHeadcount,
                servingPrice,
                deliveryRadiusKm,
                storeAddress,
                storeAddressDetail,
                latitude,
                longitude,
                category,
                description,
                dayOfWeek,
                unavailableDates,
                imageUrl,
                ownerAccountId
        );
    }

    /**
     * null 이 아닌 값만 반영해 판매 조건을 부분 수정한다.
     * 관리자가 아니라면 요청자가 이 판매 조건의 소유자인지 먼저 검증한다.
     */
    public void update(
            Long requesterAccountId,
            boolean requesterIsAdmin,
            String productName,
            Integer minHeadcount,
            Integer maxHeadcount,
            Integer servingPrice,
            Double deliveryRadiusKm,
            String storeAddress,
            String storeAddressDetail,
            Double latitude,
            Double longitude,
            String category,
            String description,
            DayOfWeek dayOfWeek,
            List<LocalDate> unavailableDates,
            String imageUrl
    ) {
        if (!requesterIsAdmin) {
            verifyOwner(requesterAccountId);
        }

        if (minHeadcount != null) {
            this.minHeadcount = minHeadcount;
        }

        if (maxHeadcount != null) {
            this.maxHeadcount = maxHeadcount;
        }

        validateHeadcountRange(this.minHeadcount, this.maxHeadcount);

        if (servingPrice != null) {
            this.servingPrice = servingPrice;
        }

        if (deliveryRadiusKm != null) {
            this.deliveryRadiusKm = deliveryRadiusKm;
        }

        if (storeAddress != null) {
            this.storeAddress = storeAddress;
        }

        if (storeAddressDetail != null) {
            this.storeAddressDetail =
                    storeAddressDetail;
        }

        if (productName != null) {
            this.productName = productName;
        }

        if (latitude != null) {
            this.latitude = latitude;
        }

        if (longitude != null) {
            this.longitude = longitude;
        }

        if (category != null) {
            this.category = category;
        }

        if (description != null) {
            this.description = description;
        }

        if (dayOfWeek != null) {
            this.dayOfWeek = dayOfWeek;
        }

        if (unavailableDates != null) {
            this.unavailableDates = normalizeUnavailableDates(unavailableDates);
        }

        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
    }

    /**
     * 판매 조건을 소프트 삭제 상태로 바꾼다.
     * 관리자가 아니라면 요청자가 이 판매 조건의 소유자인지 먼저 검증한다.
     */
    public void softDelete(Long requesterAccountId, boolean requesterIsAdmin) {
        if (!requesterIsAdmin) {
            verifyOwner(requesterAccountId);
        }

        this.hidden = true;
    }

    /**
     * 이 상품에 달린 리뷰들을 기준으로 재계산된 평점으로 덮어쓴다.
     * domain/review가 리뷰를 새로 저장할 때마다 호출하며, 증분 계산이 아니라
     * 매번 그 상품의 리뷰 전체를 다시 평균 낸 값을 그대로 반영한다.
     */
    public void updateRatingAvg(Double ratingAvg) {
        this.ratingAvg = ratingAvg;
    }

    /**
     * 요청자가 이 판매 조건의 소유자인지 검증한다.
     */
    private void verifyOwner(Long requesterAccountId) {
        if (requesterAccountId == null || !requesterAccountId.equals(this.ownerAccountId)) {
            throw new AccessDeniedException("본인이 등록한 판매 조건만 수정 또는 삭제할 수 있습니다.");
        }
    }

    /**
     * 최소 수주 수량이 최대 수주 수량보다 크지 않은지 검증한다.
     */
    private static void validateHeadcountRange(Integer minHeadcount, Integer maxHeadcount) {
        if (minHeadcount != null && maxHeadcount != null && minHeadcount > maxHeadcount) {
            throw new IllegalArgumentException(
                    "최소 수주 수량(%d)은 최대 수주 수량(%d)보다 클 수 없습니다.".formatted(minHeadcount, maxHeadcount)
            );
        }
    }

    /**
     * 엔티티가 처음 저장될 때 수정 시각을 현재 시각으로 초기화한다.
     */
    @PrePersist
    protected void onCreate() {
        this.hidden = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 엔티티가 수정될 때 수정 시각을 현재 시각으로 갱신한다.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 특정 불가 날짜 목록을 null-safe한 불변 리스트로 정규화한다.
     * null 또는 빈 값이면 빈 리스트를, 그렇지 않으면 새 ArrayList로 복사해 반환한다.
     */
    private List<LocalDate> normalizeUnavailableDates(List<LocalDate> unavailableDates) {
        if (unavailableDates == null || unavailableDates.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(unavailableDates);
    }
}
