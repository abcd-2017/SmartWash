package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 本单已邀请条目 VO（GET /web/auth/dining/orders/{id}/invites）。
 * 邀请页展示「已邀请」列表与状态（已邀请 / 已加入）。
 */
@Data
@Accessors(chain = true)
public class DiningInviteVo {

    private Long id;

    private Long inviteeId;

    /** 被邀请人脱敏手机号 */
    private String inviteePhoneMasked;

    /** 被邀请人头像（可空） */
    private String inviteeAvatar;

    /** INVITED / JOINED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime joinedAt;
}
