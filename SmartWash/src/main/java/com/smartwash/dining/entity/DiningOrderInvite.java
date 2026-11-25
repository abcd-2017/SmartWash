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
 * 吃啥-点单定向邀请。
 * 发起者从「协作过的用户」中勾选定向邀请：一人一行，uk(order_id,inviter_id,invitee_id)
 * 保证同一单同一邀请人对同一被邀请人只发一次；被邀请人加入后回写 status=JOINED + joined_at。
 * 与公开 join_token 并存：公开链接仍是无定向的广播凭证，本表只服务定向邀请与站内信触达。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_order_invite")
public class DiningOrderInvite implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 点单订单 ID(dining_order.id) */
    private Long orderId;

    /** 邀请人用户 ID(users.user_id) */
    private Long inviterId;

    /** 被邀请人用户 ID(users.user_id) */
    private Long inviteeId;

    /** 状态：INVITED 已邀请 / JOINED 已加入 */
    private String status;

    private LocalDateTime createdAt;

    /** 加入时间（status=JOINED 时写入） */
    private LocalDateTime joinedAt;
}
