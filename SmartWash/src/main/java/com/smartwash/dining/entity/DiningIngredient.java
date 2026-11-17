package com.smartwash.dining.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 吃啥-配料库。
 * 配料主数据（名称唯一，uk_name 裁决）：CSV 导入 / 建菜谱时按名 upsert 自动注册，
 * Web 后台配料库管理页维护（改名/改分类/删除——被菜谱配料关联引用时拒绝删除）。
 * category 为导入时启发式预判，后台可改；供应商信息不入库（转换器侧剥离）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_ingredient")
public class DiningIngredient implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主料（肉蛋禽水产等） */
    public static final String CATEGORY_MAIN = "MAIN";

    /** 辅料（蔬菜菌菇豆腐葱姜蒜等） */
    public static final String CATEGORY_AUX = "AUX";

    /** 调料（油盐糖酱醋料酒等） */
    public static final String CATEGORY_SEASONING = "SEASONING";

    /** 其他（启发规则未命中的兜底分类） */
    public static final String CATEGORY_OTHER = "OTHER";

    /** 配料分类合法值集合（与 DB 注释、Web 端下拉口径一致） */
    public static final java.util.List<String> CATEGORY_VALUES =
            java.util.List.of(CATEGORY_MAIN, CATEGORY_AUX, CATEGORY_SEASONING, CATEGORY_OTHER);

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 配料名（唯一；供应商信息不入库） */
    private String name;

    /** 配料分类：MAIN=主料/AUX=辅料/SEASONING=调料/OTHER=其他 */
    private String category;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
