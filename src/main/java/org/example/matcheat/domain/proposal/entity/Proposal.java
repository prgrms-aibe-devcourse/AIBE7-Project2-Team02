package org.example.matcheat.domain.proposal.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.matcheat.domain.proposal.enums.ProposalStatus;

import java.time.LocalDateTime;

/**
 * 판매자가 구매 주문에 보내는 최초 수주 제안 정보를 저장하는 엔티티이다.
 */
@Entity
@Table(name = "proposals", indexes = {
        @Index(name = "idx_proposal_seller_status_created", columnList = "seller_id,status,created_at"),
        @Index(name = "idx_proposal_request_status_created", columnList = "request_id,status,created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    // 등록 상품으로 제안하면 상품 ID를 저장하고, 직접 입력 제안이면 null이다.
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "preparation_days", nullable = false)
    private Integer preparationDays;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Proposal(
            Long requestId,
            Long sellerId,
            Long productId,
            String itemName,
            Integer quantity,
            Long unitPrice,
            Long totalAmount,
            Integer preparationDays,
            String description
    ) {
        this.requestId = requestId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.preparationDays = preparationDays;
        this.description = description;
        this.status = ProposalStatus.SENT;
    }

    /**
     * 새 수주 제안을 생성한다.
     */
    public static Proposal create(
            Long requestId,
            Long sellerId,
            Long productId,
            String itemName,
            Integer quantity,
            Long unitPrice,
            Long totalAmount,
            Integer preparationDays,
            String description
    ) {
        return new Proposal(
                requestId,
                sellerId,
                productId,
                itemName,
                quantity,
                unitPrice,
                totalAmount,
                preparationDays,
                description
        );
    }

    /**
     * 제안이 처음 저장될 때 생성 시각을 기록한다.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
