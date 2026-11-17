package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端菜谱视图对象，供 Web 后台列表与治理操作使用。
 * ingredients 以结构化数组返回（服务层反序列化 DB 中的 JSON 文本）；
 * coverUrl 为封面对外直链（MinioConfig.urlOf 拼装，Web 直接渲染），coverObjectKey 供编辑回传。
 */
@Data
@Accessors(chain = true)
public class DiningRecipeVo {

    private Long id;

    /** 所属分类 ID，null=未分类 */
    private Long categoryId;

    /** 所属分类名称（服务层联查回填，未分类为 null） */
    private String categoryName;

    private String name;

    /** 封面对象键（编辑回传用），null=未上传 */
    private String coverObjectKey;

    /** 封面对外直链（由对象键拼装），null=未上传 */
    private String coverUrl;

    /** 食材名数组（服务层由 DB JSON 文本反序列化） */
    private List<String> ingredients;

    /** 做法步骤（纯文本） */
    private String steps;

    /** 热量 Kcal/100g（CookLikeHOC 营养表口径），null=未录入 */
    private BigDecimal caloriesPer100g;

    /** 整份估算热量 Kcal（HowToCook 预估卡路里口径），null=未录入；一份热量展示口径 = caloriesTotal ?: caloriesPer100g × 250g（份量假设常量，四端一致写死） */
    private BigDecimal caloriesTotal;

    /** 蛋白质 g/100g，null=未录入 */
    private BigDecimal proteinG;

    /** 脂肪 g/100g，null=未录入 */
    private BigDecimal fatG;

    /** 碳水化合物 g/100g，null=未录入 */
    private BigDecimal carbsG;

    /** 钠 mg/100g，null=未录入 */
    private BigDecimal sodiumMg;

    /** 来源：ADMIN=运营后台录入，USER=用户端新增 */
    private String source;

    /** 上下架：1=上架 0=下架 */
    private Integer status;

    /** 当前用户是否已收藏（用户端列表/详情由服务层按 user_id 批量填充；管理端为 false） */
    private Boolean favorited;

    /** 该菜全部配料的过敏原去重集合（仅用户端详情接口拼装，按 ALLERGEN_VALUES 顺序；列表不带，避免放大 payload） */
    private List<String> allergens;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
