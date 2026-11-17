package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 配料库管理端视图对象，供 Web 后台配料库管理页使用。
 */
@Data
@Accessors(chain = true)
public class DiningIngredientVo {

    private Long id;

    /** 配料名（唯一） */
    private String name;

    /** 配料分类：MAIN=主料/AUX=辅料/SEASONING=调料/OTHER=其他 */
    private String category;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
