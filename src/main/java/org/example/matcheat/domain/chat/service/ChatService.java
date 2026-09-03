package org.example.matcheat.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.chat.dto.ChatRoomCreateRequest;
import org.example.matcheat.domain.chat.dto.ChatRoomResponse;
import org.example.matcheat.domain.chat.entity.ChatMessage;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.repository.ChatMessageRepository;
import org.example.matcheat.domain.chat.repository.ChatRoomRepository;
import org.example.matcheat.domain.account.service.TradeAccountValidationService;
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

	@Transactional
	public ChatRoomResponse createChatRoom(ChatRoomCreateRequest request, Long currentUserId) {
		accounts.requireActiveUser(currentUserId);

		Long resolvedSellerId;
		if (request.getProductId() != null) {
			Long ownerAccountId = productOwnerLookup.findOwnerAccountId(request.getProductId());
			resolvedSellerId = accounts.approvedSellerIdForUser(ownerAccountId);
		} else {
			resolvedSellerId = request.getSellerId();
			accounts.requireApprovedSeller(resolvedSellerId);
		}

		ChatRoom chatRoom = getOrCreateChatRoomEntity(
				request.getProposalId(), request.getOriginType(), currentUserId, resolvedSellerId, request.getProductId());
		return ChatRoomResponse.from(chatRoom);
	}

	@Transactional
	public ChatRoom getOrCreateChatRoomForQuote(Long proposalId, ChatRoom.OriginType originType,
	                                            Long buyerId, Long sellerId, Long productId) {
		accounts.requireActiveUser(buyerId);
		accounts.requireApprovedSeller(sellerId);
		return getOrCreateChatRoomEntity(proposalId, originType, buyerId, sellerId, productId);
	}

	@Transactional(readOnly = true)
	public ChatRoom getChatRoomEntity(Long chatRoomId) {
		return chatRoomRepository.findById(chatRoomId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다. ID: " + chatRoomId));
	}

	/**
	 * [정책] proposalId가 있으면 그 기준으로만 기존 방 재사용.
	 * proposalId가 없으면 buyerId+sellerId+originType+status 기준으로 기존 방 재사용.
	 * [A안] 기존 방을 재사용할 때는 productId를 절대 자동으로 바꾸지 않는다 —
	 * 새 상품으로 넘어가려면 changeChatRoomProduct()를 통한 명시적 호출이 필요하다.
	 * (프론트가 방의 기존 productId와 새로 들어온 productId를 비교해서 경고 문구를
	 *  보여주고, 사용자가 확인한 뒤에만 그 API를 호출하는 흐름을 기대한다.)
	 */
	private ChatRoom getOrCreateChatRoomEntity(Long proposalId, ChatRoom.OriginType requestedOriginType,
	                                           Long buyerId, Long sellerId, Long productId) {
		ChatRoom.OriginType resolvedOriginType = (proposalId != null)
				? ChatRoom.OriginType.PROPOSAL
				: (requestedOriginType != null ? requestedOriginType : ChatRoom.OriginType.INQUIRY);

		if (proposalId != null) {
			return chatRoomRepository.findByProposalId(proposalId)
					.orElseGet(() -> createNewChatRoom(proposalId, resolvedOriginType, buyerId, sellerId, productId));
		}

		return chatRoomRepository.findByBuyerIdAndSellerIdAndOriginTypeAndStatus(
						buyerId, sellerId, resolvedOriginType, ChatRoom.Status.ACTIVE)
				.orElseGet(() -> createNewChatRoom(null, resolvedOriginType, buyerId, sellerId, productId));
	}

	private ChatRoom createNewChatRoom(Long proposalId, ChatRoom.OriginType originType,
	                                   Long buyerId, Long sellerId, Long productId) {
		ChatRoom chatRoom = ChatRoom.builder()
				.proposalId(proposalId)
				.quoteId(null)
				.productId(productId)
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
	public ChatRoomResponse changeChatRoomProduct(Long chatRoomId, Long currentUserId, Long newProductId) {
		ChatRoom chatRoom = getChatRoomEntity(chatRoomId);
		chatRoom.validateParticipant(currentUserId, accounts.sellerIdForUserOrNull(currentUserId));

		// 존재하지 않는 productId면 여기서 예외 (ProductOwnerLookup이 존재 검증 겸용)
		productOwnerLookup.findOwnerAccountId(newProductId);

		chatRoom.changeProduct(newProductId);
		return ChatRoomResponse.from(chatRoom);
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

}

