package org.example.matcheat.domain.order.dto;

import org.example.matcheat.domain.estimate.dto.EstimateResponseDTO;
import org.example.matcheat.domain.payment.entity.Payment;
import org.example.matcheat.domain.proposal.dto.ProposalResponseDTO;
import org.example.matcheat.domain.quote.entity.Quote;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 마이페이지 구매·판매 목록을 위한 읽기 전용 거래 활동 응답이다.
 * 도메인별 원본 상태는 유지하고 화면 표시 상태는 클라이언트가 결정한다.
 */
public record TradeActivityResponse(
        String activityId,
        ActivityType sourceType,
        Long sourceId,
        Direction direction,
        String sourceStatus,
        Long requestId,
        Long productId,
        Long quoteId,
        Long paymentId,
        Long chatRoomId,
        String itemName,
        String description,
        Integer quantity,
        BigDecimal totalAmount,
        LocalDateTime eventDateTime,
        LocalDateTime createdAt,
        String paymentStatus,
        LocalDateTime paidAt
) {
    public enum ActivityType {
        PROPOSAL,
        ESTIMATE,
        QUOTE
    }

    public enum Direction {
        SENT,
        RECEIVED
    }

    public static TradeActivityResponse fromProposal(
            ProposalResponseDTO proposal,
            Direction direction
    ) {
        return new TradeActivityResponse(
                activityId(ActivityType.PROPOSAL, proposal.getId()),
                ActivityType.PROPOSAL,
                proposal.getId(),
                direction,
                proposal.getStatus().name(),
                proposal.getRequestId(),
                proposal.getProductId(),
                null,
                null,
                null,
                proposal.getItemName(),
                proposal.getDescription(),
                proposal.getQuantity(),
                BigDecimal.valueOf(proposal.getTotalAmount()),
                null,
                proposal.getCreatedAt(),
                null,
                null
        );
    }

    public static TradeActivityResponse fromEstimate(
            EstimateResponseDTO estimate,
            Direction direction
    ) {
        return new TradeActivityResponse(
                activityId(ActivityType.ESTIMATE, estimate.getId()),
                ActivityType.ESTIMATE,
                estimate.getId(),
                direction,
                estimate.getStatus().name(),
                null,
                estimate.getProductId(),
                null,
                null,
                null,
                estimate.getItemName(),
                estimate.getDescription(),
                estimate.getQuantity(),
                estimate.getBudget(),
                estimate.getEventDateTime(),
                estimate.getCreatedAt(),
                null,
                null
        );
    }

    public static TradeActivityResponse fromQuote(
            Quote quote,
            Payment payment,
            Direction direction
    ) {
        return fromQuote(quote, payment, direction, null);
    }

    public static TradeActivityResponse fromQuote(
            Quote quote,
            Payment payment,
            Direction direction,
            ProposalResponseDTO proposal
    ) {
        return new TradeActivityResponse(
                activityId(ActivityType.QUOTE, quote.getId()),
                ActivityType.QUOTE,
                quote.getId(),
                direction,
                quote.getStatus().name(),
                proposal == null ? null : proposal.getRequestId(),
                proposal == null ? null : proposal.getProductId(),
                quote.getId(),
                payment == null ? null : payment.getId(),
                quote.getChatRoomId(),
                proposal == null ? "견적 거래 #" + quote.getId() : proposal.getItemName(),
                quote.getAdditionalNotes() != null
                        ? quote.getAdditionalNotes()
                        : proposal == null ? null : proposal.getDescription(),
                quote.getQuantity(),
                quote.getTotalAmount() == null ? null : BigDecimal.valueOf(quote.getTotalAmount()),
                null,
                quote.getCreatedAt(),
                payment == null ? null : payment.getStatus().name(),
                payment == null ? null : payment.getPaidAt()
        );
    }

    private static String activityId(ActivityType sourceType, Long sourceId) {
        return sourceType.name() + ":" + sourceId;
    }
}
