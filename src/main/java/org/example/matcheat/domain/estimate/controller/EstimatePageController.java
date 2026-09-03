package org.example.matcheat.domain.estimate.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
/**
 * 견적 화면(Thymeleaf 페이지) 라우팅만 담당하는 컨트롤러이다. 실제 데이터는
 * 각 템플릿의 JS가 /api/v1/estimates 계열 API를 직접 호출해 채운다.
 */
public class EstimatePageController {

    /**
     * 견적 작성 페이지로 이동한다.
     */
    @GetMapping("/estimates/new")
    public String createPage() {
        return "estimate/create";
    }

    /**
     * 견적 상세 페이지로 이동한다. id는 페이지 내 JS가 URL에서 직접 읽어 사용한다.
     */
    @GetMapping("/estimates/{id}")
    public String detailPage() {
        return "estimate/detail";
    }
}
