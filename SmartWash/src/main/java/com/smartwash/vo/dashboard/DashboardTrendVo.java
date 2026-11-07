package com.smartwash.vo.dashboard;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工作台趋势数据（单日）
 */
@Data
public class DashboardTrendVo {

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 当日收入（元）：当日支付成功流水金额合计，口径与今日收入一致 */
    private BigDecimal income;

    /** 当日订单创建数 */
    private Integer orderCount;
}
