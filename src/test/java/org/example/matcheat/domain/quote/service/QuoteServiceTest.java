package org.example.matcheat.domain.quote.service;

import org.example.matcheat.domain.account.service.TradeAccountValidationService;
import org.example.matcheat.domain.chat.service.ChatService;
import org.example.matcheat.domain.quote.dto.QuoteDirectRequestToBuyer;
import org.example.matcheat.domain.quote.dto.QuoteDirectRequestToSeller;
import org.example.matcheat.domain.quote.entity.Quote;
import org.example.matcheat.domain.quote.repository.QuoteRepository;
import org.example.matcheat.support.product.ProductOwnerLookup;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuoteServiceTest {
    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final TradeAccountValidationService accounts = mock(TradeAccountValidationService.class);
    private final QuoteService service =
            new QuoteService(
                    quotes,
                    mock(ChatService.class),
                    accounts,
                    mock(ProductOwnerLookup.class)
            );

    @Test
    void mapsSellerAccountAndValidatesTargetBuyerBeforeStandaloneQuoteCreation() {
        when(accounts.approvedSellerIdForUser(20L)).thenReturn(200L);
        when(quotes.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createStandaloneQuoteToBuyer(20L, requestToBuyer(10L));

        verify(accounts).approvedSellerIdForUser(20L);
        verify(accounts).requireActiveUser(10L);
        assertThat(response.getBuyerId()).isEqualTo(10L);
        assertThat(response.getSellerId()).isEqualTo(200L);
        assertThat(response.getSenderRole()).isEqualTo(Quote.SenderRole.SELLER);
        assertThat(response.getTotalAmount()).isEqualTo(20_500L);
    }

    @Test
    void validatesBuyerAndTargetSellerBeforeStandaloneQuoteCreation() {
        when(quotes.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createStandaloneQuoteToSeller(10L, requestToSeller(200L));

        verify(accounts).requireActiveUser(10L);
        verify(accounts).requireApprovedSeller(200L);
        assertThat(response.getBuyerId()).isEqualTo(10L);
        assertThat(response.getSellerId()).isEqualTo(200L);
        assertThat(response.getSenderRole()).isEqualTo(Quote.SenderRole.BUYER);
    }

    @Test
    void doesNotSaveStandaloneQuoteWhenTargetSellerIsInvalid() {
        var request = requestToSeller(200L);
        doThrow(new IllegalArgumentException("존재하지 않는 판매자입니다."))
                .when(accounts).requireApprovedSeller(200L);

        assertThatThrownBy(() -> service.createStandaloneQuoteToSeller(10L, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(quotes, never()).save(any());
    }

    private static QuoteDirectRequestToBuyer requestToBuyer(long buyerId) {
        QuoteDirectRequestToBuyer request = new QuoteDirectRequestToBuyer();
        setQuoteFields(request, buyerId);
        return request;
    }

    private static QuoteDirectRequestToSeller requestToSeller(long sellerId) {
        QuoteDirectRequestToSeller request = new QuoteDirectRequestToSeller();
        setQuoteFields(request, sellerId);
        return request;
    }

    private static void setQuoteFields(Object request, long targetId) {
        String targetField = request instanceof QuoteDirectRequestToBuyer ? "targetBuyerId" : "targetSellerId";
        ReflectionTestUtils.setField(request, targetField, targetId);
        ReflectionTestUtils.setField(request, "quantity", 2);
        ReflectionTestUtils.setField(request, "unitPrice", 10_000L);
        ReflectionTestUtils.setField(request, "deliveryFee", 500L);
    }
}
