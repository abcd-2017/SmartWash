package com.smartwash.divination.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 拦截问题日志分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchBlockedQuestionFrom extends BaseSearchFrom {

    /** 关键词：模糊匹配问题内容 */
    private String keyword;
}
