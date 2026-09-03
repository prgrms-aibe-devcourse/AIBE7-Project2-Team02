package org.example.matcheat.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.account.service.TradeAccountValidationService;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.repository.ChatRoomRepository;
import org.example.matcheat.domain.estimate.service.EstimateAccessService;
import org.example.matcheat.domain.order.dto.TradeActivityResponse;
import org.example.matcheat.domain.payment.entity.Payment;
import org.example.matcheat.domain.payment.repository.PaymentRepository;
import org.example.matcheat.domain.proposal.dto.ProposalResponseDTO;
import org.example.matcheat.domain.proposal.service.ProposalAccessService;
import org.example.matcheat.domain.quote.entity.Quote;
import org.example.matcheat.domain.quote.repository.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeActivityQueryService {
    private final ProposalAccessService proposals;
    private final EstimateAccessService estimates;
    private final QuoteRepository quoteRepository;
    private final PaymentRepository paymentRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final TradeAccountValidationService accounts;

    public List<TradeActivityResponse> findPurchases(long accountId) {
        accounts.requireActiveUser(accountId);

        List<TradeActivityResponse> activities = new ArrayList<>();
        List<ProposalResponseDTO> receivedProposals = proposals.findReceived(accountId);
        estimates.findSentByMe(accountId).stream()
                .map(estimate -> TradeActivityResponse.fromEstimate(
                        estimate, TradeActivityResponse.Direction.SENT))
                .forEach(activities::add);

        List<Quote> quotes = quoteRepository.findAllByBuyerIdOrderByCreatedAtDesc(accountId);
        addCurrentTransactions(activities, receivedProposals, quotes, true);
        return sortNewestFirst(activities);
    }

    public List<TradeActivityResponse> findSales(long accountId) {
        long sellerId = accounts.approvedSellerIdForUser(accountId);

        List<TradeActivityResponse> activities = new ArrayList<>();
        List<ProposalResponseDTO> sentProposals = proposals.findSent(accountId);
        estimates.findReceivedByMe(accountId).stream()
                .map(estimate -> TradeActivityResponse.fromEstimate(
                        estimate, TradeActivityResponse.Direction.RECEIVED))
                .forEach(activities::add);

        List<Quote> quotes = quoteRepository.findAllBySellerIdOrderByCreatedAtDesc(sellerId);
        addCurrentTransactions(activities, sentProposals, quotes, false);
        return sortNewestFirst(activities);
    }

    private void addCurrentTransactions(
            List<TradeActivityResponse> activities,
            List<ProposalResponseDTO> proposalActivities,
            List<Quote> quotes,
            boolean buyerView
    ) {
        Map<Long, ChatRoom> chatRooms = chatRoomsById(quotes);
        List<Quote> currentQuotes = currentQuotes(quotes, chatRooms);
        Map<Long, Payment> paymentsByQuoteId = paymentsByQuoteId(currentQuotes);
        Map<Long, ProposalResponseDTO> proposalsById = proposalActivities.stream()
                .collect(Collectors.toMap(ProposalResponseDTO::getId, Function.identity()));
        Set<Long> supersededProposalIds = new HashSet<>();

        currentQuotes.stream()
                .map(quote -> TradeActivityResponse.fromQuote(
                        quote,
                        paymentsByQuoteId.get(quote.getId()),
                        directionFor(quote, buyerView),
                        linkedProposal(quote, chatRooms, proposalsById, supersededProposalIds)))
                .forEach(activities::add);

        proposalActivities.stream()
                .filter(proposal -> !supersededProposalIds.contains(proposal.getId()))
                .map(proposal -> TradeActivityResponse.fromProposal(
                        proposal,
                        buyerView
                                ? TradeActivityResponse.Direction.RECEIVED
                                : TradeActivityResponse.Direction.SENT))
                .forEach(activities::add);
    }

    private Map<Long, ChatRoom> chatRoomsById(Collection<Quote> quotes) {
        List<Long> chatRoomIds = quotes.stream()
                .map(Quote::getChatRoomId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (chatRoomIds.isEmpty()) {
            return Map.of();
        }
        return chatRoomRepository.findAllById(chatRoomIds).stream()
                .collect(Collectors.toMap(ChatRoom::getId, Function.identity()));
    }

    private static List<Quote> currentQuotes(List<Quote> quotes, Map<Long, ChatRoom> chatRooms) {
        Map<Long, Quote> latestByChatRoom = new HashMap<>();
        List<Quote> standalone = new ArrayList<>();

        for (Quote quote : quotes) {
            if (quote.getChatRoomId() == null) {
                standalone.add(quote);
                continue;
            }
            latestByChatRoom.merge(quote.getChatRoomId(), quote, TradeActivityQueryService::newerQuote);
        }

        List<Quote> current = new ArrayList<>(standalone);
        latestByChatRoom.forEach((chatRoomId, latestQuote) -> {
            ChatRoom chatRoom = chatRooms.get(chatRoomId);
            if (chatRoom == null || chatRoom.getQuoteId() == null) {
                current.add(latestQuote);
                return;
            }
            quotes.stream()
                    .filter(quote -> chatRoom.getQuoteId().equals(quote.getId()))
                    .findFirst()
                    .ifPresentOrElse(current::add, () -> current.add(latestQuote));
        });
        return current;
    }

    private static Quote newerQuote(Quote left, Quote right) {
        Comparator<Quote> comparator = Comparator
                .comparing(Quote::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Quote::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    private static ProposalResponseDTO linkedProposal(
            Quote quote,
            Map<Long, ChatRoom> chatRooms,
            Map<Long, ProposalResponseDTO> proposalsById,
            Set<Long> supersededProposalIds
    ) {
        if (quote.getChatRoomId() == null) {
            return null;
        }
        ChatRoom chatRoom = chatRooms.get(quote.getChatRoomId());
        if (chatRoom == null || chatRoom.getProposalId() == null) {
            return null;
        }
        ProposalResponseDTO proposal = proposalsById.get(chatRoom.getProposalId());
        if (proposal != null) {
            supersededProposalIds.add(proposal.getId());
        }
        return proposal;
    }

    private Map<Long, Payment> paymentsByQuoteId(Collection<Quote> quotes) {
        List<Long> quoteIds = quotes.stream()
                .map(Quote::getId)
                .filter(id -> id != null)
                .toList();
        if (quoteIds.isEmpty()) {
            return Map.of();
        }
        return paymentRepository.findAllByQuoteIdIn(quoteIds).stream()
                .collect(Collectors.toMap(Payment::getQuoteId, Function.identity(), (left, right) -> right));
    }

    private static TradeActivityResponse.Direction directionFor(Quote quote, boolean buyerView) {
        boolean sent = buyerView
                ? quote.getSenderRole() == Quote.SenderRole.BUYER
                : quote.getSenderRole() == Quote.SenderRole.SELLER;
        return sent ? TradeActivityResponse.Direction.SENT : TradeActivityResponse.Direction.RECEIVED;
    }

    private static List<TradeActivityResponse> sortNewestFirst(List<TradeActivityResponse> activities) {
        activities.sort((left, right) -> compareNullableDesc(sortAt(left), sortAt(right)));
        return List.copyOf(activities);
    }

    private static LocalDateTime sortAt(TradeActivityResponse activity) {
        return activity.paidAt() != null ? activity.paidAt() : activity.createdAt();
    }

    private static int compareNullableDesc(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right == null ? 0 : 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }
}
