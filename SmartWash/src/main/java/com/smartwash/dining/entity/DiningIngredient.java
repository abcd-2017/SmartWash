package com.smartwash.dining.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    // ==================== 过敏原标签（契约 §2.2） ====================

    /** 海鲜类 */
    public static final String ALLERGEN_SEAFOOD = "SEAFOOD";

    /** 坚果类 */
    public static final String ALLERGEN_NUT = "NUT";

    /** 麸质类 */
    public static final String ALLERGEN_GLUTEN = "GLUTEN";

    /** 乳制品类 */
    public static final String ALLERGEN_DAIRY = "DAIRY";

    /** 蛋类 */
    public static final String ALLERGEN_EGG = "EGG";

    /** 大豆类 */
    public static final String ALLERGEN_SOY = "SOY";

    /** 辛辣类 */
    public static final String ALLERGEN_SPICY = "SPICY";

    /** 其他（后台可手动指定的兜底标签） */
    public static final String ALLERGEN_OTHER = "OTHER";

    /** 过敏原合法值集合（与 DB 注释、Web 端下拉口径一致；顺序即详情聚合的输出顺序） */
    public static final java.util.List<String> ALLERGEN_VALUES = java.util.List.of(
            ALLERGEN_SEAFOOD, ALLERGEN_NUT, ALLERGEN_GLUTEN, ALLERGEN_DAIRY,
            ALLERGEN_EGG, ALLERGEN_SOY, ALLERGEN_SPICY, ALLERGEN_OTHER);

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 配料名（唯一；供应商信息不入库） */
    private String name;

    /** 配料分类：MAIN=主料/AUX=辅料/SEASONING=调料/OTHER=其他 */
    private String category;

    /**
     * 过敏原：SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY/OTHER，NULL=无。
     * updateStrategy=IGNORED：后台清空（传空串归一为 null）时必须把 NULL 写回库，
     * 不被全局 update-strategy=not_empty 跳过。
     */
    @TableField(value = "allergen", updateStrategy = FieldStrategy.IGNORED)
    private String allergen;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
