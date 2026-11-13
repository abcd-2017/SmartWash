package com.smartwash.ui.page.workbench

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 工具卡图标容器语义色组（规范 §2.1 六色浅底，与后端 color 白名单 key 一一对应）。
 * 未知 color key 兜底 GREEN（契约见 WorkbenchEntryVo：未知值显示默认样式）。
 */
enum class WorkbenchTint {
    GREEN,
    BLUE,
    ORANGE,
    PINK,
    TEAL,
    PURPLE;

    companion object {
        /** 后端 color key → 色组（green/blue/orange/pink/teal/purple，大小写不敏感，未知兜底 GREEN） */
        fun fromKey(key: String?): WorkbenchTint = when (key?.lowercase()) {
            "blue" -> BLUE
            "orange" -> ORANGE
            "pink" -> PINK
            "teal" -> TEAL
            "purple" -> PURPLE
            else -> GREEN
        }
    }
}

/**
 * 后端 icon 白名单 key → Material 内置图标映射（规范 §4.1：功能图标保持 Material 内置，
 * 禁外部图标库）。白名单：calendar/search/book/calculate/history/chat/link/science/gamepad/default，
 * 未知 key 与 default 同样兜底为「工具格」图标。
 * gamepad 对齐后端 Form 白名单（游戏中心种子入口），为补齐既有契约新增的唯一 Icons 映射。
 */
internal fun workbenchIconFromKey(key: String?): ImageVector = when (key?.lowercase()) {
    "calendar" -> Icons.Default.CalendarMonth
    "search" -> Icons.Default.Search
    "book" -> Icons.Default.MenuBook
    "calculate" -> Icons.Default.Calculate
    "history" -> Icons.Default.History
    "chat" -> Icons.Default.ChatBubbleOutline
    "link" -> Icons.Default.Link
    "lock" -> Icons.Default.Lock
    "science" -> Icons.Default.Science
    "gamepad" -> Icons.Default.SportsEsports
    else -> Icons.Default.Apps
}

/**
 * 卡片类型（规范 §3.1 卡片变体的端上落地，与后端 type 白名单 key 一一对应）。
 * - TILE：两列小卡（默认，兼容存量无 type 的下发数据）
 * - WIDE：通栏横卡（重点入口，独占一行）
 * - ROW：列表行（连续 row 在页面归并进同一张白卡，收敛轻量入口的视觉噪音）
 * - NOTICE：公告条（可关闭的运营触达位，target 空 = 纯文案公告）
 * 未知 type key 兜底 TILE（契约：未知值显示默认样式）。
 */
enum class WorkbenchLayout {
    TILE,
    WIDE,
    ROW,
    NOTICE;

    companion object {
        /** 后端 type key → 卡片类型（tile/wide/row/notice，大小写不敏感，未知兜底 TILE） */
        fun fromKey(key: String?): WorkbenchLayout = when (key?.lowercase()) {
            "wide" -> WIDE
            "row" -> ROW
            "notice" -> NOTICE
            else -> TILE
        }
    }
}

/**
 * 工作台工具条目（后端 WorkbenchEntryVo 的客户端映射层）。
 * 数据源二选一：服务端下发（title/description 为服务端文案）或本地兜底
 * （[WorkbenchViewModel.defaultTools] 经 application.getString 解析，规则见 CLAUDE.md §7.4）。
 * 点击按 [target] 走深链分发（DeepLinkHandler.resolve → navigate）；target 为空 = 功能建设中。
 *
 * @param entryKey     业务唯一标识（服务端下发或兜底 key），兼作列表稳定 key
 * @param title        标题（已解析文案，非资源 id）
 * @param target       smartwash://... 或 https://...；空串 = 功能建设中（点击提示）
 * @param badge        角标文案（如 新/热），null/空不显示
 * @param layout       卡片类型（服务端 type 下发映射；页面按类型选组件渲染）
 * @param sectionTitle 分组标题（服务端下发；与前一 entry 不同时端上渲染区块标题），null 不分组
 * @param imageUrl     配图直链（服务端下发），null = 纯文字卡片
 */
data class WorkbenchTool(
    val entryKey: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tint: WorkbenchTint,
    val target: String = "",
    val badge: String? = null,
    val layout: WorkbenchLayout = WorkbenchLayout.TILE,
    val sectionTitle: String? = null,
    val imageUrl: String? = null,
)
