package org.example.matcheat.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.account.repository.SellerApplicationRepository;
import org.example.matcheat.domain.account.repository.UserCredentialRepository;
import org.example.matcheat.domain.account.service.TradeAccountValidationService;
import org.example.matcheat.domain.chat.dto.ChatRoomCreateRequest;
import org.example.matcheat.domain.chat.dto.ChatRoomResponse;
import org.example.matcheat.domain.chat.entity.ChatMessage;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.repository.ChatMessageRepository;
import org.example.matcheat.domain.chat.repository.ChatRoomRepository;
import org.example.matcheat.domain.order.dto.OrderRequestResponseDTO;
import org.example.matcheat.domain.order.service.OrderRequestService;
import org.example.matcheat.support.product.ProductOwnerLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TradeAccountValidationService accounts;
    private final ProductOwnerLookup productOwnerLookup;
    private final OrderRequestService orderRequestService;
    private final UserCredentialRepository users;
    private final SellerApplicationRepository sellerApplications;

    @Transactional
    public ChatRoomResponse createChatRoom(
            ChatRoomCreateRequest request,
            Long currentUserId
    ) {
        accounts.requireActiveUser(currentUserId);

        Long buyerId;
        Long resolvedSellerId;

        if (request.getProductId() != null) {
            // 상품 기준: 현재 사용자가 구매자
            Long ownerAccountId =
                    productOwnerLookup.findOwnerAccountId(
                            request.getProductId()
                    );

            buyerId = currentUserId;

            // 상품 등록자의 승인된 seller_id를 조회한다.
            resolvedSellerId =
                    accounts.approvedSellerIdForUser(
                            ownerAccountId
                    );

        } else if (request.getOrderRequestId() != null) {
            // 주문 기준: 주문 작성자가 구매자, 현재 사용자가 판매자
            OrderRequestResponseDTO order =
                    orderRequestService.findById(
                            request.getOrderRequestId()
                    );

            buyerId = order.getBuyerId();

            resolvedSellerId =
                    accounts.approvedSellerIdForUser(
                            currentUserId
                    );

        } else {
            // 기존 sellerId 직접 지정 방식
            buyerId = currentUserId;
            resolvedSellerId = request.getSellerId();

            accounts.requireApprovedSeller(
                    resolvedSellerId
            );
        }

        ChatRoom chatRoom =
                getOrCreateChatRoomEntity(
                        request.getProposalId(),
                        request.getOriginType(),
                        buyerId,
                        resolvedSellerId,
                        request.getProductId(),
                        request.getOrderRequestId()
                );

        return addParticipantNames(
                ChatRoomResponse.from(chatRoom),
                chatRoom
        );
    }

    @Transactional
    public ChatRoom getOrCreateChatRoomForQuote(
            Long proposalId,
            ChatRoom.OriginType originType,
            Long buyerId,
            Long sellerId,
            Long productId
    ) {
        accounts.requireActiveUser(buyerId);
        accounts.requireApprovedSeller(sellerId);

        return getOrCreateChatRoomEntity(
                proposalId,
                originType,
                buyerId,
                sellerId,
                productId,
                null
        );
    }

    /**
     * [추가] 외부 도메인(QuoteService 등) 전용 ChatRoom 엔티티 조회 메서드
     */
    @Transactional(readOnly = true)
    public ChatRoom getChatRoomEntity(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 채팅방입니다. ID: " + chatRoomId
                        )
                );
    }

    /**
     * 채팅방이 가리키는 상품을 사용자가 확인한 뒤 명시적으로 변경한다.
     */
    @Transactional
    public ChatRoomResponse changeChatRoomProduct(
            Long chatRoomId,
            Long currentUserId,
            Long newProductId
    ) {
        ChatRoom chatRoom = getChatRoomEntity(chatRoomId);

        chatRoom.validateParticipant(
                currentUserId,
                accounts.sellerIdForUserOrNull(currentUserId)
        );

        // 존재하는 상품인지 확인한다.
        productOwnerLookup.findOwnerAccountId(newProductId);

        chatRoom.changeProduct(newProductId);

        return addParticipantNames(
                ChatRoomResponse.from(chatRoom),
                chatRoom
        );
    }

    /**
     * [버그 수정] 기존 코드는 proposalId가 null인 경우(=Proposal 도메인이 아직 없어
     * /quotes/direct처럼 proposalId 없이 PROPOSAL 타입 방을 만드는 모든 경로)
     * 중복 방지 조회를 전혀 타지 않아, 같은 buyer-seller 조합으로 호출할 때마다
     * ChatRoom이 무한히 새로 생성됐다.
     * <p>
     * 수정 후 규칙:
     * - proposalId가 있으면: 그 proposalId 기준으로만 기존 방 재사용 (오퍼 1개당 방 1개).
     * - proposalId가 없으면: PROPOSAL/INQUIRY 상관없이 buyerId+sellerId+originType+status
     * 기준으로 활성 상태인 기존 방을 재사용한다.
     */
    private ChatRoom getOrCreateChatRoomEntity(
            Long proposalId,
            ChatRoom.OriginType requestedOriginType,
            Long buyerId,
            Long sellerId,
            Long productId,
            Long orderRequestId
    ) {
        ChatRoom.OriginType resolvedOriginType =
                requestedOriginType != null
                        ? requestedOriginType
                        : (orderRequestId != null || proposalId != null
                           ? ChatRoom.OriginType.PROPOSAL
                           : ChatRoom.OriginType.INQUIRY);

        // 상품 기준 채팅
        if (productId != null) {
            return chatRoomRepository
                    .findByBuyerIdAndSellerIdAndProductIdAndOriginTypeAndStatus(
                            buyerId,
                            sellerId,
                            productId,
                            resolvedOriginType,
                            ChatRoom.Status.ACTIVE
                    )
                    .orElseGet(() ->
                            createNewChatRoom(
                                    proposalId,
                                    resolvedOriginType,
                                    buyerId,
                                    sellerId,
                                    productId,
                                    null
                            )
                    );
        }

        // 주문 기준 채팅
        if (orderRequestId != null) {
            ChatRoom chatRoom =
                    chatRoomRepository
                            .findByBuyerIdAndSellerIdAndOrderRequestIdAndOriginTypeAndStatus(
                                    buyerId,
                                    sellerId,
                                    orderRequestId,
                                    resolvedOriginType,
                                    ChatRoom.Status.ACTIVE
                            )
                            .orElseGet(() ->
                                    createNewChatRoom(
                                            proposalId,
                                            resolvedOriginType,
                                            buyerId,
                                            sellerId,
                                            null,
                                            orderRequestId
                                    )
                            );

            // 바로 채팅으로 먼저 생성된 방이라면 이후 제안 ID를 연결한다.
            chatRoom.linkProposal(proposalId);

            return chatRoom;
        }

        // 기존 proposal 기준 채팅
        if (proposalId != null) {
            return chatRoomRepository
                    .findByProposalId(proposalId)
                    .orElseGet(() ->
                            createNewChatRoom(
                                    proposalId,
                                    resolvedOriginType,
                                    buyerId,
                                    sellerId,
                                    productId,
                                    orderRequestId
                            )
                    );
        }

        // 기존 buyer-seller 기준 채팅
        return chatRoomRepository
                .findByBuyerIdAndSellerIdAndOriginTypeAndStatus(
                        buyerId,
                        sellerId,
                        resolvedOriginType,
                        ChatRoom.Status.ACTIVE
                )
                .orElseGet(() ->
                        createNewChatRoom(
                                null,
                                resolvedOriginType,
                                buyerId,
                                sellerId,
                                productId,
                                orderRequestId
                        )
                );
    }

    private ChatRoom createNewChatRoom(
            Long proposalId,
            ChatRoom.OriginType originType,
            Long buyerId,
            Long sellerId,
            Long productId,
            Long orderRequestId
    ) {
        ChatRoom chatRoom =
                ChatRoom.builder()
                        .proposalId(proposalId)
                        .quoteId(null)
                        .productId(productId)
                        .orderRequestId(orderRequestId)
                        .originType(originType)
                        .buyerId(buyerId)
                        .sellerId(sellerId)
                        .build();

        return chatRoomRepository.save(chatRoom);
    }

    /**
     * [추가] 방이 가리키는 상품을 명시적으로 전환한다 (A안 + 안전장치).
     * 프론트가 "이 방은 이미 다른 상품(OO)에 대해 진행 중입니다. 전환할까요?" 같은
     * 경고를 띄운 뒤, 사용자가 확인했을 때만 이 메서드를 호출해야 한다.
     */

    @Transactional
    public void closeChatRoom(Long chatRoomId) {
        ChatRoom chatRoom = getChatRoomEntity(chatRoomId);
        chatRoom.close();
    }

    @Transactional
    public ChatRoom updateChatRoomQuoteId(Long chatRoomId, Long quoteId) {
        ChatRoom chatRoom = getChatRoomEntity(chatRoomId);
        chatRoom.updateQuoteId(quoteId);
        return chatRoom;
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse getChatRoom(Long chatRoomId, Long currentUserId) {
        ChatRoom chatRoom = getChatRoomEntity(chatRoomId);
        chatRoom.validateParticipant(currentUserId, accounts.sellerIdForUserOrNull(currentUserId));
        return addParticipantNames(
                ChatRoomResponse.from(chatRoom),
                chatRoom
        );
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getChatRooms(Long currentUserId) {
        Long sellerProfileId = accounts.sellerIdForUserOrNull(currentUserId);
        List<ChatRoom> chatRooms = chatRoomRepository.findAllByParticipant(currentUserId, sellerProfileId);
        if (chatRooms.isEmpty()) {
            return List.of();
        }

        List<Long> chatRoomIds = chatRooms.stream().map(ChatRoom::getId).toList();
        Map<Long, ChatMessage> latestMessages = chatMessageRepository.findLatestByChatRoomIds(chatRoomIds).stream()
                .collect(Collectors.toMap(ChatMessage::getChatRoomId, Function.identity()));

        return chatRooms.stream()
                .map(chatRoom ->
                        addParticipantNames(
                                ChatRoomResponse.from(
                                        chatRoom,
                                        latestMessages.get(
                                                chatRoom.getId()
                                        )
                                ),
                                chatRoom
                        )
                )
                .sorted((left, right) -> compareNewestFirst(sortAt(left), sortAt(right)))
                .toList();
    }

    /**
     * 구매자와 판매자의 실제 회원 이름을 채팅방 응답에 추가한다.
     */
    private ChatRoomResponse addParticipantNames(
            ChatRoomResponse response,
            ChatRoom chatRoom
    ) {
        String buyerName =
                users.findById(
                                chatRoom.getBuyerId()
                        )
                        .map(user -> user.name())
                        .orElse(
                                "구매자 #" + chatRoom.getBuyerId()
                        );

        String sellerName =
                sellerApplications
                        .findUserIdBySellerId(
                                chatRoom.getSellerId()
                        )
                        .flatMap(users::findById)
                        .map(user -> user.name())
                        .orElse(
                                "판매자 #" + chatRoom.getSellerId()
                        );

        return response.withParticipantNames(
                buyerName,
                sellerName
        );
    }

    private static java.time.LocalDateTime sortAt(ChatRoomResponse room) {
        return room.getLastMessageAt() != null ? room.getLastMessageAt() : room.getCreatedAt();
    }

    private static int compareNewestFirst(java.time.LocalDateTime left, java.time.LocalDateTime right) {
        if (left == null) {
            return right == null ? 0 : 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    @Transactional(readOnly = true)
    public Long getSellerAccountId(Long chatRoomId, Long currentUserId) {
        ChatRoom chatRoom = getChatRoomEntity(chatRoomId);
        chatRoom.validateParticipant(currentUserId, accounts.sellerIdForUserOrNull(currentUserId));
        return accounts.userIdForSellerId(chatRoom.getSellerId());
    }
}

