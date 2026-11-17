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
 * 吃啥-菜谱收藏（契约 §2.3）。
 * uk_user_recipe 保证一人一菜一行：收藏接口幂等的最终裁决（并发撞唯一键按已收藏处理）；
 * idx_user 支撑「我的收藏」按人反查分页。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_recipe_favorite")
public class DiningRecipeFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID（users.user_id） */
    private Long userId;

    /** 菜谱 ID（dining_recipe.id） */
    private Long recipeId;

    private LocalDateTime createdAt;
}
