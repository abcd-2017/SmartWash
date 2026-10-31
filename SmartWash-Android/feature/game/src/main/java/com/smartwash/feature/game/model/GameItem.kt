package com.smartwash.feature.game.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.smartwash.feature.game.GameCategory
import com.smartwash.feature.game.GameTint

/**
 * 游戏卡模型（后端 GameVo 的客户端映射层，由 ViewModel 完成白名单映射）。
 *
 * @param gameKey 业务唯一标识（后端 game_config.game_key），兼作网格稳定 key
 * @param title   游戏名（服务端文案）
 * @param description 一句话玩法描述（服务端文案，可为空串）
 * @param icon    图标（白名单枚举映射产物，未知值兜底）
 * @param tint    图标容器色组（六色浅底，未知值兜底 GREEN）
 * @param category 分类（本地筛选 chips 依据，未知值兜底 OTHER）
 * @param badge   角标文案（如 新/热），null/空不显示
 * @param url     最终可玩地址（后端下发即用，端上零拼装；空串时不响应点击）
 */
data class GameItem(
    val gameKey: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tint: GameTint,
    val category: GameCategory,
    val badge: String? = null,
    val url: String = "",
)
