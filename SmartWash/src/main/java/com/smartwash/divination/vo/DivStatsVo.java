package com.smartwash.divination.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 盘感统计视图（/web/auth/div/stats）。
 * 口径：verify = 反馈 rating ≥ 4（5 档评分，≥4 记应验）；verifyRate = verifyCount / feedbackTotal（0-1，4 位小数，无反馈为 0）。
 */
@Data
public class DivStatsVo {

    /** 卦例总数 */
    private Long totalRecords;

    /** 按术数方法计数（liuyao/meihua/qimen/liuren） */
    private List<DivNameCountVo> methodCounts;

    /** 按问题领域计数（general/career/wealth/...） */
    private List<DivNameCountVo> categoryCounts;

    /** 近 12 个月卦例趋势（month: yyyy-MM，缺失月份补 0） */
    private List<DivMonthCountVo> monthTrend;

    /** 反馈总数 */
    private Long feedbackTotal;

    /** 应验数（rating ≥ 4） */
    private Long verifyCount;

    /** 应验率（0-1） */
    private BigDecimal verifyRate;
}
