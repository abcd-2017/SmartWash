package com.smartwash.feature.toolbox.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 工具箱深链贡献者 JVM 单测（锚点）：
 * t/{code} 的 4-16 位字母数字校验（原 DeepLinkParser 内建职责迁入本模块贡献者后，
 * 边界用例随迁锚定）+ toolbox-short-code 映射 + 未登记形态返回 null。
 */
class ToolboxDeepLinkModuleTest {

    /** 被测贡献者（object 方法直调，Hilt 注解不影响 JVM 实例化） */
    private val contributor = ToolboxDeepLinkModule.provideToolboxDeepLinkContributor()

    private val tFactory = contributor.routes().getValue("t")

    private val listFactory = contributor.routes().getValue("toolbox-short-code")

    // ---------- t/{code} 校验 ----------

    @Test
    fun `t 合法短码映射详情路由`() {
        assertEquals(
            "ToolboxShortCodeDetail?code=ab12cd34",
            tFactory.route(listOf("ab12cd34"), emptyMap())
        )
    }

    @Test
    fun `t 短码 4 位下界与 16 位上界合法`() {
        assertEquals(
            "ToolboxShortCodeDetail?code=abcd",
            tFactory.route(listOf("abcd"), emptyMap())
        )
        assertEquals(
            "ToolboxShortCodeDetail?code=abcdefghijklmno1",
            tFactory.route(listOf("abcdefghijklmno1"), emptyMap())
        )
    }

    @Test
    fun `t 短码过短过长含非法字符返回 null`() {
        assertNull(tFactory.route(listOf("abc"), emptyMap()))          // 3 位
        assertNull(tFactory.route(listOf("abcdefghijklmnop1"), emptyMap())) // 17 位
        assertNull(tFactory.route(listOf("ab-cd"), emptyMap()))        // 非字母数字
    }

    @Test
    fun `t 缺段与多段返回 null`() {
        assertNull(tFactory.route(emptyList(), emptyMap()))            // smartwash://t
        assertNull(tFactory.route(listOf("abcd", "efgh"), emptyMap())) // smartwash://t/a/b
    }

    @Test
    fun `t 短码保留原始大小写`() {
        assertEquals(
            "ToolboxShortCodeDetail?code=Ab12Cd",
            tFactory.route(listOf("Ab12Cd"), emptyMap())
        )
    }

    // ---------- toolbox-short-code 与未登记 host ----------

    @Test
    fun `toolbox-short-code 映射列表页且忽略参数`() {
        assertEquals(
            "ToolboxShortCodeList",
            listFactory.route(emptyList(), emptyMap())
        )
        assertEquals(
            "ToolboxShortCodeList",
            listFactory.route(listOf("unexpected"), mapOf("x" to "1"))
        )
    }

    @Test
    fun `未登记 host 不在贡献表`() {
        assertNull(contributor.routes()["web"])
        assertNull(contributor.routes()["login"])
    }
}
