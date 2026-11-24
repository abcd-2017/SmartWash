package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 协作过的用户 VO（GET /web/auth/dining/orders/collaborators 条目，与后端 DiningCollaboratorVo 对应）。
 * 邀请页「邀请协作过的用户」数据源：跨订单聚合去重，按最近协作时间倒序。
 *
 * 无昵称：端上用手机号脱敏 + 头像展示（users 表无 nickname）。
 * Gson 容错约定（api 模块统一）：字段可空 + 默认 null，消费方经 safe 系兜底。
 */
@Keep
data class DiningCollaboratorVo(
    val userId: Long? = null,
    /** 脱敏手机号（如 139****9000） */
    val phoneMasked: String? = null,
    /** 头像 URL（可空） */
    val avatar: String? = null,
    /** 最近协作时间（ISO 字符串原文，格式化由映射层处理） */
    val lastCollaboratedAt: String? = null,
) {
    val safeUserId: Long get() = userId ?: 0L
    val safePhoneMasked: String get() = phoneMasked.orEmpty()
    val safeAvatar: String get() = avatar.orEmpty()
    val safeLastCollaboratedAt: String get() = lastCollaboratedAt.orEmpty()
}
