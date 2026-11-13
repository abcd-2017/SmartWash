package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 加入二维码 URL 构造 JVM 单测（spec 契约：内容 = https://{host}/dining/join/{orderId}?t={join_token}）。
 *
 * host 取舍（@Named("baseUrl") 运行期值）：从 BASE_URL 提取 origin（scheme://host[:port]），
 * scheme 随环境（demo 明文 http 兜底、生产注入 https 后二维码同源变 https），对齐
 * 「禁止新增硬编码地址」环境红线；host 缺失/畸形 baseUrl 返回 null 由页面降级。
 * 端内扫码侧按 path /dining/join/ 识别（与深链 host "dining" 对齐），不依赖域名本身。
 */
class DiningJoinUrlBuilderTest {

    // ==================== 正常构造 ====================

    @Test
    fun `https baseUrl 拼出 spec 约定内容`() {
        val url = DiningJoinUrlBuilder.buildJoinUrl(
            baseUrl = "https://api.example.com/",
            orderId = 42L,
            joinToken = "abc123",
        )
        assertEquals("https://api.example.com/dining/join/42?t=abc123", url)
    }

    @Test
    fun `http baseUrl scheme 随环境（demo 明文兜底）`() {
        val url = DiningJoinUrlBuilder.buildJoinUrl(
            baseUrl = "http://8.148.70.81:9000/",
            orderId = 7L,
            joinToken = "tok",
        )
        assertEquals("http://8.148.70.81:9000/dining/join/7?t=tok", url)
    }

    @Test
    fun `baseUrl 无尾斜杠与带路径均归一化到 origin`() {
        assertEquals(
            "https://api.example.com/dining/join/1?t=t",
            DiningJoinUrlBuilder.buildJoinUrl("https://api.example.com", 1L, "t"),
        )
        assertEquals(
            "https://api.example.com/dining/join/1?t=t",
            DiningJoinUrlBuilder.buildJoinUrl("https://api.example.com/sub/path/", 1L, "t"),
        )
    }

    // ==================== 畸形输入降级 ====================

    @Test
    fun `orderId 非法按 0 兜底不拼接出非法路径`() {
        // safeId 兜底 0 由调用方守卫，builder 本身对 0 不拦截（后端不存在 0 号单，扫码侧校验）
        assertEquals(
            "https://api.example.com/dining/join/0?t=t",
            DiningJoinUrlBuilder.buildJoinUrl("https://api.example.com/", 0L, "t"),
        )
    }

    @Test
    fun `token 为空返回 null（无凭证的码无意义）`() {
        assertNull(DiningJoinUrlBuilder.buildJoinUrl("https://api.example.com/", 1L, ""))
        assertNull(DiningJoinUrlBuilder.buildJoinUrl("https://api.example.com/", 1L, "   "))
    }

    @Test
    fun `baseUrl 畸形返回 null`() {
        assertNull(DiningJoinUrlBuilder.buildJoinUrl("", 1L, "t"))
        assertNull(DiningJoinUrlBuilder.buildJoinUrl("   ", 1L, "t"))
        assertNull(DiningJoinUrlBuilder.buildJoinUrl("not-a-url", 1L, "t"))
        assertNull(DiningJoinUrlBuilder.buildJoinUrl("api.example.com", 1L, "t"))
    }

    @Test
    fun `未知 scheme 拒绝（二维码内容必须是可被浏览器端内识别的 http 族地址）`() {
        assertNull(DiningJoinUrlBuilder.buildJoinUrl("ftp://api.example.com/", 1L, "t"))
        assertNull(DiningJoinUrlBuilder.buildJoinUrl("smartwash://dining", 1L, "t"))
    }
}
