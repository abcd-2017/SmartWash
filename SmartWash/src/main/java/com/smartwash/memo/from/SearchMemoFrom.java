package com.smartwash.memo.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 速记分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchMemoFrom extends BaseSearchFrom {

    /** 关键词，对 content 做 LIKE 模糊匹配，可空 */
    private String keyword;

    /** 标签精确过滤（匹配逗号分隔 tags 中的一个完整标签），可空 */
    private String tag;
}
