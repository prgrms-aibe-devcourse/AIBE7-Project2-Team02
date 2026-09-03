package org.example.matcheat.domain.review.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.review.dto.ReviewCreateDTO;
import org.example.matcheat.domain.review.dto.ReviewEligibilityDTO;
import org.example.matcheat.domain.review.dto.ReviewResponseDTO;
import org.example.matcheat.domain.review.service.ReviewAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
/**
 * 리뷰 관련 API 요청을 받아 서비스 계층에 전달하는 컨트롤러이다.
 * 권한/자격 검증은 {@link ReviewAccessService}에 위임한다.
 */
public class ReviewController {

    private final ReviewAccessService reviewAccessService;

    /**
     * 결제가 완료된 거래에 대해 리뷰를 작성한다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewResponseDTO> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("review") ReviewCreateDTO dto,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        ReviewResponseDTO response = reviewAccessService.create(dto, imageFile, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 특정 판매자가 받은 리뷰 목록을 조회한다.
     */
    @GetMapping
    public ResponseEntity<List<ReviewResponseDTO>> findBySellerId(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long sellerId
    ) {
        Long viewerId = Long.valueOf(jwt.getSubject());

        return ResponseEntity.ok(reviewAccessService.findBySellerId(sellerId, viewerId));
    }

    /**
     * 이 결제 건에 리뷰를 작성할 수 있는 상태인지 확인한다.
     */
    @GetMapping("/eligibility")
    public ResponseEntity<ReviewEligibilityDTO> checkEligibility(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long paymentId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return ResponseEntity.ok(reviewAccessService.checkEligibility(paymentId, userId));
    }

    /**
     * 주어진 결제 ID 목록 중, 이미 리뷰가 작성된 결제 ID만 골라 반환한다.
     * 마이페이지 구매 내역처럼 여러 결제 건을 한 번에 나열할 때, eligibility API를
     * 건마다 따로 부르지 않고 이 API 하나로 "리뷰 작성" 버튼 노출 여부를 판단할 수 있다.
     */
    @GetMapping("/existing-payment-ids")
    public ResponseEntity<List<Long>> findExistingPaymentIds(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam List<Long> paymentIds
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return ResponseEntity.ok(reviewAccessService.findExistingPaymentIds(paymentIds, userId));
    }
}
