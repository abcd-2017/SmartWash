package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 定向邀请请求体（POST /web/auth/dining/orders/{id}/invites）。
 * inviteeIds 为被邀请人用户 ID（协作过的用户中勾选）；后端单次上限 20 人。
 */
@Keep
data class InviteDiningOrderRequest(
    val inviteeIds: List<Long>,
)
