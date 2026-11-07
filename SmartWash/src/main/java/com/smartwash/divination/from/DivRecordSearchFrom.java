package com.smartwash.divination.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 解读历史检索 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DivRecordSearchFrom extends BaseSearchFrom {

    /** 检索关键词：匹配卦例问题或该卦最新解读正文；为空时返回空页 */
    private String keyword;
}
