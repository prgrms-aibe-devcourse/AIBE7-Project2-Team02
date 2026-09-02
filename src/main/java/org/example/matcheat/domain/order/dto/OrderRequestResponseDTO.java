package org.example.matcheat.domain.order.dto;

import lombok.Getter;
import org.example.matcheat.domain.order.entity.OrderRequest;
import org.example.matcheat.domain.order.enums.BudgetType;
import org.example.matcheat.domain.order.enums.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 요청 정보를 API 응답 형태로 전달하는 DTO
 */
@Getter
public class OrderRequestResponseDTO {
    private final Long buyerId;
    private final String title;
    private final String description;
    private final Long id;
    private final LocalDateTime eventDateTime;
    private final Integer quantity;
    private final BudgetType budgetType;
    private final BigDecimal budget;
    private final String category;
    private final String deliveryAddress;
    private final String deliveryAddressDetail;
    private final Double latitude;
    private final Double longitude;
    private final String referenceImageUrl;
    private final RequestStatus status;

    private OrderRequestResponseDTO(OrderRequest orderRequest) {
        this.id = orderRequest.getId();
        this.buyerId = orderRequest.getBuyerId();
        this.title = orderRequest.getTitle();
        this.description = orderRequest.getDescription();
        this.eventDateTime = orderRequest.getEventDateTime();
        this.quantity = orderRequest.getQuantity();
        this.budgetType = orderRequest.getBudgetType();
        this.budget = orderRequest.getBudget();
        this.category = orderRequest.getCategory();
        this.deliveryAddress =
                orderRequest.getDeliveryAddress();

        this.deliveryAddressDetail =
                orderRequest.getDeliveryAddressDetail();

        this.latitude =
                orderRequest.getLatitude();
        this.longitude = orderRequest.getLongitude();
        this.referenceImageUrl =
                orderRequest.getReferenceImageUrl();
        this.status = orderRequest.getStatus();
    }

    /**
     * DB에서 가져온 OrderRequest Entity를 화면에 내려줄 응답 객체로 변환
     */
    public static OrderRequestResponseDTO from(OrderRequest orderRequest) {
        return new OrderRequestResponseDTO(orderRequest);
    }

    /**
     * 주문의 총 예산을 계산한다.
     */
    public BigDecimal getTotalBudget() {
        if (budgetType == BudgetType.TOTAL) {
            return budget;
        }

        return budget.multiply(
                BigDecimal.valueOf(quantity)
        );
    }
}
