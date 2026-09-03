package org.example.matcheat.domain.product.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.product.dto.ProductCreateDTO;
import org.example.matcheat.domain.product.dto.ProductResponseDTO;
import org.example.matcheat.domain.product.dto.ProductUpdateDTO;
import org.example.matcheat.domain.product.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
/**
 * 판매 조건 관련 API 요청을 받아 서비스 계층에 전달하는 컨트롤러이다.
 */
public class ProductController {
    private final ProductService productService;

    /**
     * 새로운 판매 조건을 등록한다. 승인된 판매자만 가능하다.
     */
    @PostMapping
    public ResponseEntity<ProductResponseDTO> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProductCreateDTO dto
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        ProductResponseDTO response = productService.create(dto, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * 새로운 판매 조건을 등록한다. (multipart) 승인된 판매자만 가능하다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> createMultipart(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("product") ProductCreateDTO dto,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        ProductResponseDTO response = productService.create(dto, imageFile, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * 판매 조건 ID로 단건 조회한다.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> findById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        ProductResponseDTO response = productService.findById(id, viewerId(jwt));

        return ResponseEntity.ok(response);
    }

    /**
     * 현재 로그인 사용자가 등록한 판매 조건을 조회한다.
     */
    @GetMapping("/mine")
    public ResponseEntity<List<ProductResponseDTO>> findMine(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return ResponseEntity.ok(
                productService.findMine(userId)
        );
    }

    /**
     * 등록된 모든 판매 조건을 조회한다.
     */
    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> findAll(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<ProductResponseDTO> response = productService.findAll(viewerId(jwt));

        return ResponseEntity.ok(response);
    }

    /**
     * 수량, 카테고리, 1인분 가격 조건으로 판매 조건을 조회한다.
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponseDTO>> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String quantity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String servingPrice
    ) {
        List<ProductResponseDTO> response = productService.search(quantity, category, servingPrice, viewerId(jwt));

        return ResponseEntity.ok(response);
    }

    /**
     * 판매 조건 ID에 해당하는 항목을 부분 수정한다. 본인 소유의 판매 조건이거나 관리자만 가능하다.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> update (
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateDTO dto
    ) {
       Long userId = Long.valueOf(jwt.getSubject());
       boolean isAdmin = "ADMIN".equals(jwt.getClaimAsString("role"));

       ProductResponseDTO response = productService.update(id, dto, null, userId, isAdmin);

       return ResponseEntity.ok(response);
    }

    /**
     * 판매 조건 ID에 해당하는 항목을 부분 수정한다. (multipart) 본인 소유의 판매 조건이거나 관리자만 가능하다.
     */
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> updateMultipart(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestPart("product") ProductUpdateDTO dto,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        boolean isAdmin = "ADMIN".equals(jwt.getClaimAsString("role"));

        ProductResponseDTO response = productService.update(id, dto, imageFile, userId, isAdmin);

        return ResponseEntity.ok(response);
    }

    /**
     * 판매 조건을 소프트 삭제한다. 본인 소유의 판매 조건이거나 관리자만 가능하다.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> softDelete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        boolean isAdmin = "ADMIN".equals(jwt.getClaimAsString("role"));

        ProductResponseDTO response = productService.softDelete(id, userId, isAdmin);

        return ResponseEntity.ok(response);
    }

    /**
     * JWT가 있으면 sub 클레임에서 계정 ID를 꺼내고, 없으면(비로그인) null을 반환한다.
     */
    private static Long viewerId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }
}
