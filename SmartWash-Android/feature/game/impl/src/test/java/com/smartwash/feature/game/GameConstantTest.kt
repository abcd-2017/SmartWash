package com.smartwash.feature.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideogameAsset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 游戏域白名单映射 JVM 单测（锚点）：icon 白名单 / 色组六色 key / 分类 key 的
 * 映射与未知值兜底（契约：未知值显示默认样式，见 GameVo 注释）。
 */
class GameConstantTest {

    // ---------- icon 白名单 ----------

    @Test
    fun `icon 白名单 key 逐一映射预期图标`() {
        assertEquals(Icons.Default.SportsEsports, gameIconFromKey("gamepad"))
        assertEquals(Icons.Default.VideogameAsset, gameIconFromKey("arcade"))
        assertEquals(Icons.Default.Extension, gameIconFromKey("puzzle"))
        assertEquals(Icons.Default.GridOn, gameIconFromKey("grid"))
        assertEquals(Icons.Default.Casino, gameIconFromKey("board"))
        assertEquals(Icons.Default.TextFields, gameIconFromKey("word"))
    }

    @Test
    fun `icon 大小写不敏感`() {
        assertEquals(Icons.Default.SportsEsports, gameIconFromKey("GamePad"))
        assertEquals(Icons.Default.Extension, gameIconFromKey("PUZZLE"))
    }

    @Test
    fun `icon default 与未知与空与 null 一律兜底应用格`() {
        assertEquals(Icons.Default.Apps, gameIconFromKey("default"))
        assertEquals(Icons.Default.Apps, gameIconFromKey("unknown-key"))
        assertEquals(Icons.Default.Apps, gameIconFromKey(""))
        assertEquals(Icons.Default.Apps, gameIconFromKey(null))
    }

    // ---------- 色组六色 key ----------

    @Test
    fun `color 六色 key 逐一映射预期色组`() {
        assertEquals(GameTint.GREEN, GameTint.fromKey("green"))
        assertEquals(GameTint.BLUE, GameTint.fromKey("blue"))
        assertEquals(GameTint.ORANGE, GameTint.fromKey("orange"))
        assertEquals(GameTint.PINK, GameTint.fromKey("pink"))
        assertEquals(GameTint.TEAL, GameTint.fromKey("teal"))
        assertEquals(GameTint.PURPLE, GameTint.fromKey("purple"))
    }

    @Test
    fun `color 未知与空与 null 兜底 GREEN`() {
        assertEquals(GameTint.GREEN, GameTint.fromKey("default"))
        assertEquals(GameTint.GREEN, GameTint.fromKey("red"))
        assertEquals(GameTint.GREEN, GameTint.fromKey(""))
        assertEquals(GameTint.GREEN, GameTint.fromKey(null))
    }

    // ---------- 分类 key ----------

    @Test
    fun `category 白名单 key 逐一映射预期分类`() {
        assertEquals(GameCategory.PUZZLE, GameCategory.fromKey("puzzle"))
        assertEquals(GameCategory.ARCADE, GameCategory.fromKey("arcade"))
        assertEquals(GameCategory.WORD, GameCategory.fromKey("word"))
        assertEquals(GameCategory.BOARD, GameCategory.fromKey("board"))
        assertEquals(GameCategory.OTHER, GameCategory.fromKey("other"))
    }

    @Test
    fun `category 未知与空与 null 兜底 OTHER`() {
        assertEquals(GameCategory.OTHER, GameCategory.fromKey("racing"))
        assertEquals(GameCategory.OTHER, GameCategory.fromKey(""))
        assertEquals(GameCategory.OTHER, GameCategory.fromKey(null))
    }

    @Test
    fun `分类筛选 chips 只含四类不含 OTHER`() {
        assertEquals(
            listOf(GameCategory.PUZZLE, GameCategory.ARCADE, GameCategory.WORD, GameCategory.BOARD),
            GameCategory.CHIP_ORDER,
        )
    }
}
