package com.smartwash.dining.from;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新菜谱分类请求 DTO。
 * 字段不传（null）即不修改；name 传入时校验与其他分类不重名（排除自身）。
 */
@Data
public class UpdateDiningCategoryFrom {

    /** 分类记录 id */
    @NotNull(message = "分类 id 不能为空")
    private Long id;

    /** 分类名称，不传即不修改 */
    @Size(max = 32, message = "分类名称过长（上限32字符）")
    private String name;

    /** 排序，不传即不修改 */
    @Min(value = 0, message = "排序不能为负数")
    private Integer sort;
}
