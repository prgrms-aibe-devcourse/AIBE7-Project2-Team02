package org.example.matcheat.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
/**
 * 리뷰 작성 요청에서 사용하는 입력 DTO이다.
 */
public class ReviewCreateDTO {

    /** 리뷰를 남길 결제 ID */
    @NotNull
    private Long paymentId;

    /** 별점(1~5) */
    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    /** 리뷰 내용 */
    @NotBlank
    private String content;
}
