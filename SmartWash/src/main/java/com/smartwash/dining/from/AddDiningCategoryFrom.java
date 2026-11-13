package com.smartwash.dining.from;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增菜谱分类请求 DTO。
 */
@Data
public class AddDiningCategoryFrom {

    /** 分类名称，全局唯一（uk_name 兜底并发） */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 32, message = "分类名称过长（上限32字符）")
    private String name;

    /** 排序，越小越靠前 */
    @Min(value = 0, message = "排序不能为负数")
    private Integer sort = 100;
}
