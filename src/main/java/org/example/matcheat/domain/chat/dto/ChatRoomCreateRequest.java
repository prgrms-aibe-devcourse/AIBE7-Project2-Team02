package org.example.matcheat.domain.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.matcheat.domain.chat.entity.ChatRoom.OriginType;

@Getter
@NoArgsConstructor
public class ChatRoomCreateRequest {
    private Long sellerId;      // 기존 방식: seller_id 직접 지정
    private Long productId;     // 신규: 상품 기준으로 판매자를 서버가 자동 결정 ("바로 채팅하기"용)
    private Long orderRequestId;
    private Long proposalId;
    private OriginType originType;
}