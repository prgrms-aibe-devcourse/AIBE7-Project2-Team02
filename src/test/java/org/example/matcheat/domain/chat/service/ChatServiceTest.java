package org.example.matcheat.domain.chat.service;

import org.example.matcheat.domain.account.service.TradeAccountValidationService;
import org.example.matcheat.domain.chat.dto.ChatRoomResponse;
import org.example.matcheat.domain.chat.entity.ChatMessage;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.repository.ChatMessageRepository;
import org.example.matcheat.domain.chat.repository.ChatRoomRepository;
import org.example.matcheat.domain.chat.support.ProductOwnerLookup;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private final ChatRoomRepository chatRooms = mock(ChatRoomRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final TradeAccountValidationService accounts = mock(TradeAccountValidationService.class);
    private final ProductOwnerLookup productOwnerLookup = mock(ProductOwnerLookup.class);

    private final ChatService service =
            new ChatService(chatRooms, messages, accounts, productOwnerLookup);

    @Test
    void returnsParticipantRoomsWithLatestMessageSnippets() {
        ChatRoom olderRoom = chatRoom(1L, LocalDateTime.of(2026, 9, 1, 9, 0));
        ChatRoom recentRoom = chatRoom(2L, LocalDateTime.of(2026, 9, 1, 10, 0));
        ChatMessage latest = message(
                2L,
                "배송 시간은 오후 2시로 부탁드립니다.",
                LocalDateTime.of(2026, 9, 2, 12, 0));

        when(accounts.sellerIdForUserOrNull(42L)).thenReturn(420L);
        when(chatRooms.findAllByParticipant(42L, 420L)).thenReturn(List.of(olderRoom, recentRoom));
        when(messages.findLatestByChatRoomIds(List.of(1L, 2L))).thenReturn(List.of(latest));

        List<ChatRoomResponse> result = service.getChatRooms(42L);

        assertThat(result).extracting(ChatRoomResponse::getChatRoomId).containsExactly(2L, 1L);
        assertThat(result.get(0).getLastMessage()).isEqualTo("배송 시간은 오후 2시로 부탁드립니다.");
        assertThat(result.get(0).getLastMessageAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 12, 0));
        assertThat(result.get(1).getLastMessage()).isNull();
        verify(chatRooms).findAllByParticipant(42L, 420L);
        verify(messages).findLatestByChatRoomIds(List.of(1L, 2L));
    }

    @Test
    void skipsMessageLookupWhenParticipantHasNoRooms() {
        when(chatRooms.findAllByParticipant(7L, null)).thenReturn(List.of());

        assertThat(service.getChatRooms(7L)).isEmpty();

        verifyNoInteractions(messages);
    }

    private static ChatRoom chatRoom(long id, LocalDateTime createdAt) {
        ChatRoom room = ChatRoom.builder()
                .originType(ChatRoom.OriginType.PROPOSAL)
                .buyerId(42L)
                .sellerId(420L)
                .build();
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "createdAt", createdAt);
        return room;
    }

    private static ChatMessage message(long chatRoomId, String content, LocalDateTime createdAt) {
        ChatMessage message = ChatMessage.builder()
                .chatRoomId(chatRoomId)
                .senderId(42L)
                .content(content)
                .messageType(ChatMessage.MessageType.TEXT)
                .build();
        ReflectionTestUtils.setField(message, "id", 100L);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }
}
