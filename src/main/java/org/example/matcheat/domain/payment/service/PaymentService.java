package org.example.matcheat.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.payment.dto.PaymentResponse;
import org.example.matcheat.domain.payment.entity.Payment;
import org.example.matcheat.domain.payment.mock.MockPaymentGatewayClient;
import org.example.matcheat.domain.payment.repository.PaymentRepository;
import org.example.matcheat.domain.quote.entity.Quote;
import org.example.matcheat.domain.quote.service.QuoteService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final QuoteService quoteService;
	private final MockPaymentGatewayClient mockPaymentGatewayClient;
	private final SettlementService settlementService;
	private final PaymentAccessService paymentAccess;

	@Transactional
	public PaymentResponse pay(Long quoteId, Long currentUserId) {
		// 견적 행을 잠가 같은 견적의 결제 생성과 PG 호출을 한 번씩만 수행한다.
		Quote quote = quoteService.getQuoteEntityForPayment(quoteId);

		if (!currentUserId.equals(quote.getBuyerId())) {
			throw new AccessDeniedException("결제는 구매자 본인만 할 수 있습니다.");
		}
		if (quote.getStatus() != Quote.QuoteStatus.ACCEPTED) {
			throw new IllegalStateException("확정(ACCEPTED)된 견적서만 결제할 수 있습니다. 현재 상태: " + quote.getStatus());
		}

		Optional<Payment> existing = paymentRepository.findByQuoteId(quoteId);
		if (existing.isPresent() && existing.get().getStatus() == Payment.PaymentStatus.COMPLETED) {
			// 이미 결제 완료 — 중복 결제 방지, 기존 기록 그대로 반환
			return PaymentResponse.from(existing.get());
		}

		Payment payment = existing.orElseGet(() -> paymentRepository.save(Payment.builder()
				.quoteId(quoteId)
				.buyerId(quote.getBuyerId())
				.sellerId(quote.getSellerId())
				.quantity(quote.getQuantity())
				.unitPrice(quote.getUnitPrice())
				.deliveryFee(quote.getDeliveryFee())
				.amount(quote.getTotalAmount())
				.build()));

		MockPaymentGatewayClient.MockPaymentResult result =
				mockPaymentGatewayClient.charge(quoteId, payment.getAmount());

		if (result.success()) {
			payment.markCompleted(result.transactionId());
			settlementService.issueSettlement(payment, quote); // 결제 완료 시 정산서 자동 발행
		} else {
			payment.markFailed(result.failureReason());
		}

		return PaymentResponse.from(payment);
	}

	@Transactional(readOnly = true)
	public PaymentResponse getByQuoteId(Long quoteId, Long currentUserId) {
		Payment payment = paymentRepository.findByQuoteId(quoteId)
				.orElseThrow(() -> new IllegalArgumentException("해당 견적서의 결제 기록이 없습니다. quoteId: " + quoteId));
		paymentAccess.requirePaymentParticipant(payment, currentUserId);
		return PaymentResponse.from(payment);
	}

	@Transactional(readOnly = true)
	public Payment getPaymentEntity(Long paymentId, Long currentUserId) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new IllegalArgumentException("결제 기록을 찾을 수 없습니다. ID: " + paymentId));
		paymentAccess.requirePaymentBuyer(payment, currentUserId);
		return payment;
	}
}
