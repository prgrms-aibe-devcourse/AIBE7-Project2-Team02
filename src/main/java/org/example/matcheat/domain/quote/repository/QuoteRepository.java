package org.example.matcheat.domain.quote.repository;

import jakarta.persistence.LockModeType;
import org.example.matcheat.domain.quote.entity.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {
	List<Quote> findByChatRoomIdOrderByIdDesc(Long chatRoomId);

	List<Quote> findAllByBuyerIdOrderByCreatedAtDesc(Long buyerId);

	List<Quote> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select q from Quote q where q.id = :id")
	Optional<Quote> findByIdForPayment(@Param("id") Long id);

	@Query("select (count(q) > 0) from Quote q where (q.buyerId = :userId or q.sellerId = :userId) and q.status = :status")
	boolean existsByParticipantAndStatus(@Param("userId") Long userId, @Param("status") Quote.QuoteStatus status);

	boolean existsByBuyerIdAndStatusIn(Long buyerId, Collection<Quote.QuoteStatus> statuses);

	boolean existsBySellerIdAndStatusIn(Long sellerId, Collection<Quote.QuoteStatus> statuses);
}
