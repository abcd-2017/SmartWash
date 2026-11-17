package com.smartwash.dining.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配料库分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 * 管理端列表筛选：keyword（配料名模糊）+ category（精确，空=全部）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchDiningIngredientFrom extends BaseSearchFrom {

    /** 配料名模糊关键词 */
    private String keyword;

    /** 配料分类精确筛选：MAIN/AUX/SEASONING/OTHER，空=全部 */
    private String category;
}
