package com.smartwash.workbench.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * deeplink 路由字典分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 * 管理端列表筛选：host 模糊 + enabled 精确。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchDeeplinkRouteFrom extends BaseSearchFrom {

    /** host 模糊筛选 */
    private String host;

    /** 启用状态筛选：1启用 0停用，空=全部 */
    private Integer enabled;
}
