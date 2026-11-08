package com.smartwash.feature.notice.api.model

import androidx.annotation.Keep

/**
 * 站内信视图对象（GET /web/auth/notices 响应载荷，与后端 notice/vo/NoticeVo 一一对应）。
 *
 * 后端语义：isRead 为「当前登录用户」的已读状态，由服务端按 read 表批量回填；
 * 所有用户看到同一份公告列表，只是已读标记不同。
 *
 * Gson 容错约定（api 模块统一）：字段全部可空 + 默认 null——Gson 反序列化缺字段或
 * 类型漂移时置 null 而非抛出，消费方一律经 safe 系计算属性取兜底值，禁止直接读可空原始字段。
 * createdAt 保留后端下发的 ISO 字符串原样（Gson 默认不支持 java.time，解析与格式化由 impl 容错处理）。
 */
@Keep
data class NoticeVo(
    /** 公告 ID（列表 item key） */
    val id: Long? = null,
    /** 标题 */
    val title: String? = null,
    /** 正文（可含换行，列表展示摘要） */
    val content: String? = null,
    /** 类型枚举串（SYSTEM/ANNOUNCEMENT，见后端 NoticeType） */
    val type: String? = null,
    /** 深链跳转目标（smartwash://... 或 https://...），空表示纯文案公告 */
    val deepLink: String? = null,
    /** 当前用户是否已读（字段缺失按未读处理，宁可高亮也不误灰化） */
    val isRead: Boolean? = null,
    /** 发送时间（ISO yyyy-MM-dd'T'HH:mm:ss） */
    val createdAt: String? = null,
) {
    val safeId: Long get() = id ?: 0L
    val safeTitle: String get() = title.orEmpty()
    val safeContent: String get() = content.orEmpty()
    val safeType: String get() = type.orEmpty()
    val safeDeepLink: String get() = deepLink.orEmpty()
    val isUnread: Boolean get() = isRead != true
}
