package org.example.matcheat.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.chat.dto.ChatRoomCreateRequest;
import org.example.matcheat.domain.chat.dto.ChatRoomResponse;
import org.example.matcheat.domain.chat.entity.ChatMessage;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.repository.ChatMessageRepository;
import org.example.matcheat.domain.chat.repository.ChatRoomRepository;
import org.example.matcheat.domain.account.service.TradeAccountValidationService;
import org.example.matcheat.domain.chat.support.ProductOwnerLookup;
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

	@Transactional
	public ChatRoomResponse createChatRoom(ChatRoomCreateRequest request, Long currentUserId) {
		accounts.requireActiveUser(currentUserId);

		Long resolvedSellerId;
		if (request.getProductId() != null) {
			Long ownerAccountId = productOwnerLookup.findOwnerAccountId(request.getProductId());
			// 상품 등록자가 실제 "승인된 판매자"인지 검증 + seller_id 획득을 한 번에 처리
			resolvedSellerId = accounts.approvedSellerIdForUser(ownerAccountId);
		} else {
			resolvedSellerId = request.getSellerId();
			accounts.requireApprovedSeller(resolvedSellerId);
		}

		ChatRoom chatRoom = getOrCreateChatRoomEntity(
				request.getProposalId(), request.getOriginType(), currentUserId, resolvedSellerId);
		return ChatRoomResponse.from(chatRoom);
	}

	@Transactional
	public ChatRoom getOrCreateChatRoomForQuote(Long proposalId, ChatRoom.OriginType originType, Long buyerId, Long sellerId) {
		accounts.requireActiveUser(buyerId);
		accounts.requireApprovedSeller(sellerId);
		return getOrCreateChatRoomEntity(proposalId, originType, buyerId, sellerId);
	}

	/**
	 * [추가] 외부 도메인(QuoteService 등) 전용 ChatRoom 엔티티 조회 메서드
	 */
	@Transactional(readOnly = true)
	public ChatRoom getChatRoomEntity(Long chatRoomId) {
		return chatRoomRepository.findById(chatRoomId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다. ID: " + chatRoomId));
	}

	/**
	 * [버그 수정] 기존 코드는 proposalId가 null인 경우(=Proposal 도메인이 아직 없어
	 * /quotes/direct처럼 proposalId 없이 PROPOSAL 타입 방을 만드는 모든 경로)
	 * 중복 방지 조회를 전혀 타지 않아, 같은 buyer-seller 조합으로 호출할 때마다
	 * ChatRoom이 무한히 새로 생성됐다.
	 *
	 * 수정 후 규칙:
	 * - proposalId가 있으면: 그 proposalId 기준으로만 기존 방 재사용 (오퍼 1개당 방 1개).
	 * - proposalId가 없으면: PROPOSAL/INQUIRY 상관없이 buyerId+sellerId+originType+status
	 *   기준으로 활성 상태인 기존 방을 재사용한다.
	 */
	private ChatRoom getOrCreateChatRoomEntity(Long proposalId, ChatRoom.OriginType requestedOriginType, Long buyerId, Long sellerId) {
		ChatRoom.OriginType resolvedOriginType = (proposalId != null)
				? ChatRoom.OriginType.PROPOSAL
				: (requestedOriginType != null ? requestedOriginType : ChatRoom.OriginType.INQUIRY);

		if (proposalId != null) {
			return chatRoomRepository.findByProposalId(proposalId)
					.orElseGet(() -> createNewChatRoom(proposalId, resolvedOriginType, buyerId, sellerId));
		}

		return chatRoomRepository.findByBuyerIdAndSellerIdAndOriginTypeAndStatus(
						buyerId, sellerId, resolvedOriginType, ChatRoom.Status.ACTIVE)
				.orElseGet(() -> createNewChatRoom(null, resolvedOriginType, buyerId, sellerId));
	}

	private ChatRoom createNewChatRoom(Long proposalId, ChatRoom.OriginType originType, Long buyerId, Long sellerId) {
		ChatRoom chatRoom = ChatRoom.builder()
				.proposalId(proposalId)
				.quoteId(null)
				.originType(originType)
				.buyerId(buyerId)
				.sellerId(sellerId)
				.build();

		return chatRoomRepository.save(chatRoom);
	}

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
		return ChatRoomResponse.from(chatRoom);
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
				.map(chatRoom -> ChatRoomResponse.from(chatRoom, latestMessages.get(chatRoom.getId())))
				.sorted((left, right) -> compareNewestFirst(sortAt(left), sortAt(right)))
				.toList();
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
}
