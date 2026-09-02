package org.example.matcheat.domain.proposal.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.proposal.dto.ProposalCreateDTO;
import org.example.matcheat.domain.proposal.dto.ProposalResponseDTO;
import org.example.matcheat.domain.proposal.service.ProposalAccessService;
import org.example.matcheat.domain.proposal.enums.ProposalStatus;
import org.example.matcheat.global.dto.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 로그인 사용자의 Proposal 등록과 조회 API를 제공한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProposalController {

    private final ProposalAccessService proposalAccessService;

    /**
     * 승인된 판매자가 특정 주문에 최초 제안을 보낸다.
     */
    @PostMapping("/requests/{requestId}/proposals")
    public ResponseEntity<ProposalResponseDTO> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long requestId,
            @Valid @RequestBody ProposalCreateDTO dto
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        ProposalResponseDTO response =
                proposalAccessService.create(
                        requestId,
                        userId,
                        dto
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * 구매자가 특정 주문에 받은 제안 목록을 조회한다.
     */
    @GetMapping("/requests/{requestId}/proposals")
    public ResponseEntity<List<ProposalResponseDTO>> findByRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long requestId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return ResponseEntity.ok(
                proposalAccessService.findReceivedByRequest(
                        requestId,
                        userId
                )
        );
    }

    /**
     * 현재 구매자가 자신의 주문들에 받은 모든 제안을 조회한다.
     */
    @GetMapping("/proposals/received")
    public ResponseEntity<?> findReceived(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) ProposalStatus status
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        List<ProposalResponseDTO> values = proposalAccessService.findReceived(userId);
        if (page == null && size == null && status == null) return ResponseEntity.ok(values);
        return ResponseEntity.ok(PageResponse.from(values, page == null ? 0 : page, size == null ? 20 : size,
                value -> status == null || status == value.getStatus()));
    }

    /**
     * 현재 판매자가 보낸 모든 제안을 조회한다.
     */
    @GetMapping("/proposals/sent")
    public ResponseEntity<?> findSent(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) ProposalStatus status
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        List<ProposalResponseDTO> values = proposalAccessService.findSent(userId);
        if (page == null && size == null && status == null) return ResponseEntity.ok(values);
        return ResponseEntity.ok(PageResponse.from(values, page == null ? 0 : page, size == null ? 20 : size,
                value -> status == null || status == value.getStatus()));
    }

    /**
     * 현재 사용자가 제안을 보낼 수 있는 승인 판매자인지 확인한다.
     */
    @GetMapping("/proposals/eligibility")
    public ResponseEntity<Void> checkEligibility(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        proposalAccessService.validateSellerEligibility(userId);

        return ResponseEntity.noContent().build();
    }
}
