package org.example.matcheat.domain.chat.interceptor;

import org.example.matcheat.domain.account.service.TradeAccountValidationService;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSubscriptionInterceptorTest {
    private ChatService chatService;
    private TradeAccountValidationService accounts;
    private JwtDecoder jwtDecoder;
    private Converter<Jwt, AbstractAuthenticationToken> converter;
    private ChatSubscriptionInterceptor interceptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatService = mock(ChatService.class);
        accounts = mock(TradeAccountValidationService.class);
        jwtDecoder = mock(JwtDecoder.class);
        converter = mock(Converter.class);
        interceptor = new ChatSubscriptionInterceptor(chatService, accounts, jwtDecoder, converter);
    }

    @Test
    void rejectsConnectWithoutBearerToken() {
        Message<?> message = message(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void authenticatesConnectWithAccountJwt() {
        Jwt jwt = jwt("42");
        AbstractAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt, List.of(), "42");
        when(jwtDecoder.decode("access-token")).thenReturn(jwt);
        when(converter.convert(jwt)).thenReturn(authentication);
        Message<?> message = message(StompCommand.CONNECT, null, "Bearer access-token");

        interceptor.preSend(message, mock(MessageChannel.class));

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        assertThat(accessor.getUser()).isEqualTo(authentication);
    }

    @Test
    void validatesSubscriberUsingAuthenticatedPrincipal() {
        ChatRoom room = mock(ChatRoom.class);
        when(chatService.getChatRoomEntity(7L)).thenReturn(room);
        when(accounts.sellerIdForUserOrNull(42L)).thenReturn(420L);
        Message<?> message = subscribedMessage("/sub/chat/room/7", "42");

        interceptor.preSend(message, mock(MessageChannel.class));

        verify(room).validateParticipant(42L, 420L);
    }

    private static Message<?> message(StompCommand command, String destination, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) accessor.setDestination(destination);
        if (authorization != null) accessor.setNativeHeader("Authorization", authorization);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<?> subscribedMessage(String destination, String subject) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Jwt jwt = jwt(subject);
        accessor.setUser(new JwtAuthenticationToken(jwt, List.of(), subject));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Jwt jwt(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
