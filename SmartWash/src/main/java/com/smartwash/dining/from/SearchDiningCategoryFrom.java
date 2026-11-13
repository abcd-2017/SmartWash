package com.smartwash.dining.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜谱分类分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 * 管理端列表筛选：name 模糊。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchDiningCategoryFrom extends BaseSearchFrom {

    /** 分类名称模糊筛选 */
    private String name;
}
