package org.example.matcheat.domain.payment.repository;

import org.example.matcheat.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByQuoteId(Long quoteId);

	List<Payment> findAllByQuoteIdIn(Collection<Long> quoteIds);
}
