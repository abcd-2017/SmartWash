package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 协作过的用户 VO（GET /web/auth/dining/collaborators 条目）。
 * 跨订单聚合 dining_order_member：当前用户历史参与过的所有点单，取这些单的其他成员去重，
 * 附最近一次协作时间。邀请页据此展示「邀请协作过的用户」。
 *
 * 无昵称：端上用手机号脱敏 + 头像展示（users 表无 nickname 字段）。
 */
@Data
@Accessors(chain = true)
public class DiningCollaboratorVo {

    private Long userId;

    /** 脱敏手机号（如 139****9000） */
    private String phoneMasked;

    /** 头像 URL（可空） */
    private String avatar;

    /** 最近一次协作时间（该用户与你共同参与的最近一单加入时间） */
    private LocalDateTime lastCollaboratedAt;
}
