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
 * 吃啥-菜谱。
 * 平台公共菜谱库的正片条目：Web 管理端维护（增删改查/上下架/封面上传），用户端浏览上架列表
 * 并在点单菜单按菜谱勾选；购买清单由所选菜谱的食材名并集去重生成（后续批次）。
 * 封面存 MinIO 对象键（dining/covers/ 前缀），直链由 MinioConfig.urlOf 拼装——端点变更不必刷库。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_recipe")
public class DiningRecipe implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 上架：客户端菜谱列表可见可选 */
    public static final int STATUS_ON_SHELF = 1;

    /** 下架：不再出现在客户端上架列表，历史点单订单不受影响 */
    public static final int STATUS_OFF_SHELF = 0;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属分类 ID（dining_category.id），null=未分类 */
    private Long categoryId;

    /** 菜名（如 番茄炒蛋），不做唯一约束（同名菜谱是合法业务态） */
    private String name;

    /** 封面图对象键（MinIO dining/covers/ 前缀），null=未上传 */
    private String coverObjectKey;

    /** 食材名数组 JSON 文本（如 ["西红柿","鸡蛋"]），购买清单按名字并集去重 */
    private String ingredients;

    /** 做法步骤（纯文本，换行分段） */
    private String steps;

    /** 来源：ADMIN=运营后台录入，USER=用户端新增 */
    private String source;

    /** 上下架：1=上架（STATUS_ON_SHELF），0=下架（STATUS_OFF_SHELF） */
    private Integer status;

    /** 创建人用户 ID（source=USER 时记录） */
    private Long createdByUserId;

    /** 创建人管理员 ID（source=ADMIN 时记录） */
    private Long createdByAdminId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
