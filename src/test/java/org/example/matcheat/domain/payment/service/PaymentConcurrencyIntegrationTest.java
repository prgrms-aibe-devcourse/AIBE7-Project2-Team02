package org.example.matcheat.domain.payment.service;

import org.example.matcheat.domain.payment.dto.PaymentResponse;
import org.example.matcheat.domain.payment.entity.Payment;
import org.example.matcheat.domain.payment.mock.MockPaymentGatewayClient;
import org.example.matcheat.domain.payment.repository.PaymentRepository;
import org.example.matcheat.domain.payment.repository.SettlementRepository;
import org.example.matcheat.domain.quote.entity.Quote;
import org.example.matcheat.domain.quote.repository.QuoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.account.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class PaymentConcurrencyIntegrationTest {
    @Autowired
    private PaymentService paymentService;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @MockitoBean
    private MockPaymentGatewayClient paymentGateway;

    @AfterEach
    void cleanUp() {
        settlementRepository.deleteAll();
        paymentRepository.deleteAll();
        quoteRepository.deleteAll();
    }

    @Test
    void serializesConcurrentPaymentsForTheSameQuote() throws Exception {
        Quote quote = quoteRepository.saveAndFlush(Quote.builder()
                .buyerId(11L)
                .sellerId(101L)
                .senderRole(Quote.SenderRole.SELLER)
                .quantity(10)
                .unitPrice(5_000L)
                .deliveryFee(0L)
                .totalAmount(50_000L)
                .status(Quote.QuoteStatus.ACCEPTED)
                .build());
        when(paymentGateway.charge(quote.getId(), 50_000L))
                .thenAnswer(invocation -> {
                    Thread.sleep(100);
                    return MockPaymentGatewayClient.MockPaymentResult.success("tx-once");
                });

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<PaymentResponse>> results = List.of(
                    executor.submit(() -> payAfter(start, quote.getId())),
                    executor.submit(() -> payAfter(start, quote.getId()))
            );
            start.countDown();

            PaymentResponse first = results.get(0).get();
            PaymentResponse second = results.get(1).get();

            assertThat(first.getPaymentId()).isEqualTo(second.getPaymentId());
            assertThat(paymentRepository.count()).isEqualTo(1);
            assertThat(settlementRepository.count()).isEqualTo(1);
            verify(paymentGateway).charge(quote.getId(), 50_000L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsDuplicatePaymentRowsForTheSameQuote() {
        paymentRepository.saveAndFlush(payment(99L));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment(99L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private PaymentResponse payAfter(CountDownLatch start, Long quoteId) throws InterruptedException {
        start.await();
        return paymentService.pay(quoteId, 11L);
    }

    private static Payment payment(Long quoteId) {
        return Payment.builder()
                .quoteId(quoteId)
                .buyerId(11L)
                .sellerId(101L)
                .amount(50_000L)
                .build();
    }
}
