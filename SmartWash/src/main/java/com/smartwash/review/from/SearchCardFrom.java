package com.smartwash.review.from;

import com.smartwash.from.BaseSearchFrom;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 卡片分页查询 DTO（按资料筛选）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchCardFrom extends BaseSearchFrom {

    @NotNull(message = "materialId 不能为空")
    private Long materialId;
}
