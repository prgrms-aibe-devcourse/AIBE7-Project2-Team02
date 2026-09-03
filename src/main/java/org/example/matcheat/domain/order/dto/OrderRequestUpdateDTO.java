package org.example.matcheat.domain.order.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.matcheat.domain.order.enums.BudgetType;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 요청에서 전달된 항목만 부분 수정하기 위한 입력 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderRequestUpdateDTO {
    @Size(max = 100)
    private String title;

    @Size(max = 1000)
    private String description;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime eventDateTime;

    @Positive
    private Integer quantity;

    private BudgetType budgetType;

    @Positive
    private BigDecimal budget;

    private String category;

    private String deliveryAddress;

    @Size(max = 255)
    private String deliveryAddressDetail;

    /**
     * 조회된 주문 요청 정보를 수정용 DTO로 변환
     */
    public static OrderRequestUpdateDTO from(OrderRequestResponseDTO response) {
        OrderRequestUpdateDTO dto = new OrderRequestUpdateDTO();

        dto.title = response.getTitle();
        dto.description = response.getDescription();
        dto.eventDateTime = response.getEventDateTime();
        dto.quantity = response.getQuantity();
        dto.budgetType = response.getBudgetType();
        dto.budget = response.getBudget();
        dto.category = response.getCategory();
        dto.deliveryAddress = response.getDeliveryAddress();
        dto.deliveryAddressDetail =
                response.getDeliveryAddressDetail();

        return dto;
    }
}
