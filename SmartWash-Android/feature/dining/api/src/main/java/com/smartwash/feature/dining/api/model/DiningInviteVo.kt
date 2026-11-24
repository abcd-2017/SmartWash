package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 本单已邀请条目 VO（GET /web/auth/dining/orders/{id}/invites 条目，与后端 DiningInviteVo 对应）。
 * 邀请页「已邀请」区域：展示被邀请人与状态（已邀请 / 已加入）。
 *
 * Gson 容错约定（api 模块统一）：字段可空 + 默认 null，消费方经 safe 系兜底。
 */
@Keep
data class DiningInviteVo(
    val id: Long? = null,
    val inviteeId: Long? = null,
    /** 被邀请人脱敏手机号 */
    val inviteePhoneMasked: String? = null,
    /** 被邀请人头像（可空） */
    val inviteeAvatar: String? = null,
    /** INVITED / JOINED */
    val status: String? = null,
    /** 邀请时间（ISO 字符串原文） */
    val createdAt: String? = null,
    /** 加入时间（ISO 字符串原文，未加入为 null） */
    val joinedAt: String? = null,
) {
    val safeId: Long get() = id ?: 0L
    val safeInviteeId: Long get() = inviteeId ?: 0L
    val safeInviteePhoneMasked: String get() = inviteePhoneMasked.orEmpty()
    val safeInviteeAvatar: String get() = inviteeAvatar.orEmpty()
    val safeStatus: String get() = status.orEmpty()
    val safeCreatedAt: String get() = createdAt.orEmpty()
    val safeJoinedAt: String get() = joinedAt.orEmpty()

    /** 是否已加入（status=JOINED） */
    val isJoined: Boolean get() = status == "JOINED"
}
