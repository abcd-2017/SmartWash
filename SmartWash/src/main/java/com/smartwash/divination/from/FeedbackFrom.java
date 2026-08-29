package com.smartwash.divination.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 反馈 + 应验回填请求 DTO。
 */
@Data
public class FeedbackFrom {

    @NotNull(message = "解读 ID 不能为空")
    private Long interpretationId;

    /** 1-5 */
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer rating;

    /** 0未回填/1应验/2未验/3难说 */
    @Min(value = 0)
    @Max(value = 3)
    private Integer outcome;

    private String outcomeNote;
}
