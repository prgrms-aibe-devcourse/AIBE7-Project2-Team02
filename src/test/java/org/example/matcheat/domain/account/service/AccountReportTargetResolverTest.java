package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.enums.AccountReportTargetType;
import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.repository.SellerApplicationRepository;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.repository.ChatRoomRepository;
import org.example.matcheat.domain.estimate.repository.EstimateRepository;
import org.example.matcheat.domain.order.entity.OrderRequest;
import org.example.matcheat.domain.order.repository.OrderRequestRepository;
import org.example.matcheat.domain.product.entity.ProductEntity;
import org.example.matcheat.domain.product.repository.ProductRepository;
import org.example.matcheat.domain.proposal.repository.ProposalRepository;
import org.example.matcheat.domain.quote.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountReportTargetResolverTest {
    private ChatRoomRepository chatRooms;
    private ProductRepository products;
    private OrderRequestRepository orderRequests;
    private ProposalRepository proposals;
    private SellerApplicationRepository sellers;
    private AccountReportTargetResolver resolver;

    @BeforeEach
    void setUp() {
        chatRooms = mock(ChatRoomRepository.class);
        products = mock(ProductRepository.class);
        orderRequests = mock(OrderRequestRepository.class);
        proposals = mock(ProposalRepository.class);
        sellers = mock(SellerApplicationRepository.class);
        resolver = new AccountReportTargetResolver(
                chatRooms,
                mock(QuoteRepository.class),
                products,
                orderRequests,
                proposals,
                mock(EstimateRepository.class),
                sellers);
    }

    @Test
    void resolvesSellerAccountWhenBuyerReportsChatRoom() {
        ChatRoom room = mock(ChatRoom.class);
        when(room.getBuyerId()).thenReturn(7L);
        when(room.getSellerId()).thenReturn(70L);
        when(chatRooms.findById(3L)).thenReturn(Optional.of(room));
        when(sellers.findUserIdBySellerId(70L)).thenReturn(Optional.of(9L));

        assertThat(resolver.resolveReportedUser(7L, AccountReportTargetType.CHAT_ROOM, 3L)).isEqualTo(9L);
    }

    @Test
    void resolvesBuyerWhenSellerReportsChatRoom() {
        ChatRoom room = mock(ChatRoom.class);
        when(room.getBuyerId()).thenReturn(7L);
        when(room.getSellerId()).thenReturn(70L);
        when(chatRooms.findById(3L)).thenReturn(Optional.of(room));
        when(sellers.findByUserId(9L)).thenReturn(Optional.of(
                new SellerApplicationRepository.SellerApplication(70L, SellerVerificationStatus.APPROVED)));

        assertThat(resolver.resolveReportedUser(9L, AccountReportTargetType.CHAT_ROOM, 3L)).isEqualTo(7L);
    }

    @Test
    void rejectsNonParticipantChatReport() {
        ChatRoom room = mock(ChatRoom.class);
        when(room.getBuyerId()).thenReturn(7L);
        when(room.getSellerId()).thenReturn(70L);
        when(chatRooms.findById(3L)).thenReturn(Optional.of(room));
        when(sellers.findByUserId(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveReportedUser(11L, AccountReportTargetType.CHAT_ROOM, 3L))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.REPORT_TARGET_NOT_FOUND));
    }

    @Test
    void rejectsReportingOwnProduct() {
        ProductEntity product = mock(ProductEntity.class);
        when(product.getOwnerAccountId()).thenReturn(7L);
        when(products.findById(4L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> resolver.resolveReportedUser(7L, AccountReportTargetType.PRODUCT, 4L))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.FORBIDDEN));
    }

    @Test
    void onlySellerWhoProposedCanReportOrderRequest() {
        OrderRequest request = mock(OrderRequest.class);
        when(request.getId()).thenReturn(5L);
        when(request.getBuyerId()).thenReturn(7L);
        when(orderRequests.findById(5L)).thenReturn(Optional.of(request));
        when(sellers.findByUserId(9L)).thenReturn(Optional.of(
                new SellerApplicationRepository.SellerApplication(70L, SellerVerificationStatus.APPROVED)));
        when(proposals.existsByRequestIdAndSellerId(5L, 70L)).thenReturn(true);

        assertThat(resolver.resolveReportedUser(9L, AccountReportTargetType.ORDER_REQUEST, 5L)).isEqualTo(7L);
    }

    @Test
    void returnsNotFoundForMissingTarget() {
        when(chatRooms.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveReportedUser(7L, AccountReportTargetType.CHAT_ROOM, 99L))
                .isInstanceOfSatisfying(AccountApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AccountErrorCode.REPORT_TARGET_NOT_FOUND));
    }
}
