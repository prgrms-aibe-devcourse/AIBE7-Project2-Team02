package org.example.matcheat.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.account.enums.AccountReportTargetType;
import org.example.matcheat.domain.chat.entity.ChatMessage;
import org.example.matcheat.domain.chat.repository.ChatMessageRepository;
import org.example.matcheat.domain.estimate.repository.EstimateRepository;
import org.example.matcheat.domain.order.repository.OrderRequestRepository;
import org.example.matcheat.domain.product.repository.ProductRepository;
import org.example.matcheat.domain.proposal.repository.ProposalRepository;
import org.example.matcheat.domain.quote.repository.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountReportSnapshotService {
    private static final int CHAT_SNAPSHOT_LIMIT = 30;

    private final ChatMessageRepository chatMessages;
    private final QuoteRepository quotes;
    private final ProductRepository products;
    private final OrderRequestRepository orderRequests;
    private final ProposalRepository proposals;
    private final EstimateRepository estimates;

    public String capture(AccountReportTargetType type, long id) {
        return switch (type) {
            case CHAT_ROOM -> chatSnapshot(id);
            case PRODUCT -> products.findById(id)
                    .map(value -> "productName=" + value.getProductName() + "\ndescription=" + value.getDescription()
                            + "\nimageUrl=" + value.getImageUrl())
                    .orElse(null);
            case ORDER_REQUEST -> orderRequests.findById(id)
                    .map(value -> "title=" + value.getTitle() + "\ndescription=" + value.getDescription()
                            + "\nreferenceImageUrl=" + value.getReferenceImageUrl())
                    .orElse(null);
            case PROPOSAL -> proposals.findById(id)
                    .map(value -> "itemName=" + value.getItemName() + "\ndescription=" + value.getDescription())
                    .orElse(null);
            case ESTIMATE -> estimates.findById(id)
                    .map(value -> "itemName=" + value.getItemName() + "\ndescription=" + value.getDescription()
                            + "\nestimateImage=" + value.getEstimateImage())
                    .orElse(null);
            case QUOTE -> quotes.findById(id)
                    .map(value -> "quantity=" + value.getQuantity() + "\nunitPrice=" + value.getUnitPrice()
                            + "\ndeliveryFee=" + value.getDeliveryFee() + "\nnotes=" + value.getAdditionalNotes())
                    .orElse(null);
        };
    }

    private String chatSnapshot(long chatRoomId) {
        List<ChatMessage> history = chatMessages.findHistoryWithFilesByChatRoomId(chatRoomId);
        int start = Math.max(0, history.size() - CHAT_SNAPSHOT_LIMIT);
        return history.subList(start, history.size()).stream()
                .map(message -> message.getSenderId() + " | " + message.getCreatedAt() + " | "
                        + message.getMessageType() + " | " + message.getContent())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(no messages)");
    }
}
