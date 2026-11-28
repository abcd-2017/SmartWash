package com.smartwash.feature.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import com.smartwash.feature.game.model.GameItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 游戏搜索 / 分类本地过滤 JVM 单测（[filterGames] 纯函数锚点）。
 *
 * 锚点契约：
 * - 空关键词（含纯空格）= 非搜索态，按选中分类过滤；
 * - 搜索态跨分类按标题包含过滤，大小写不敏感、去首尾空格；
 * - 搜索无命中返回空列表（页面据此走搜索空态 + 清除搜索）。
 */
class GameSearchFilterTest {

    private fun game(key: String, title: String, category: GameCategory) = GameItem(
        gameKey = key,
        title = title,
        description = "",
        icon = Icons.Default.Apps,
        tint = GameTint.GREEN,
        category = category,
        url = "https://example.com/$key",
    )

    private val games = listOf(
        game("snake", "贪吃蛇大作战", GameCategory.ARCADE),
        game("2048", "2048 益智版", GameCategory.PUZZLE),
        game("word", "成语接龙", GameCategory.WORD),
        game("mario", "Super Mario Run", GameCategory.ARCADE),
    )

    @Test
    fun `空关键词返回全量`() {
        assertEquals(games, filterGames(games, null, ""))
        assertEquals(games, filterGames(games, null, "   "))
    }

    @Test
    fun `标题包含匹配中文`() {
        assertEquals(
            listOf("贪吃蛇大作战"),
            filterGames(games, null, "贪吃").map { it.title },
        )
    }

    @Test
    fun `标题包含匹配英文大小写不敏感`() {
        assertEquals(
            listOf("Super Mario Run"),
            filterGames(games, null, "mario").map { it.title },
        )
        assertEquals(
            listOf("Super Mario Run"),
            filterGames(games, null, "MARIO").map { it.title },
        )
    }

    @Test
    fun `搜索去首尾空格`() {
        assertEquals(
            listOf("2048 益智版"),
            filterGames(games, null, "  2048  ").map { it.title },
        )
    }

    @Test
    fun `搜索跨分类不受选中分类限制`() {
        // 选中「益智」，但搜索命中的是街机游戏 → 仍返回（搜索即全库，分类 chips 让位）
        assertEquals(
            listOf("贪吃蛇大作战"),
            filterGames(games, GameCategory.PUZZLE, "贪吃").map { it.title },
        )
    }

    @Test
    fun `搜索无命中返回空列表`() {
        assertEquals(emptyList<GameItem>(), filterGames(games, null, "不存在的游戏"))
    }

    @Test
    fun `非搜索态按分类过滤`() {
        assertEquals(
            listOf("贪吃蛇大作战", "Super Mario Run"),
            filterGames(games, GameCategory.ARCADE, "").map { it.title },
        )
    }
}
