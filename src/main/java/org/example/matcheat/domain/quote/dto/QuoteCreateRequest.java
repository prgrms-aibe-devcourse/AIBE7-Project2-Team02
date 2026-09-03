package org.example.matcheat.domain.quote.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅방 안에서(또는 채팅방을 새로 만들며) 견적서를 생성할 때 쓰는 요청.
 * buyerId/sellerId는 여기 포함하지 않는다 — ChatRoom에서 서버가 유도한다.
 */
@Getter
@NoArgsConstructor
public class QuoteCreateRequest {

	@NotNull(message = "수량은 필수입니다.")
	@Positive(message = "수량은 1개 이상이어야 합니다.")
	private Integer quantity;

	@NotNull(message = "단가는 필수입니다.")
	@Positive(message = "단가는 0보다 커야 합니다.")
	private Long unitPrice;

	private Long deliveryFee; // 선택 (null이면 0으로 계산)
}