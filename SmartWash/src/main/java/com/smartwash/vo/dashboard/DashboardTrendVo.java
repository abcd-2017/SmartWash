package com.smartwash.vo.dashboard;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DashboardTrendVo {

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 当日收入（元）：当日支付成功流水金额合计，口径与今日收入一致 */
    private BigDecimal income;

    private Integer orderCount;
}
