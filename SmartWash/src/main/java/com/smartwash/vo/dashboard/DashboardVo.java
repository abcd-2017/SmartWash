package com.smartwash.vo.dashboard;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DashboardVo {

    private Long schoolCount;

    private Long userCount;

    private Long todayOrderCount;

    /** 今日收入（单位：元） */
    private BigDecimal todayIncome;
}
