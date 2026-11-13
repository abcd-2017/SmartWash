package com.smartwash.dining.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户端菜谱分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 * 与管理端 {@link SearchDiningRecipeFrom} 的差异：不带 status——用户端固定只查上架，
 * 下架筛选是运营视角能力，不暴露给客户端。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchWebDiningRecipeFrom extends BaseSearchFrom {

    /** 分类精确筛选，null=全部（含未分类） */
    private Long categoryId;

    /** 关键词，菜名模糊匹配 */
    private String keyword;
}
