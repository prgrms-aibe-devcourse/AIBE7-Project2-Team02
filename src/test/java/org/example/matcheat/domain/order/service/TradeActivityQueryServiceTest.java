package org.example.matcheat.domain.order.service;

import org.example.matcheat.domain.account.service.TradeAccountValidationService;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.repository.ChatRoomRepository;
import org.example.matcheat.domain.estimate.dto.EstimateResponseDTO;
import org.example.matcheat.domain.estimate.enums.EstimateStatus;
import org.example.matcheat.domain.estimate.service.EstimateAccessService;
import org.example.matcheat.domain.order.dto.TradeActivityResponse;
import org.example.matcheat.domain.payment.entity.Payment;
import org.example.matcheat.domain.payment.repository.PaymentRepository;
import org.example.matcheat.domain.proposal.dto.ProposalResponseDTO;
import org.example.matcheat.domain.proposal.entity.Proposal;
import org.example.matcheat.domain.proposal.service.ProposalAccessService;
import org.example.matcheat.domain.quote.entity.Quote;
import org.example.matcheat.domain.quote.repository.QuoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeActivityQueryServiceTest {
    private final ProposalAccessService proposals = mock(ProposalAccessService.class);
    private final EstimateAccessService estimates = mock(EstimateAccessService.class);
    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ChatRoomRepository chatRooms = mock(ChatRoomRepository.class);
    private final TradeAccountValidationService accounts = mock(TradeAccountValidationService.class);
    private final TradeActivityQueryService service = new TradeActivityQueryService(
            proposals, estimates, quotes, payments, chatRooms, accounts);

    @Test
    void replacesLinkedProposalWithCompletedQuoteUsingJwtAccountId() {
        ProposalResponseDTO proposal = proposal(1L, 11L, 101L);
        EstimateResponseDTO estimate = EstimateResponseDTO.builder()
                .id(2L)
                .status(EstimateStatus.REQUESTED)
                .itemName("도시락 견적")
                .budget(BigDecimal.valueOf(50_000))
                .createdAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .build();
        Quote quote = quote(3L, 30L, 11L, 101L, Quote.SenderRole.BUYER);
        ChatRoom chatRoom = chatRoom(30L, 1L, 3L, 11L, 101L);
        Payment payment = payment(4L, 3L, 11L, 101L);

        when(proposals.findReceived(11L)).thenReturn(List.of(proposal));
        when(estimates.findSentByMe(11L)).thenReturn(List.of(estimate));
        when(quotes.findAllByBuyerIdOrderByCreatedAtDesc(11L)).thenReturn(List.of(quote));
        when(chatRooms.findAllById(List.of(30L))).thenReturn(List.of(chatRoom));
        when(payments.findAllByQuoteIdIn(List.of(3L))).thenReturn(List.of(payment));

        List<TradeActivityResponse> result = service.findPurchases(11L);

        assertThat(result).extracting(TradeActivityResponse::sourceType)
                .containsExactlyInAnyOrder(
                        TradeActivityResponse.ActivityType.ESTIMATE,
                        TradeActivityResponse.ActivityType.QUOTE);
        assertThat(result).filteredOn(activity -> activity.sourceType() == TradeActivityResponse.ActivityType.QUOTE)
                .singleElement()
                .satisfies(activity -> {
                    assertThat(activity.direction()).isEqualTo(TradeActivityResponse.Direction.SENT);
                    assertThat(activity.paymentStatus()).isEqualTo("COMPLETED");
                    assertThat(activity.paymentId()).isEqualTo(4L);
                    assertThat(activity.requestId()).isEqualTo(11L);
                    assertThat(activity.itemName()).isEqualTo("행사 제안");
                });
        verify(accounts).requireActiveUser(11L);
        verify(quotes).findAllByBuyerIdOrderByCreatedAtDesc(11L);
    }

    @Test
    void mapsSellerAccountIdBeforeCollectingSales() {
        Quote quote = quote(7L, null, 22L, 202L, Quote.SenderRole.BUYER);
        when(accounts.approvedSellerIdForUser(33L)).thenReturn(202L);
        when(proposals.findSent(33L)).thenReturn(List.of());
        when(estimates.findReceivedByMe(33L)).thenReturn(List.of());
        when(quotes.findAllBySellerIdOrderByCreatedAtDesc(202L)).thenReturn(List.of(quote));
        when(payments.findAllByQuoteIdIn(List.of(7L))).thenReturn(List.of());

        List<TradeActivityResponse> result = service.findSales(33L);

        assertThat(result).singleElement().satisfies(activity -> {
            assertThat(activity.sourceId()).isEqualTo(7L);
            assertThat(activity.direction()).isEqualTo(TradeActivityResponse.Direction.RECEIVED);
        });
        verify(accounts).approvedSellerIdForUser(33L);
        verify(quotes).findAllBySellerIdOrderByCreatedAtDesc(202L);
    }

    @Test
    void keepsOnlyCurrentQuoteForAChatRoom() {
        Quote previous = quote(7L, 30L, 11L, 101L, Quote.SenderRole.SELLER);
        Quote current = quote(8L, 30L, 11L, 101L, Quote.SenderRole.BUYER);
        ReflectionTestUtils.setField(previous, "createdAt", LocalDateTime.of(2026, 9, 2, 10, 0));
        ReflectionTestUtils.setField(current, "createdAt", LocalDateTime.of(2026, 9, 2, 11, 0));
        ChatRoom chatRoom = chatRoom(30L, null, 8L, 11L, 101L);

        when(proposals.findReceived(11L)).thenReturn(List.of());
        when(estimates.findSentByMe(11L)).thenReturn(List.of());
        when(quotes.findAllByBuyerIdOrderByCreatedAtDesc(11L)).thenReturn(List.of(current, previous));
        when(chatRooms.findAllById(List.of(30L))).thenReturn(List.of(chatRoom));
        when(payments.findAllByQuoteIdIn(List.of(8L))).thenReturn(List.of());

        assertThat(service.findPurchases(11L))
                .singleElement()
                .extracting(TradeActivityResponse::sourceId)
                .isEqualTo(8L);
    }

    @Test
    void keepsProposalUntilAQuoteIsCreated() {
        ProposalResponseDTO proposal = proposal(1L, 11L, 101L);
        when(proposals.findReceived(11L)).thenReturn(List.of(proposal));
        when(estimates.findSentByMe(11L)).thenReturn(List.of());
        when(quotes.findAllByBuyerIdOrderByCreatedAtDesc(11L)).thenReturn(List.of());

        assertThat(service.findPurchases(11L))
                .singleElement()
                .satisfies(activity -> {
                    assertThat(activity.sourceType()).isEqualTo(TradeActivityResponse.ActivityType.PROPOSAL);
                    assertThat(activity.sourceId()).isEqualTo(1L);
                });
    }

    private static ProposalResponseDTO proposal(long id, long requestId, long sellerId) {
        Proposal proposal = Proposal.create(
                requestId, sellerId, null, "행사 제안", 10, 5_000L, 50_000L, 2, "구성 제안");
        ReflectionTestUtils.setField(proposal, "id", id);
        ReflectionTestUtils.setField(proposal, "createdAt", LocalDateTime.of(2026, 9, 2, 9, 0));
        return ProposalResponseDTO.from(proposal);
    }

    private static Quote quote(long id, Long chatRoomId, long buyerId, long sellerId, Quote.SenderRole senderRole) {
        Quote quote = Quote.builder()
                .chatRoomId(chatRoomId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .senderRole(senderRole)
                .quantity(10)
                .unitPrice(5_000L)
                .deliveryFee(0L)
                .totalAmount(50_000L)
                .status(Quote.QuoteStatus.ACCEPTED)
                .build();
        ReflectionTestUtils.setField(quote, "id", id);
        ReflectionTestUtils.setField(quote, "createdAt", LocalDateTime.of(2026, 9, 2, 11, 0));
        return quote;
    }

    private static ChatRoom chatRoom(
            long id,
            Long proposalId,
            Long quoteId,
            long buyerId,
            long sellerId
    ) {
        ChatRoom chatRoom = ChatRoom.builder()
                .proposalId(proposalId)
                .quoteId(quoteId)
                .originType(ChatRoom.OriginType.PROPOSAL)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .build();
        ReflectionTestUtils.setField(chatRoom, "id", id);
        return chatRoom;
    }

    private static Payment payment(long id, long quoteId, long buyerId, long sellerId) {
        Payment payment = Payment.builder()
                .quoteId(quoteId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .amount(50_000L)
                .build();
        ReflectionTestUtils.setField(payment, "id", id);
        payment.markCompleted("test-transaction");
        return payment;
    }
}
