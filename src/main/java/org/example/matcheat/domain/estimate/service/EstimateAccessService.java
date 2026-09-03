/* Created by DINKIssTyle on 2026. Copyright (C) 2026 DINKI'ssTyle. All rights reserved. */
package org.example.matcheat.domain.estimate.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.common.location.GeocodingService;
import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.repository.SellerApplicationRepository;
import org.example.matcheat.domain.estimate.dto.EstimateCreateDTO;
import org.example.matcheat.domain.estimate.dto.EstimateResponseDTO;
import org.example.matcheat.domain.product.service.ProductService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * 로그인 사용자(accountId)를 기준으로 Estimate에 대한 권한과 자격을 검증하는 서비스이다.
 * <p>
 * 실제 Estimate 저장과 조회는 {@link EstimateService}가 담당하고,
 * 이 서비스는 요청자가 구매자/판매자 본인인지, 상품의 판매자가 승인된 상태인지를 검증하고
 * 주소를 지오코딩한 뒤 EstimateService에 위임한다. ProposalAccessService와 같은 위치이다.
 * <p>
 * request_id는 더 이상 OrderRequest를 가리키는 FK가 아니라, 요청자(구매자) 본인의 계정 ID를
 * 그대로 저장한다 — 구매자가 사전에 주문 요청을 등록하지 않았어도 견적을 요청할 수 있도록 하기 위함이다.
 * 그래서 견적에 필요한 값(예산, 행사일자, 항목명, 주소 등)은 전부 이 화면에서 직접 입력받는다.
 * <p>
 * sellerId는 클라이언트가 직접 보내지 않는다 — ProductResponseDTO가 판매자 계정 ID를 노출하지 않기
 * 때문에(보안상 이유), 구매자는 productId만 알려주고 서버가 그 상품의 판매자를 내부적으로 알아낸다.
 */
public class EstimateAccessService {

    private final EstimateService estimateService;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final ProductService productService;
    private final GeocodingService geocodingService;

    /**
     * 구매자가 특정 상품(productId)의 판매자에게 견적을 요청한다. 로그인한 본인의 accountId가
     * request_id로 그대로 저장된다. productId로 판매자 계정을 알아낸 뒤 승인된 판매자인지 검증하고,
     * 실제로 Estimate에 저장되는 sellerId는 그 계정에 연결된 seller_profiles PK로 변환한다
     * (domain/account의 다른 거래 도메인들과 동일한 판매자 식별 체계를 맞추기 위함).
     * 배송(행사) 주소를 지오코딩한 뒤 저장한다.
     */
    @Transactional
    public EstimateResponseDTO create(EstimateCreateDTO dto, Long requesterAccountId) {
        if (requesterAccountId == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        Long sellerAccountId = productService.findOwnerAccountId(dto.getProductId());
        Long sellerProfileId = requireApprovedSeller(sellerAccountId);

        GeocodingService.Coordinates coordinates = geocodingService.geocode(dto.getDeliveryAddress());

        EstimateResponseDTO created = estimateService.create(
                requesterAccountId,
                sellerProfileId,
                dto.getProductId(),
                dto.getDescription(),
                dto.getBudget(),
                dto.getBudgetType(),
                dto.getItemName(),
                dto.getQuantity(),
                dto.getEventDateTime(),
                dto.getEstimateImage(),
                dto.getDeliveryAddress(),
                dto.getDeliveryAddressDetail(),
                coordinates.latitude(),
                coordinates.longitude()
        );

        // 견적을 요청한 사람은 항상 이 견적의 구매자 본인이다.
        return created.withViewer(true, false);
    }

    /**
     * 견적 상세를 조회한다. 요청자가 이 견적의 구매자 또는 판매자인 경우에만 허용한다.
     */
    public EstimateResponseDTO findById(Long id, Long requesterAccountId) {
        EstimateResponseDTO estimate = estimateService.findById(id);

        return applyViewerOrDeny(estimate, requesterAccountId);
    }

    /**
     * 전체 견적 목록을 조회한다. 관리자만 허용한다.
     */
    public List<EstimateResponseDTO> findAll(boolean requesterIsAdmin) {
        if (!requesterIsAdmin) {
            throw new AccessDeniedException("전체 견적 목록은 관리자만 조회할 수 있습니다.");
        }

        return estimateService.findAll();
    }

    /**
     * 내가 구매자로서 보낸 견적 요청 목록을 조회한다. request_id가 곧 내 계정 ID이므로
     * 그 값으로 바로 조회한다.
     */
    public List<EstimateResponseDTO> findSentByMe(Long requesterAccountId) {
        if (requesterAccountId == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        return estimateService.findByRequestId(requesterAccountId).stream()
                .map(estimate -> estimate.withViewer(true, false))
                .toList();
    }

    /**
     * 내가 판매자로서 받은 견적 요청 목록을 조회한다. 내 계정 ID를 seller_profiles PK로
     * 변환한 뒤 그 PK로 조회한다. 판매자 프로필이 없으면 빈 목록을 반환한다.
     */
    public List<EstimateResponseDTO> findReceivedByMe(Long requesterAccountId) {
        if (requesterAccountId == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        return sellerApplicationRepository.findByUserId(requesterAccountId)
                .map(seller -> estimateService.findBySellerId(seller.sellerId()).stream()
                        .map(estimate -> estimate.withViewer(false, true))
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * 요청자가 이 견적의 구매자(request_id) 또는 판매자(sellerId를 계정 ID로 환산한 값) 본인인지 검증하고,
     * 통과하면 buyer/seller 여부를 채운 응답을 반환한다. 둘 다 아니면 예외를 던진다.
     */
    private EstimateResponseDTO applyViewerOrDeny(EstimateResponseDTO estimate, Long requesterAccountId) {
        if (requesterAccountId == null) {
            throw new AccessDeniedException("본인과 관련된 견적만 조회할 수 있습니다.");
        }

        boolean isBuyer = requesterAccountId.equals(estimate.getRequestId());
        if (isBuyer) {
            return estimate.withViewer(true, false);
        }

        Long sellerAccountId = sellerApplicationRepository.findUserIdBySellerId(estimate.getSellerId())
                .orElse(null);
        boolean isSeller = requesterAccountId.equals(sellerAccountId);
        if (isSeller) {
            return estimate.withViewer(false, true);
        }

        throw new AccessDeniedException("본인과 관련된 견적만 조회할 수 있습니다.");
    }

    /**
     * accountId가 승인된(APPROVED) 판매자인지 검증하고, 그 판매자의 seller_profiles PK를 반환한다.
     */
    private Long requireApprovedSeller(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("sellerId는 필수입니다.");
        }

        SellerApplicationRepository.SellerApplication seller = sellerApplicationRepository.findByUserId(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 판매자입니다. sellerId=" + accountId));

        if (seller.status() != SellerVerificationStatus.APPROVED) {
            throw new AccessDeniedException("승인된 판매자에게만 견적을 요청할 수 있습니다.");
        }

        return seller.sellerId();
    }
}
