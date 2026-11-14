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
 * 吃啥-选菜记录（共享购物车明细）。
 * 一人一菜一条（uk order+recipe+user）：勾选即插入、取消即删行；note 承载个人备注（如 不要辣）。
 * 所有变更走 dining_order.version 乐观并发协议（条件 UPDATE +1，不匹配返回业务错误码 409）。
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

    /** 勾选时间 */
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
