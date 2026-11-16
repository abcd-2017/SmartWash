package com.smartwash.subscription.from;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.smartwash.from.BaseSearchFrom;

@Data
@EqualsAndHashCode(callSuper = false)
public class SearchSubscriptionFrom extends BaseSearchFrom {

    /** 按状态筛选（active/paused/canceled/finished，不传查全部） */
    private String status;

    @Size(max = 100, message = "关键词过长（上限100字符）")
    private String keyword;
}
