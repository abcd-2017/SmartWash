package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 菜谱分类管理端视图对象，供 Web 后台列表与治理操作使用。
 */
@Data
@Accessors(chain = true)
public class DiningCategoryVo {

    private Long id;

    /** 分类名称 */
    private String name;

    /** 排序，越小越靠前 */
    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
