package com.smartwash.divination.vo;

import lombok.Data;

/**
 * 用户反馈聚合行（盘感统计中间结果，single row）。
 * 口径：verify = 反馈 rating ≥ 4（5 档评分，≥4 记应验）。
 */
@Data
public class DivFeedbackAggVo {

    /** 反馈总数 */
    private Long feedbackTotal;

    /** 应验数（rating ≥ 4） */
    private Long verifyCount;
}
