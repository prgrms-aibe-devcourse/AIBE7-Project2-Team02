package org.example.matcheat.domain.quote.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class AiNegotiationFieldsResult {
	private Integer quantity;
	private String budgetType;      // "PER_PERSON" | "TOTAL" 외 값이면 가드에서 버려짐
	private BigDecimal budget;
	private String eventDateTime;   // ISO-8601 (yyyy-MM-dd'T'HH:mm)
	private String deliveryAddress;
	private String description;
	private String summaryNote;     // 숫자로 표현 안 되는 나머지 합의사항
}