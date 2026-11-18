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
 * 吃啥-选菜记录（历史遗留，已停止写入）。
 * 新模型「菜单即已选清单」，购买清单直接聚合 dining_order_menu，不再产生个人选菜记录。
 * 表与实体保留（不删表，避免迁移风险），仅 detail 兼容读取历史数据；一行一人一菜（uk order+recipe+user）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_order_item")
public class DiningOrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 点单订单 ID(dining_order.id) */
    private Long orderId;

    /** 菜谱 ID(dining_recipe.id) */
    private Long recipeId;

    /** 选菜人用户 ID(users.user_id) */
    private Long userId;

    /** 个人备注（如 不要辣），可空；null = 未写备注（改备注传 null 即清空） */
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
