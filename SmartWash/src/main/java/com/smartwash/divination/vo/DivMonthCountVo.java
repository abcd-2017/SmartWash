package com.smartwash.divination.vo;

import lombok.Data;

/**
 * 月份-计数对（盘感统计的月度趋势行）。
 */
@Data
public class DivMonthCountVo {

    /** 月份（yyyy-MM） */
    private String month;

    /** 计数 */
    private Long count;
}
