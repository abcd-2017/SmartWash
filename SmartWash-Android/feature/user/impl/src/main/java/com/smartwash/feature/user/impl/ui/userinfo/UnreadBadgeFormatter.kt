package com.smartwash.feature.user.impl.ui.userinfo

/**
 * 未读角标文案格式化（纯 JVM，我的页入口角标消费）：
 * - null / 0 / 负数 → null（隐藏角标，不显示「0」）
 * - 1..99 → 原值
 * - >99 → 「99+」封顶（避免行尾值过长挤压标题）
 */
internal fun formatUnreadBadge(count: Long?): String? = when {
    count == null || count <= 0L -> null
    count > 99L -> "99+"
    else -> count.toString()
}
