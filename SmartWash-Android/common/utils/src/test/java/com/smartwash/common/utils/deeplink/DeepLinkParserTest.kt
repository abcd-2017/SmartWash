package com.smartwash.common.utils.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeepLinkParser 全分支 JVM 单测（锚点）：
 * http/https、smartwash://web（带/缺 url、url 非 http）、routeLookup 命中/未命中/
 * 参数传递（host 归一化、pathSegments 保留大小写、query 解码）、非 smartwash scheme、
 * 大小写、query 编码解码、全程不抛异常。
 * 注：host → 路由的映射知识（含 t/{code} 校验）在各域贡献者，不在 parser——
 * 这里用 lambda 模拟 routeLookup 行为。
 */
class DeepLinkParserTest {

    /** 记录 routeLookup 收到的参数（断言归一化与下传语义用） */
    private class LookupSpy(var result: String? = null) {
        var host: String? = null
        var pathSegments: List<String>? = null
        var query: Map<String, String>? = null

        fun lookup(h: String, p: List<String>, q: Map<String, String>): String? {
            host = h; pathSegments = p; query = q
            return result
        }
    }

    private fun spyLookup(result: String?) = LookupSpy(result)

    // ---------- 1. http/https → WebViewUrl（原样） ----------

    @Test
    fun `http 直链原样进 WebView 且不解码不改写`() {
        val uri = "http://8.148.70.81:9000/web/t/ab12?x=%E6%B4%97"
        val action = DeepLinkParser.parse(uri) { _, _, _ -> "whatever" }
        assertEquals(DeepLinkAction.WebViewUrl(uri, null), action)
    }

    @Test
    fun `https 直链原样进 WebView`() {
        val uri = "https://smartwash.example.com/activity?foo=1&bar=2"
        val action = DeepLinkParser.parse(uri) { _, _, _ -> null }
        assertEquals(DeepLinkAction.WebViewUrl(uri, null), action)
    }

    @Test
    fun `大写 HTTPS 协议头同样进 WebView`() {
        val action = DeepLinkParser.parse("HTTPS://example.com/a") { _, _, _ -> null }
        assertEquals(DeepLinkAction.WebViewUrl("HTTPS://example.com/a", null), action)
    }

    // ---------- 2. smartwash://web 分支 ----------

    @Test
    fun `web 分支 url 与 title 均解码`() {
        val action = DeepLinkParser.parse(
            "smartwash://web?url=https%3A%2F%2Fa.b.com%2Fc%3Fd%3D1&title=%E6%B4%97%E8%A1%A3%E6%94%BB%E7%95%A5"
        ) { _, _, _ -> "never" }
        assertEquals(
            DeepLinkAction.WebViewUrl("https://a.b.com/c?d=1", "洗衣攻略"),
            action
        )
    }

    @Test
    fun `web 分支缺 title 时 title 为 null`() {
        val action = DeepLinkParser.parse(
            "smartwash://web?url=https%3A%2F%2Fa.b.com%2Fx"
        ) { _, _, _ -> null }
        assertEquals(DeepLinkAction.WebViewUrl("https://a.b.com/x", null), action)
    }

    @Test
    fun `web 分支缺 url 为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://web?title=abc") { _, _, _ -> "Login" }
        assertEquals(DeepLinkAction.Unknown("smartwash://web?title=abc"), action)
    }

    @Test
    fun `web 分支无 query 为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://web") { _, _, _ -> "Login" }
        assertEquals(DeepLinkAction.Unknown("smartwash://web"), action)
    }

    @Test
    fun `web 分支 url 非 http 为 Unknown`() {
        // javascript: 伪协议必须拒之门外
        val action = DeepLinkParser.parse(
            "smartwash://web?url=javascript%3Aalert(1)"
        ) { _, _, _ -> "Login" }
        assertEquals(DeepLinkAction.Unknown("smartwash://web?url=javascript%3Aalert(1)"), action)
    }

    @Test
    fun `web 分支 url 畸形百分号编码时丢弃该参数走 Unknown`() {
        val action = DeepLinkParser.parse(
            "smartwash://web?url=http%3A%2F%2Fa.com%2F%ZZ"
        ) { _, _, _ -> "Login" }
        assertEquals(DeepLinkAction.Unknown("smartwash://web?url=http%3A%2F%2Fa.com%2F%ZZ"), action)
    }

    @Test
    fun `web 分支字面加号不转空格`() {
        // URI query 语义：字面 + 是普通字符（区别于表单编码），解码后必须保留
        val action = DeepLinkParser.parse(
            "smartwash://web?url=https%3A%2F%2Fa.com%2Fb+a"
        ) { _, _, _ -> null }
        assertEquals(DeepLinkAction.WebViewUrl("https://a.com/b+a", null), action)
    }

    // ---------- 3. routeLookup 命中 / 未命中 ----------

    @Test
    fun `routeLookup 命中返回 NativeRoute`() {
        val action = DeepLinkParser.parse("smartwash://login") { _, _, _ -> "Login" }
        assertEquals(DeepLinkAction.NativeRoute("Login"), action)
    }

    @Test
    fun `routeLookup 未命中返回 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://nonexistent") { _, _, _ -> null }
        assertEquals(DeepLinkAction.Unknown("smartwash://nonexistent"), action)
    }

    @Test
    fun `routeLookup 收到归一化小写 host 与原始大小写 pathSegments`() {
        // toolbox 的 t/{code}：host 归一化小写，code 段保留原始大小写（短码大小写敏感）
        val spy = spyLookup("ToolboxShortCodeDetail?code=Ab12")
        val action = DeepLinkParser.parse("smartwash://T/Ab12", spy::lookup)
        assertEquals(DeepLinkAction.NativeRoute("ToolboxShortCodeDetail?code=Ab12"), action)
        assertEquals("t", spy.host)
        assertEquals(listOf("Ab12"), spy.pathSegments)
        assertEquals(emptyMap<String, String>(), spy.query)
    }

    @Test
    fun `routeLookup 收到多段 pathSegments`() {
        val spy = spyLookup(null)
        DeepLinkParser.parse("smartwash://a/b/c?x=1", spy::lookup)
        assertEquals(listOf("b", "c"), spy.pathSegments)
        assertEquals("a", spy.host)
    }

    @Test
    fun `routeLookup 未命中含尾斜杠形态`() {
        val action = DeepLinkParser.parse("smartwash://order/") { _, _, _ -> null }
        assertEquals(DeepLinkAction.Unknown("smartwash://order/"), action)
    }

    @Test
    fun `web 是内建分支不经 routeLookup`() {
        var called = false
        DeepLinkParser.parse("smartwash://web?url=https%3A%2F%2Fa.com") { _, _, _ ->
            called = true; null
        }
        assertTrue(!called)
    }

    // ---------- 4. query 参数下传（已解码） ----------

    @Test
    fun `routeLookup 收到的 query 已百分号解码`() {
        val spy = spyLookup("Order/2")
        val action = DeepLinkParser.parse("smartwash://order?tab=2&label=%E8%AE%A2%E5%8D%95", spy::lookup)
        assertEquals(DeepLinkAction.NativeRoute("Order/2"), action)
        assertEquals(mapOf("tab" to "2", "label" to "订单"), spy.query)
    }

    @Test
    fun `query 畸形编码参数被丢弃`() {
        val spy = spyLookup(null)
        DeepLinkParser.parse("smartwash://order?tab=%ZZ&x=1", spy::lookup)
        assertEquals(mapOf("x" to "1"), spy.query)
    }

    @Test
    fun `query 字面加号不转空格`() {
        val spy = spyLookup(null)
        DeepLinkParser.parse("smartwash://order?a=b+c", spy::lookup)
        assertEquals(mapOf("a" to "b+c"), spy.query)
    }

    @Test
    fun `无值 query 参数解析为空串`() {
        val spy = spyLookup(null)
        DeepLinkParser.parse("smartwash://order?flag", spy::lookup)
        assertEquals(mapOf("flag" to ""), spy.query)
    }

    // ---------- 5. 非 smartwash scheme / 畸形输入 ----------

    @Test
    fun `其他 scheme 为 Unknown`() {
        assertEquals(
            DeepLinkAction.Unknown("alipays://platformapi/startapp"),
            DeepLinkParser.parse("alipays://platformapi/startapp") { _, _, _ -> "Login" }
        )
    }

    @Test
    fun `无 scheme 为 Unknown`() {
        assertEquals(DeepLinkAction.Unknown("just-a-string"), DeepLinkParser.parse("just-a-string") { _, _, _ -> null })
        assertEquals(DeepLinkAction.Unknown("://x"), DeepLinkParser.parse("://x") { _, _, _ -> null })
    }

    @Test
    fun `空串与空白为 Unknown 且不抛异常`() {
        assertEquals(DeepLinkAction.Unknown(""), DeepLinkParser.parse("") { _, _, _ -> null })
        assertEquals(DeepLinkAction.Unknown("   "), DeepLinkParser.parse("   ") { _, _, _ -> null })
    }

    @Test
    fun `smartwash 无 host 为 Unknown`() {
        assertEquals(DeepLinkAction.Unknown("smartwash://"), DeepLinkParser.parse("smartwash://") { _, _, _ -> null })
        assertEquals(DeepLinkAction.Unknown("smartwash://?a=1"), DeepLinkParser.parse("smartwash://?a=1") { _, _, _ -> null })
    }

    @Test
    fun `routeLookup 抛异常时整体降级 Unknown 不外抛`() {
        // 解析全程不抛异常的兜底分支：lookup 实现自身出错也不许炸调用方
        val action = DeepLinkParser.parse("smartwash://boom") { _, _, _ -> error("lookup bug") }
        assertEquals(DeepLinkAction.Unknown("smartwash://boom"), action)
    }

    // ---------- 6. 大小写不敏感 ----------

    @Test
    fun `scheme 大小写不敏感`() {
        assertEquals(
            DeepLinkAction.NativeRoute("Login"),
            DeepLinkParser.parse("SMARTWASH://login") { _, _, _ -> "Login" }
        )
    }

    @Test
    fun `host 大小写不敏感且下传前归一化`() {
        val spy = spyLookup("Login")
        val action = DeepLinkParser.parse("smartwash://LOGIN", spy::lookup)
        assertEquals(DeepLinkAction.NativeRoute("Login"), action)
        assertEquals("login", spy.host)
    }

    // ---------- 7. hostOf（壳层 Handler 字典兜底查询用） ----------

    @Test
    fun `hostOf 提取 host 并小写`() {
        assertEquals("login", DeepLinkParser.hostOf("smartwash://LOGIN?x=1"))
        assertEquals("t", DeepLinkParser.hostOf("smartwash://t/abcd"))
        assertNull(DeepLinkParser.hostOf("https://a.com"))
        assertNull(DeepLinkParser.hostOf("smartwash://"))
        assertNull(DeepLinkParser.hostOf("任意输入"))
    }
}
