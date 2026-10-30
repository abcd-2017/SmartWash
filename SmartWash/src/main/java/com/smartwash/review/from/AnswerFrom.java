package com.smartwash.review.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 复习评分请求 DTO。
 */
@Data
public class AnswerFrom {

    @NotNull(message = "cardId 不能为空")
    private Long cardId;

    /** 1=Again 2=Hard 3=Good 4=Easy */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分必须在 1..4 内")
    @Max(value = 4, message = "评分必须在 1..4 内")
    private Integer rating;

    /** 作答耗时毫秒，可空 */
    @Min(value = 0, message = "作答耗时不能为负")
    private Integer durationMs;
}
