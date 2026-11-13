package com.smartwash.dining.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端菜谱分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 * 筛选维度：分类（精确）/ 状态（上架/下架）/ 关键词（菜名模糊），全部可空 = 不过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchDiningRecipeFrom extends BaseSearchFrom {

    /** 分类精确筛选，null=不过滤（注意：传值才筛选，无法用本字段筛「未分类」） */
    private Long categoryId;

    /** 状态筛选：1=上架 0=下架，null=不过滤 */
    private Integer status;

    /** 关键词，菜名模糊匹配 */
    private String keyword;
}
