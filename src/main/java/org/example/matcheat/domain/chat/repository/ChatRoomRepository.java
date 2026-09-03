package org.example.matcheat.domain.chat.repository;

import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByProposalId(Long proposalId);

    @Query("select (count(c) > 0) from ChatRoom c where (c.buyerId = :userId or (:sellerProfileId is not null and c.sellerId = :sellerProfileId)) and c.status = :status")
    boolean existsByParticipantAndStatus(
            @Param("userId") Long userId,
            @Param("sellerProfileId") Long sellerProfileId,
            @Param("status") ChatRoom.Status status);

    @Query("select c from ChatRoom c where c.buyerId = :userId or (:sellerProfileId is not null and c.sellerId = :sellerProfileId) order by c.id desc")
    List<ChatRoom> findAllByParticipant(
            @Param("userId") Long userId,
            @Param("sellerProfileId") Long sellerProfileId);

    // [P1-6 추가] 동일 구매자-판매자 간 활성화된 INQUIRY 채팅방 조회
    Optional<ChatRoom> findByBuyerIdAndSellerIdAndOriginTypeAndStatus(
            Long buyerId,
            Long sellerId,
            ChatRoom.OriginType originType,
            ChatRoom.Status status
    );

    // 상품 기준 활성 채팅방 조회
    Optional<ChatRoom> findByBuyerIdAndSellerIdAndProductIdAndOriginTypeAndStatus(
            Long buyerId,
            Long sellerId,
            Long productId,
            ChatRoom.OriginType originType,
            ChatRoom.Status status
    );

    // 주문 기준 활성 채팅방 조회
    Optional<ChatRoom> findByBuyerIdAndSellerIdAndOrderRequestIdAndOriginTypeAndStatus(
            Long buyerId,
            Long sellerId,
            Long orderRequestId,
            ChatRoom.OriginType originType,
            ChatRoom.Status status
    );
}
