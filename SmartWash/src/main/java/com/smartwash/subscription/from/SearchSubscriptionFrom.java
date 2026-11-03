package com.smartwash.subscription.from;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.smartwash.from.BaseSearchFrom;

/**
 * 我的订阅分页筛选 DTO。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SearchSubscriptionFrom extends BaseSearchFrom {

    /** 按状态筛选（active/paused/canceled/finished，不传查全部） */
    private String status;

    /** 订阅名称模糊关键词 */
    @Size(max = 100, message = "关键词过长（上限100字符）")
    private String keyword;
}
