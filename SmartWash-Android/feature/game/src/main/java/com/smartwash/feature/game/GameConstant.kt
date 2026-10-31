package com.smartwash.feature.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 游戏分类枚举（后端 game_config.category 白名单：puzzle/arcade/word/board/other）。
 * 分类筛选 chips 只暴露四类（全部/益智/街机/文字/桌版，方案 §六），
 * OTHER（含未知 key 兜底）仅在「全部」下展示。
 */
enum class GameCategory(val key: String) {
    PUZZLE("puzzle"),
    ARCADE("arcade"),
    WORD("word"),
    BOARD("board"),
    OTHER("other");

    companion object {

        /** 分类筛选 chips 的展示顺序（不含 OTHER——无独立 chip，见类注释） */
        val CHIP_ORDER: List<GameCategory> = listOf(PUZZLE, ARCADE, WORD, BOARD)

        /** 后端 category key → 枚举（大小写不敏感，未知兜底 OTHER） */
        fun fromKey(key: String?): GameCategory =
            entries.firstOrNull { it.key == key?.lowercase() } ?: OTHER
    }
}

/**
 * 游戏卡图标容器语义色组（规范 §2.1 六色浅底，key 语言直接对齐工作台
 * WorkbenchTint——同一后端字典语言 green/blue/orange/pink/teal/purple）。
 * 未知 color key 兜底 GREEN（契约：未知值显示默认样式）。
 */
enum class GameTint {
    GREEN,
    BLUE,
    ORANGE,
    PINK,
    TEAL,
    PURPLE;

    companion object {

        /** 后端 color key → 色组（大小写不敏感，未知兜底 GREEN，对齐 WorkbenchTint.fromKey） */
        fun fromKey(key: String?): GameTint = when (key?.lowercase()) {
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
 * 禁外部图标库）。白名单 key 语言对齐工作台（小写 key + 未知值/default 兜底「应用格」图标），
 * 面向游戏域扩展：gamepad（通用游戏）/arcade（街机）/puzzle（益智）/grid（数字方块）/
 * board（棋牌桌游）/word（文字）。
 */
internal fun gameIconFromKey(key: String?): ImageVector = when (key?.lowercase()) {
    "gamepad" -> Icons.Default.SportsEsports
    "arcade" -> Icons.Default.VideogameAsset
    "puzzle" -> Icons.Default.Extension
    "grid" -> Icons.Default.GridOn
    "board" -> Icons.Default.Casino
    "word" -> Icons.Default.TextFields
    else -> Icons.Default.Apps
}
