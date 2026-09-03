package org.example.matcheat.domain.product.dto;

import lombok.Getter;
import org.example.matcheat.domain.product.entity.ProductEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
/**
 * 판매 조건 조회 응답에 사용하는 DTO이다.
 */
public class ProductResponseDTO {
    /**
     * 판매 조건 ID
     */
    private final Long id;
    /**
     * 상품/메뉴명
     */
    private final String productName;
    /**
     * 최소 수주(주문) 수량
     */
    private final Integer minHeadcount;
    /**
     * 최대 수주(주문) 수량
     */
    private final Integer maxHeadcount;
    /**
     * 1인분 가격
     */
    private final Integer servingPrice;
    /**
     * 최대 배달(배송) 반경(km)
     */
    private final Double deliveryRadiusKm;
    /**
     * 가게 도로명 주소
     */
    private final String storeAddress;
    /**
     * 실제 가게 위치를 확인하기 위한 상세 주소
     */
    private final String storeAddressDetail;
    /**
     * 가게 위도
     */
    private final Double latitude;
    /**
     * 가게 경도
     */
    private final Double longitude;
    /**
     * 상품(음식) 카테고리
     */
    private final String category;
    /**
     * 상품(음식) 설명
     */
    private final String description;
    /**
     * 가게 정기 휴무 요일
     */
    private final DayOfWeek dayOfWeek;
    /**
     * 가게 평점
     */
    private final Double ratingAvg;
    /**
     * 가게 특정 불가 날짜 목록
     */
    private final List<LocalDate> unavailableDates;
    /**
     * 가게 또는 상품(음식) 이미지 URL
     */
    private final String imageUrl;
    /**
     * 숨김(소프트 삭제) 여부
     */
    private final boolean hidden;
    /**
     * 최종 수정 일시
     */
    private final LocalDateTime updatedAt;
    /**
     * 조회하는 사람이 이 판매 조건의 소유자인지 여부. 실제 계정 ID는 노출하지 않는다.
     */
    private final boolean owner;

    /**
     * 엔티티 값을 응답 DTO로 옮겨 담는 생성자이다.
     */
    private ProductResponseDTO(ProductEntity product, Long viewerAccountId) {
        this.id = product.getId();
        this.productName = product.getProductName();
        this.minHeadcount = product.getMinHeadcount();
        this.maxHeadcount = product.getMaxHeadcount();
        this.servingPrice = product.getServingPrice();
        this.deliveryRadiusKm = product.getDeliveryRadiusKm();
        this.storeAddress = product.getStoreAddress();
        this.storeAddressDetail =
                product.getStoreAddressDetail();
        this.latitude = product.getLatitude();
        this.longitude = product.getLongitude();
        this.category = product.getCategory();
        this.description = product.getDescription();
        this.dayOfWeek = product.getDayOfWeek();
        this.ratingAvg = product.getRatingAvg();
        this.unavailableDates = List.copyOf(
                product.getUnavailableDates() == null ? List.of() : product.getUnavailableDates()
        );
        this.imageUrl = product.getImageUrl();
        this.hidden = product.isHidden();
        this.updatedAt = product.getUpdatedAt();
        this.owner = viewerAccountId != null && viewerAccountId.equals(product.getOwnerAccountId());
    }

    /**
     * 엔티티를 응답 DTO로 변환한다. viewerAccountId는 현재 조회하는 사용자의 계정 ID(비로그인이면 null)로,
     * 응답에 그대로 노출되지 않고 owner 여부를 계산하는 데만 쓰인다.
     */
    public static ProductResponseDTO from(ProductEntity product, Long viewerAccountId) {
        return new ProductResponseDTO(product, viewerAccountId);
    }
}
