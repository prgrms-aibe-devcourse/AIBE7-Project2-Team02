package org.example.matcheat.domain.quote.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QuoteNegotiationEditRequest {

	@NotNull(message = "수량은 필수입니다.")
	@Positive(message = "수량은 1개 이상이어야 합니다.")
	private Integer quantity;

	@NotNull(message = "단가는 필수입니다.")
	@Positive(message = "단가는 0보다 커야 합니다.")
	private Long unitPrice;

	private Long deliveryFee;

	private String additionalNotes; // [추가] 항목으로 표현 안 되는 조건 자유 기입 (선택값)
}