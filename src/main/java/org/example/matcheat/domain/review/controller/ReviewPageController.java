package org.example.matcheat.domain.review.controller;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.review.service.ReviewAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
/**
 * 리뷰 화면(Thymeleaf 페이지) 라우팅만 담당하는 컨트롤러이다. 실제 데이터는
 * 각 템플릿의 JS가 /api/v1/reviews 계열 API를 직접 호출해 채운다.
 */
public class ReviewPageController {

    private final ReviewAccessService reviewAccessService;

    /**
     * 리뷰 작성 페이지로 이동한다. paymentId는 페이지 내 JS가 쿼리스트링에서 직접 읽어 사용한다.
     */
    @GetMapping("/reviews/new")
    public String createPage() {
        return "review/create";
    }

    /**
     * 특정 판매자가 받은 리뷰 목록 페이지로 이동한다. sellerId는 페이지 내 JS가
     * 쿼리스트링에서 직접 읽어 사용한다.
     */
    @GetMapping("/reviews")
    public String listPage() {
        return "review/list";
    }

    /**
     * 상품 ID만으로 그 상품 판매자의 리뷰 목록 화면으로 이동한다. 상품 상세 화면이
     * seller_profiles PK를 직접 알 필요 없이 "판매자 리뷰 보기" 링크를 걸 수 있도록,
     * 서버가 대신 productId를 판매자로 변환해 리다이렉트해준다.
     */
    @GetMapping("/reviews/by-product/{productId}")
    public String redirectToSellerReviews(@PathVariable Long productId) {
        Long sellerProfileId = reviewAccessService.resolveSellerProfileIdForProduct(productId);

        return "redirect:/reviews?sellerId=" + sellerProfileId;
    }
}
