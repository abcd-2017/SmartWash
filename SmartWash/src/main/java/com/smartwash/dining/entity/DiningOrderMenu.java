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
 * 吃啥-本单菜单。
 * 发起者从公共菜谱库勾选的菜品范围，参与者只能在此范围内选菜；
 * uk_order_recipe 保证一道菜在本单只出现一次。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_order_menu")
public class DiningOrderMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 点单订单 ID(dining_order.id) */
    private Long orderId;

    /** 菜谱 ID(dining_recipe.id) */
    private Long recipeId;

    /** 加入菜单时间 */
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
