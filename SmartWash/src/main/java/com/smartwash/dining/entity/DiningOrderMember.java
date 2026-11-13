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
 * 家庭点单-点单订单成员。
 * 记录加入者：创建者即首个成员；uk_order_user 保证一人一单一条，重复加入由唯一索引兜底裁决。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_order_member")
public class DiningOrderMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 点单订单 ID(dining_order.id) */
    private Long orderId;

    /** 成员用户 ID(users.user_id) */
    private Long userId;

    /** 加入时间 */
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
