package com.smartwash.common.ui.components

import androidx.annotation.DrawableRes
import com.smartwash.common.ui.R

/**
 * 运营配图本地兜底映射（PS 自绘位图，drawable-nodpi）。
 *
 * 背景：工作台/游戏中心的配图终态由服务端 imageUrl 下发（管理端上传 MinIO），
 * 服务端未配置图时端上以本地自绘封面兜底展示，避免退化为纯文字卡。
 * 后续切云端后，本映射保留作为「无网络/无下发」时的降级资产。
 *
 * 命名约定：wb_*（工作台）、game_*（游戏封面，key 中 '-' 映射为 '_'）、
 * sub_*（订阅）、pay_*（支付仪式）、order_status_*（订单状态插画）。
 */
object LocalCoverArt {

    /** 工作台入口 key → 本地配图（与 workbench_entry.entry_key 对齐） */
    private val workbench: Map<String, Int> = mapOf(
        "game-center" to R.drawable.wb_wide_game_center,
        "mood" to R.drawable.wb_tile_mood,
        "lab" to R.drawable.wb_tile_lab,
        "toolbox-short-code" to R.drawable.wb_tile_short_code,
        "ai-assistant" to R.drawable.wb_tile_ai_assistant,
    )

    /** 游戏库 game_key → 本地封面（与 game_config.game_key 对齐，'-' → '_'） */
    private val games: Map<String, Int> = mapOf(
        "air-hockey" to R.drawable.game_air_hockey,
        "beat-tap" to R.drawable.game_beat_tap,
        "billiards" to R.drawable.game_billiards,
        "blackjack" to R.drawable.game_blackjack,
        "block-drop" to R.drawable.game_block_drop,
        "breakout" to R.drawable.game_breakout,
        "connect-four" to R.drawable.game_connect_four,
        "falling-sand" to R.drawable.game_falling_sand,
        "flappy" to R.drawable.game_flappy,
        "forest-run" to R.drawable.game_forest_run,
        "fruit-slice" to R.drawable.game_fruit_slice,
        "g2048" to R.drawable.game_g2048,
        "gomoku" to R.drawable.game_gomoku,
        "idiom" to R.drawable.game_idiom,
        "invaders" to R.drawable.game_invaders,
        "lights-out" to R.drawable.game_lights_out,
        "memory-cards" to R.drawable.game_memory_cards,
        "minesweeper" to R.drawable.game_minesweeper,
        "moon-lander" to R.drawable.game_moon_lander,
        "nonogram" to R.drawable.game_nonogram,
        "pacman" to R.drawable.game_pacman,
        "slingshot" to R.drawable.game_slingshot,
        "snake" to R.drawable.game_snake,
        "snow-trail" to R.drawable.game_snow_trail,
        "sokoban" to R.drawable.game_sokoban,
        "solitaire" to R.drawable.game_solitaire,
        "sudoku" to R.drawable.game_sudoku,
        "tetris" to R.drawable.game_tetris,
    )

    /** 订单状态 → 详情页头插画（key 见 OrderStatus，此处按状态名映射） */
    private val orderStatus: Map<String, Int> = mapOf(
        "PENDING_PAYMENT" to R.drawable.order_status_pending,
        "PENDING_SHIPMENT" to R.drawable.order_status_shipment,
        "WASHING" to R.drawable.order_status_washing,
        "READY_FOR_PICKUP" to R.drawable.order_status_pickup,
        "COMPLETED" to R.drawable.order_status_completed,
        "CANCELED" to R.drawable.order_status_canceled,
    )

    @DrawableRes
    fun workbenchCover(entryKey: String): Int? = workbench[entryKey]

    @DrawableRes
    fun gameCover(gameKey: String): Int? = games[gameKey]

    @DrawableRes
    fun orderStatusCover(statusName: String): Int? = orderStatus[statusName]

    /** 订阅详情页默认头图（泛订阅概念图，服务端头图字段落地前的本地资产） */
    @DrawableRes
    val subscriptionDetailHeader: Int = R.drawable.sub_detail_header

    /** 支付成功庆祝卡装饰背景（透明 PNG，叠在白卡之上、内容之下） */
    @DrawableRes
    val paySuccessCelebrationBg: Int = R.drawable.pay_success_bg
}
