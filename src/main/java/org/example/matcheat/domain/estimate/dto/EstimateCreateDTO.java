package org.example.matcheat.domain.estimate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.matcheat.domain.order.enums.BudgetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
/**
 * 견적 생성 요청에서 사용하는 입력 DTO이다.
 * sellerId는 클라이언트가 직접 보내지 않는다 — productId만으로 서버가 판매자를 알아낸다.
 */
public class EstimateCreateDTO {

    /**
     * 구매자가 직접 작성하는 상세 설명(선택)
     */
    private String description;

    /**
     * 견적을 요청할 대상 상품 ID (서버가 이 값으로 판매자를 알아낸다)
     */
    @NotNull
    private Long productId;

    /**
     * 예산 금액
     */
    @NotNull
    @Positive(message = "budget는 0보다 커야 합니다.")
    private BigDecimal budget;

    /**
     * 예산 유형(1인당/총액)
     */
    @NotNull
    private BudgetType budgetType;

    /**
     * 상품/항목명
     */
    @NotBlank
    private String itemName;

    /**
     * 주문 수량(인분 수)
     */
    @NotNull
    @Positive(message = "quantity는 0보다 커야 합니다.")
    private Integer quantity;

    /**
     * 행사/이용 일시
     */
    @NotNull
    private LocalDateTime eventDateTime;

    /**
     * 배송(행사) 도로명 주소. 서버가 이 값만 지오코딩해서 위경도를 계산한다
     */
    @NotBlank
    private String deliveryAddress;

    /**
     * 실제 배송 위치를 확인하기 위한 상세 주소
     */
    @NotBlank
    private String deliveryAddressDetail;

    /**
     * 견적 이미지 URL(선택)
     */
    private String estimateImage;
}
