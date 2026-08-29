package com.smartwash.divination.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 卦历分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchRecordFrom extends BaseSearchFrom {

    /** 术数方法筛选：liuyao/meihua/qimen/liuren */
    private String method;

    /** 起始日期（yyyy-MM-dd），闭区间 */
    private String dateFrom;

    /** 截止日期（yyyy-MM-dd），闭区间 */
    private String dateTo;
}
