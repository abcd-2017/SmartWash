package com.smartwash.common.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 设计规范 §2 字阶（DESIGN-SPEC.md v0.4，2026-10-24 定稿）。
 * 全应用收敛为 8 档（size/line-height/weight）：
 *   display 36/44/700 · headline 28/36/700 · titleLarge 22/28/600 · title 18/24/600 ·
 *   bodyLarge 16/24/400 · body 14/20/400 · caption 12/16/400 · captionStrong 12/16/500
 * weight 只允许 400/500/600/700；金额与数字槽位启用 tnum。
 *
 * 语义分配（2026-11-18 对齐餐饮内页画板后调整）：
 *   - 内页页头 PageHeader = title 档 18/24/600（居中）
 *   - 卡片标题 CardTitle / 区块标题 SectionTitle = bodyLarge 档 16/24/600（与正文同档靠字重区分）
 *   - 根页大标题 RootTitle / 状态大字 StatusLarge 仍取 titleLarge 档 22/28/600
 *   - 金额中字 AmountMedium 保持 title 档 18/24/600 + tnum
 *
 * AppTextStyles 保留旧命名（DataLarge/RootTitle 等）作为语义别名，值映射到上述 8 档
 * （规范 §1.4：「字阶命名 DataLarge/RootTitle 等，以 8 档为准，旧名作别名映射」），
 * 既有调用点无需改动。
 */
object AppTextStyles {
    /** 数据大字 — 余额、应付金额、取件码（= font.display 36/44/700 + tnum） */
    val DataLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )

    /** 保留大字 — 28sp / 36sp / 700（v0.4 字阶 headline 档）。
     *  用于「数据大字在窄屏的阶梯内降级」：36 → 28 → 22，不造夹档。 */
    val Headline = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    )

    /** 状态大字 — hero 订单状态锚点字（迁移 21→22 = font.titleLarge） */
    val StatusLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )

    /** 金额中字 — 列表内价格、券面额（迁移 20→18 = font.title + tnum） */
    val AmountMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )

    /** 根页大标题 — 首页/服务/工作台/我的（迁移 24→22 = font.titleLarge） */
    val RootTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    )

    /** 二级页页头（= font.title 18/24/600）。2026-11-18 起内页页头改为居中 18sp；
     *  原 2026-10-31 画板实测 22sp 的勘误作废。 */
    val PageHeader = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

    /** 卡片标题（= font.bodyLarge 档 16/24/600，与正文同档靠字重区分） */
    val CardTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

    /** 区块标题 — 页面内分区标题（= font.bodyLarge 档 16/24/600，与正文同档靠字重区分） */
    val SectionTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
}

val AppTypography = Typography(
    // font.display 36/44/700（余额等超大数字，tnum）
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    // 派生槽位：收敛到 headline 28/36/700，不引入新字号
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    // 派生槽位：收敛到 titleLarge 22/28/600
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    // font.headline 28/36/700（结果页大标题，tnum）
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    // 派生槽位：收敛到 titleLarge 22/28/600
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // 派生槽位：收敛到 title 18/24/600
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),

    // font.titleLarge 22/28/600（根页大标题 / hero 状态大字；不再作内页页头）
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // font.title 18/24/600 槽位（行动 / 强调文字；卡片/区块标题不占本槽位，
    // 走 AppTextStyles.CardTitle / SectionTitle = bodyLarge 档 16/24/600）
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // 派生槽位：14/20/500
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),

    // font.bodyLarge 16/24/400（主要正文）
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // font.body 14/20/400（次要正文、列表描述、输入文字）
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    // font.caption 12/16/400（辅助说明、时间戳、占位符）
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),

    // 派生槽位：14/20/500
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    // font.captionStrong 12/16/500（tab、角标）
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    // font.captionStrong 12/16/500（底部导航标签）
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)
