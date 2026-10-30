package com.smartwash.workbench.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作台入口分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 * 管理端列表筛选：title 模糊 + enabled 精确。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchWorkbenchEntryFrom extends BaseSearchFrom {

    /** 标题模糊筛选 */
    private String title;

    /** 启用状态筛选：1启用 0停用，空=全部 */
    private Integer enabled;
}
