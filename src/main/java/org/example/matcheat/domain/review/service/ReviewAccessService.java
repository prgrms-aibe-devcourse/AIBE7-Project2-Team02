package org.example.matcheat.domain.review.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.account.repository.SellerApplicationRepository;
import org.example.matcheat.domain.chat.entity.ChatRoom;
import org.example.matcheat.domain.chat.service.ChatService;
import org.example.matcheat.domain.payment.entity.Payment;
import org.example.matcheat.domain.payment.repository.PaymentRepository;
import org.example.matcheat.domain.product.service.ProductImageStorageService;
import org.example.matcheat.domain.product.service.ProductService;
import org.example.matcheat.domain.proposal.entity.Proposal;
import org.example.matcheat.domain.proposal.repository.ProposalRepository;
import org.example.matcheat.domain.quote.entity.Quote;
import org.example.matcheat.domain.quote.repository.QuoteRepository;
import org.example.matcheat.domain.review.dto.ReviewCreateDTO;
import org.example.matcheat.domain.review.dto.ReviewEligibilityDTO;
import org.example.matcheat.domain.review.dto.ReviewResponseDTO;
import org.example.matcheat.domain.review.entity.ReviewEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * 로그인 사용자(accountId)를 기준으로 Review에 대한 권한과 자격을 검증하는 서비스이다.
 * <p>
 * 실제 Review 저장과 조회는 {@link ReviewService}가 담당하고, 이 서비스는
 * - 결제(Payment)가 완료(COMPLETED) 상태이고 요청자가 그 결제의 구매자 본인인지
 * - 이미 리뷰를 작성한 결제 건은 아닌지
 * - 이 거래가 등록 상품에서 시작됐다면(Payment → Quote → ChatRoom.originType == PROPOSAL → Proposal.productId)
 *   상품명을 찾아서 화면에 보여주고, 그 상품의 평점(ProductEntity.ratingAvg)을 재계산한다.
 * 를 검증·조립한 뒤 ReviewService에 위임한다.
 * <p>
 * Payment/Quote/ChatRoom/Proposal은 전부 다른 도메인 소유이며, 이 서비스는 읽기 전용으로만
 * 참조한다 — 어떤 파일도 수정하지 않는다.
 */
public class ReviewAccessService {

    private static final String FALLBACK_ITEM_NAME = "채팅으로 협의한 주문";

    private final ReviewService reviewService;
    private final PaymentRepository paymentRepository;
    private final QuoteRepository quoteRepository;
    private final ChatService chatService;
    private final ProposalRepository proposalRepository;
    private final ProductService productService;
    private final ProductImageStorageService productImageStorageService;
    private final SellerApplicationRepository sellerApplicationRepository;

    /**
     * 결제가 완료된 거래에 대해 리뷰를 작성한다. 요청자가 그 결제의 구매자 본인이고,
     * 아직 리뷰를 작성하지 않은 경우에만 허용한다.
     */
    @Transactional
    public ReviewResponseDTO create(ReviewCreateDTO dto, MultipartFile imageFile, Long requesterAccountId) {
        if (requesterAccountId == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        Payment payment = loadPayment(dto.getPaymentId());
        payment.validatePayer(requesterAccountId);

        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new IllegalStateException("결제가 완료된 거래에만 리뷰를 작성할 수 있습니다.");
        }

        if (reviewService.existsByPaymentId(dto.getPaymentId())) {
            throw new IllegalStateException("이미 이 거래에 대한 리뷰를 작성했습니다.");
        }

        ResolvedItem resolvedItem = resolveItem(payment);
        String imageUrl = storeImageOrNull(imageFile);

        ReviewEntity saved = reviewService.create(
                dto.getPaymentId(),
                requesterAccountId,
                payment.getSellerId(),
                resolvedItem.productId(),
                dto.getRating(),
                dto.getContent(),
                imageUrl
        );

        if (resolvedItem.productId() != null) {
            refreshProductRating(resolvedItem.productId());
        }

        return ReviewResponseDTO.from(saved, resolvedItem.itemName()).withOwner(true);
    }

    /**
     * 특정 판매자가 받은 리뷰 목록을 최신순으로 조회한다. viewerAccountId는 각 리뷰의
     * 작성자 본인 여부(owner)를 계산하는 데만 쓰인다.
     */
    public List<ReviewResponseDTO> findBySellerId(Long sellerId, Long viewerAccountId) {
        return reviewService.findBySellerId(sellerId).stream()
                .map(review -> ReviewResponseDTO
                        .from(review, resolveItemName(review.getProductId(), review.getPaymentId()))
                        .withOwner(viewerAccountId != null && viewerAccountId.equals(review.getBuyerId())))
                .toList();
    }

    /**
     * 이 결제 건에 리뷰를 작성할 수 있는 상태인지 확인한다. 화면에서 "리뷰 작성" 버튼을
     * 보여줄지 판단하는 용도이다. 본인의 결제 건이 아니면 예외를 던진다.
     */
    public ReviewEligibilityDTO checkEligibility(Long paymentId, Long requesterAccountId) {
        if (requesterAccountId == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        Payment payment = loadPayment(paymentId);
        payment.validatePayer(requesterAccountId);

        boolean completed = payment.getStatus() == Payment.PaymentStatus.COMPLETED;
        boolean alreadyReviewed = reviewService.existsByPaymentId(paymentId);

        return new ReviewEligibilityDTO(completed && !alreadyReviewed, completed, alreadyReviewed);
    }

    /**
     * 주어진 결제 ID 목록 중, 이미 리뷰가 작성된 결제 ID만 골라 반환한다. 마이페이지처럼
     * 여러 결제 건을 한 화면에 나열할 때, 건마다 eligibility API를 따로 부르지 않고
     * 이 API 하나로 "리뷰 작성" 버튼 노출 여부를 한 번에 판단할 수 있게 해준다.
     */
    public List<Long> findExistingPaymentIds(List<Long> paymentIds, Long requesterAccountId) {
        if (requesterAccountId == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        return reviewService.findExistingPaymentIds(paymentIds);
    }

    /**
     * 상품 ID로 그 상품을 등록한 판매자의 seller_profiles PK를 알아낸다.
     * 상품 상세 화면에서 "판매자 리뷰 보기" 링크가 sellerId(seller_profiles PK)를 직접
     * 노출하지 않고도 목록 화면으로 이동할 수 있도록, 서버가 대신 이 값을 찾아준다.
     */
    public Long resolveSellerProfileIdForProduct(Long productId) {
        Long ownerAccountId = productService.findOwnerAccountId(productId);

        return sellerApplicationRepository.findByUserId(ownerAccountId)
                .map(SellerApplicationRepository.SellerApplication::sellerId)
                .orElseThrow(() -> new IllegalArgumentException("판매자 정보를 찾을 수 없습니다."));
    }

    /**
     * paymentId로 결제를 조회한다. 존재하지 않으면 예외를 던진다.
     */
    private Payment loadPayment(Long paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId는 필수입니다.");
        }

        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 결제입니다. id=" + paymentId));
    }

    /**
     * Payment → Quote → ChatRoom까지 이어간 뒤, ChatRoom의 originType에 따라 분기해서
     * 상품명과 상품 ID를 찾는다. 지금은 PROPOSAL만 실제로 연결되고, INQUIRY(문의로 시작한 채팅)와
     * 연결이 끊기는 경우(채팅 없는 견적 등)는 전부 대체 문구를 쓴다.
     * <p>
     * [확장 지점] ChatRoom.OriginType에 ESTIMATE가 추가되면, 아래 switch에 case ESTIMATE ->
     * resolveFromEstimate(chatRoom.getEstimateId())를 추가하면 된다. originType이 enum이라
     * 새 값이 생기면 이 switch가 컴파일 에러를 내서, 여기 고치는 걸 깜빡할 수가 없다.
     * Estimate도 Proposal과 마찬가지로 itemName/productId를 그대로 갖고 있으니
     * resolveFromProposal()과 같은 패턴을 그대로 재사용하면 된다.
     */
    private ResolvedItem resolveItem(Payment payment) {
        Quote quote = quoteRepository.findById(payment.getQuoteId()).orElse(null);
        if (quote == null || quote.getChatRoomId() == null) {
            return ResolvedItem.fallback();
        }

        ChatRoom chatRoom;
        try {
            chatRoom = chatService.getChatRoomEntity(quote.getChatRoomId());
        } catch (IllegalArgumentException exception) {
            return ResolvedItem.fallback();
        }

        return switch (chatRoom.getOriginType()) {
            case PROPOSAL -> resolveFromProposal(chatRoom.getProposalId());
            case INQUIRY -> ResolvedItem.fallback();
        };
    }

    /**
     * Proposal ID로 상품명과 상품 ID를 찾는다. Proposal이 없거나 ID 자체가 없으면 대체 문구를 반환한다.
     */
    private ResolvedItem resolveFromProposal(Long proposalId) {
        if (proposalId == null) {
            return ResolvedItem.fallback();
        }

        return proposalRepository.findById(proposalId)
                .map(proposal -> new ResolvedItem(proposal.getItemName(), proposal.getProductId()))
                .orElseGet(ResolvedItem::fallback);
    }

    /**
     * 리뷰 목록 조회용으로, 이미 저장된 리뷰의 productId를 기준으로 상품명을 다시 찾는다.
     * productId가 없으면(등록 상품과 연결 안 된 리뷰) 대체 문구를 반환한다.
     */
    private String resolveItemName(Long productId, Long paymentId) {
        if (productId == null) {
            return FALLBACK_ITEM_NAME;
        }

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            return FALLBACK_ITEM_NAME;
        }

        return resolveItem(payment).itemName();
    }

    /**
     * 상품의 리뷰 전체를 다시 조회해 평균을 새로 계산하고 ProductEntity.ratingAvg에 반영한다.
     * 증분 계산이 아니라 매번 전체를 다시 평균 내는 방식이라 누적 오차가 없다.
     */
    private void refreshProductRating(Long productId) {
        List<Integer> ratings = reviewService.findRatingsByProductId(productId);
        double average = ratings.stream().mapToInt(Integer::intValue).average().orElse(0.0);

        productService.refreshRatingAvg(productId, average);
    }

    /**
     * 리뷰 이미지 파일이 있으면 저장소에 저장해 URL을 반환하고, 없으면 null을 반환한다.
     */
    private String storeImageOrNull(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        try {
            return productImageStorageService.storeImage(imageFile);
        } catch (IOException e) {
            throw new IllegalStateException("리뷰 이미지를 저장하지 못했습니다.", e);
        }
    }

    /**
     * Proposal까지 이어가서 찾은 상품명과 상품 ID를 담는다. 연결이 끊기면 fallback()으로 만든다.
     */
    private record ResolvedItem(String itemName, Long productId) {
        static ResolvedItem fallback() {
            return new ResolvedItem(FALLBACK_ITEM_NAME, null);
        }
    }
}
