package org.example.matcheat.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.account.enums.AccountReportTargetType;
import org.example.matcheat.domain.account.repository.SellerApplicationRepository;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.repository.ChatRoomRepository;
import org.example.matcheat.domain.estimate.entity.EstimateEntity;
import org.example.matcheat.domain.estimate.repository.EstimateRepository;
import org.example.matcheat.domain.order.entity.OrderRequest;
import org.example.matcheat.domain.order.repository.OrderRequestRepository;
import org.example.matcheat.domain.product.entity.ProductEntity;
import org.example.matcheat.domain.product.repository.ProductRepository;
import org.example.matcheat.domain.proposal.entity.Proposal;
import org.example.matcheat.domain.proposal.repository.ProposalRepository;
import org.example.matcheat.domain.quote.entity.Quote;
import org.example.matcheat.domain.quote.repository.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountReportTargetResolver {
    private final ChatRoomRepository chatRooms;
    private final QuoteRepository quotes;
    private final ProductRepository products;
    private final OrderRequestRepository orderRequests;
    private final ProposalRepository proposals;
    private final EstimateRepository estimates;
    private final SellerApplicationRepository sellers;

    public long resolveReportedUser(long reporterId, AccountReportTargetType targetType, long targetId) {
        return switch (targetType) {
            case CHAT_ROOM -> fromChatRoom(reporterId, requiredChatRoom(targetId));
            case QUOTE -> fromQuote(reporterId, requiredQuote(targetId));
            case PRODUCT -> fromProduct(reporterId, requiredProduct(targetId));
            case ORDER_REQUEST -> fromOrderRequest(reporterId, requiredOrderRequest(targetId));
            case PROPOSAL -> fromProposal(reporterId, requiredProposal(targetId));
            case ESTIMATE -> fromEstimate(reporterId, requiredEstimate(targetId));
        };
    }

    private long fromChatRoom(long reporterId, ChatRoom room) {
        return counterparty(reporterId, room.getBuyerId(), room.getSellerId());
    }

    private long fromQuote(long reporterId, Quote quote) {
        return counterparty(reporterId, quote.getBuyerId(), quote.getSellerId());
    }

    private long fromProduct(long reporterId, ProductEntity product) {
        return otherUser(reporterId, product.getOwnerAccountId());
    }

    private long fromOrderRequest(long reporterId, OrderRequest request) {
        Long sellerId = sellerIdForUser(reporterId);
        if (sellerId == null || !proposals.existsByRequestIdAndSellerId(request.getId(), sellerId)) {
            throw forbiddenTarget();
        }
        return otherUser(reporterId, request.getBuyerId());
    }

    private long fromProposal(long reporterId, Proposal proposal) {
        OrderRequest request = requiredOrderRequest(proposal.getRequestId());
        return counterparty(reporterId, request.getBuyerId(), proposal.getSellerId());
    }

    private long fromEstimate(long reporterId, EstimateEntity estimate) {
        return counterparty(reporterId, estimate.getRequestId(), estimate.getSellerId());
    }

    private long counterparty(long reporterId, long buyerId, long sellerId) {
        if (reporterId == buyerId) {
            return sellerAccountId(sellerId);
        }
        Long reporterSellerId = sellerIdForUser(reporterId);
        if (reporterSellerId != null && reporterSellerId == sellerId) {
            return buyerId;
        }
        throw forbiddenTarget();
    }

    private long otherUser(long reporterId, Long reportedUserId) {
        if (reportedUserId == null) {
            throw targetNotFound();
        }
        if (reporterId == reportedUserId) {
            throw new AccountApplicationException(AccountErrorCode.FORBIDDEN, "자기 자신을 신고할 수 없습니다.");
        }
        return reportedUserId;
    }

    private Long sellerIdForUser(long userId) {
        return sellers.findByUserId(userId)
                .map(SellerApplicationRepository.SellerApplication::sellerId)
                .orElse(null);
    }

    private long sellerAccountId(long sellerId) {
        return sellers.findUserIdBySellerId(sellerId).orElseThrow(AccountReportTargetResolver::targetNotFound);
    }

    private ChatRoom requiredChatRoom(long id) {
        return chatRooms.findById(id).orElseThrow(AccountReportTargetResolver::targetNotFound);
    }

    private Quote requiredQuote(long id) {
        return quotes.findById(id).orElseThrow(AccountReportTargetResolver::targetNotFound);
    }

    private ProductEntity requiredProduct(long id) {
        return products.findById(id).orElseThrow(AccountReportTargetResolver::targetNotFound);
    }

    private OrderRequest requiredOrderRequest(long id) {
        return orderRequests.findById(id).orElseThrow(AccountReportTargetResolver::targetNotFound);
    }

    private Proposal requiredProposal(long id) {
        return proposals.findById(id).orElseThrow(AccountReportTargetResolver::targetNotFound);
    }

    private EstimateEntity requiredEstimate(long id) {
        return estimates.findById(id).orElseThrow(AccountReportTargetResolver::targetNotFound);
    }

    private static AccountApplicationException targetNotFound() {
        return new AccountApplicationException(AccountErrorCode.REPORT_TARGET_NOT_FOUND, "신고 대상을 찾을 수 없습니다.");
    }

    private static AccountApplicationException forbiddenTarget() {
        // Hide whether a private target ID exists from non-participants.
        return targetNotFound();
    }
}
