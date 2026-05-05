package com.smartwash.vo.dashboard;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工作台统计数据
 */
@Data
public class DashboardVo {

    /** 学校总数 */
    private Long schoolCount;

    /** 用户总数 */
    private Long userCount;

    /** 今日订单数 */
    private Long todayOrderCount;

    /** 今日收入（单位：元） */
    private BigDecimal todayIncome;
}
