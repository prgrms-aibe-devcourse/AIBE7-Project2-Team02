package org.example.matcheat.domain.product.dto;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
/**
 * 판매 조건 수정 요청에서 사용하는 입력 DTO이다. null이 아닌 필드만 반영된다.
 */
public class ProductUpdateDTO {

    /**
     * 상품/메뉴명
     */
    private String productName;

    /**
     * 최소 수주(주문) 수량
     */
    @Positive
    private Integer minHeadcount;

    /**
     * 최대 수주(주문) 수량
     */
    @Positive
    private Integer maxHeadcount;

    /**
     * 1인분 가격
     */
    @Positive
    private Integer servingPrice;

    /**
     * 최대 배달(배송) 반경(km)
     */
    @Positive
    private Double deliveryRadiusKm;

    /**
     * 가게 도로명 주소. 값이 바뀌면 서버가 다시 지오코딩한다
     */
    private String storeAddress;

    /**
     * 실제 가게 위치를 확인하기 위한 상세 주소
     */
    private String storeAddressDetail;

    /**
     * 상품(음식) 카테고리
     */
    private String category;

    /**
     * 상품(음식) 설명
     */
    private String description;

    /**
     * 가게 정기 휴무 요일
     */
    private DayOfWeek dayOfWeek;

    /**
     * 가게 특정 불가 날짜 목록
     */
    private List<LocalDate> unavailableDates;
}
