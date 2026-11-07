package com.smartwash.divination.vo;

import lombok.Data;

/**
 * 名称-计数对（盘感统计的 method/category 维度通用行）。
 */
@Data
public class DivNameCountVo {

    /** 维度名（method 或 category） */
    private String name;

    /** 计数 */
    private Long count;
}
