package com.smartwash.feature.game.di

import com.smartwash.feature.game.GameRoute
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 游戏域深链贡献者 JVM 单测（锚点）：game-center host 映射游戏中心路由，
 * 忽略 path 段与 query；未登记 host 不在贡献表。
 *
 * 断言对象为迁移后的类型安全路由（SmartWashRoute）；@SerialName 路由串与迁移前
 * 字符串常量一致性单独锚定（descriptor.serialName），防止迁移回归。
 */
class GameDeepLinkModuleTest {

    /** 被测贡献者（object 方法直调，Hilt 注解不影响 JVM 实例化） */
    private val contributor = GameDeepLinkModule.provideGameDeepLinkContributor()

    private val factory = contributor.routes().getValue("game-center")

    @Test
    fun `game-center 映射游戏中心路由且忽略参数`() {
        assertEquals(GameRoute.Center, factory.route(emptyList(), emptyMap()))
        assertEquals(GameRoute.Center, factory.route(listOf("unexpected"), emptyMap()))
        assertEquals(GameRoute.Center, factory.route(emptyList(), mapOf("x" to "1")))
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

    @Test
    fun `路由串锚点与迁移前一致`() {
        assertEquals("GameCenter", serializer<GameRoute.Center>().descriptor.serialName)
    }
}
