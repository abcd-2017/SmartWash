package com.smartwash.feature.game.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 游戏域深链贡献者 JVM 单测（锚点）：game-center host 映射游戏中心路由，
 * 忽略 path 段与 query；未登记 host 不在贡献表。
 */
class GameDeepLinkModuleTest {

    /** 被测贡献者（object 方法直调，Hilt 注解不影响 JVM 实例化） */
    private val contributor = GameDeepLinkModule.provideGameDeepLinkContributor()

    private val factory = contributor.routes().getValue("game-center")

    @Test
    fun `game-center 映射游戏中心路由且忽略参数`() {
        assertEquals("GameCenter", factory.route(emptyList(), emptyMap()))
        assertEquals("GameCenter", factory.route(listOf("unexpected"), emptyMap()))
        assertEquals("GameCenter", factory.route(emptyList(), mapOf("x" to "1")))
    }

    @Test
    fun `贡献表仅登记 game-center`() {
        assertEquals(setOf("game-center"), contributor.routes().keys)
    }

    @Test
    fun `未登记 host 不在贡献表`() {
        assertNull(contributor.routes()["web"])
        assertNull(contributor.routes()["game"])
        assertFalse(contributor.routes().containsKey("toolbox-short-code"))
    }
}
