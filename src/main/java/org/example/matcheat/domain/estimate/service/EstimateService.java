package org.example.matcheat.domain.estimate.service;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.estimate.dto.EstimateResponseDTO;
import org.example.matcheat.domain.estimate.entity.EstimateEntity;
import org.example.matcheat.domain.estimate.repository.EstimateRepository;
import org.example.matcheat.domain.order.enums.BudgetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 견적(Estimate)의 순수 CRUD만 담당하는 서비스이다. accountId나 권한 개념을 전혀 모르며,
 * 이미 검증이 끝난 값만 받아서 저장/조회한다. 권한 검증과 값 보정(resolve)은
 * {@link EstimateAccessService}가 담당한다. Proposal 도메인의 ProposalService와 같은 위치이다.
 */
public class EstimateService {

    private final EstimateRepository estimateRepository;

    /**
     * 검증과 값 보정이 끝난 견적 정보를 그대로 저장한다.
     */
    @Transactional
    public EstimateResponseDTO create(
            Long requestId,
            Long sellerId,
            Long productId,
            String description,
            BigDecimal budget,
            BudgetType budgetType,
            String itemName,
            Integer quantity,
            LocalDateTime eventDateTime,
            String estimateImage,
            String deliveryAddress,
            String deliveryAddressDetail,
            Double latitude,
            Double longitude
    ) {
        EstimateEntity estimate = EstimateEntity.create(
                description,
                requestId,
                sellerId,
                productId,
                budget,
                budgetType,
                itemName,
                quantity,
                eventDateTime,
                estimateImage,
                deliveryAddress,
                deliveryAddressDetail,
                latitude,
                longitude
        );

        return EstimateResponseDTO.from(estimateRepository.save(estimate));
    }

    /**
     * 견적 ID로 단건을 조회한다. 존재하지 않으면 예외를 던진다.
     */
    @Transactional(readOnly = true)
    public EstimateResponseDTO findById(Long id) {
        return EstimateResponseDTO.from(findEntityOrThrow(id));
    }

    /**
     * 전체 견적 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<EstimateResponseDTO> findAll() {
        return estimateRepository.findAll().stream()
                .map(EstimateResponseDTO::from)
                .toList();
    }

    /**
     * 특정 판매자가 받은 견적 목록을 최신순으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<EstimateResponseDTO> findBySellerId(Long sellerId) {
        return estimateRepository.findAllBySellerIdOrderByIdDesc(sellerId).stream()
                .map(EstimateResponseDTO::from)
                .toList();
    }

    /**
     * 특정 주문 요청(requestId)에 달린 견적 목록을 최신순으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<EstimateResponseDTO> findByRequestId(Long requestId) {
        return estimateRepository.findAllByRequestIdOrderByIdDesc(requestId).stream()
                .map(EstimateResponseDTO::from)
                .toList();
    }

    /**
     * 견적 엔티티를 ID로 조회한다. 존재하지 않으면 예외를 던진다.
     */
    private EstimateEntity findEntityOrThrow(Long id) {
        return estimateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("견적 정보를 찾을 수 없습니다. ID: " + id));
    }
}
