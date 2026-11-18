package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 发起点单（publish）请求体（POST /web/auth/dining/orders/{id}/publish，与后端
 * from/PublishDiningOrderFrom 字段对应）。
 *
 * 后端契约：DRAFT→ONGOING；仅发起者；标题必填（不传沿用库内标题）+ 菜单非空；
 * [version] 为客户端已知版本，做乐观锁（与服务端不一致返回业务码 409，端上刷新后重试）。
 */
@Keep
data class PublishDiningOrderRequest(
    /** 草稿页原位编辑后的最终标题；不传沿用库内标题，最终仍须非空 */
    val title: String?,
    /** 客户端已知的订单 version（乐观并发闸门） */
    val version: Long,
)
