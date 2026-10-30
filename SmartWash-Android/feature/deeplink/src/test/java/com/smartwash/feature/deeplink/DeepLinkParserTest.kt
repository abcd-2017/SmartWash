package com.smartwash.feature.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DeepLinkParser 全分支 JVM 单测（锚点）：
 * http/https、smartwash://web（带/缺 url、url 非 http）、t/{code}（合法/非法/缺段）、
 * 注册表命中/未命中、非 smartwash scheme、大小写、query 编码解码、全程不抛异常。
 */
class DeepLinkParserTest {

    /** 真实默认注册表（首批注册 host 均为纯字面量映射，可直测） */
    private val registry = DefaultDeepLinkRegistry()

    // ---------- 1. http/https → WebViewUrl（原样） ----------

    @Test
    fun `http 直链原样进 WebView 且不解码不改写`() {
        val uri = "http://8.148.70.81:9000/web/t/ab12?x=%E6%B4%97"
        val action = DeepLinkParser.parse(uri, registry)
        assertEquals(DeepLinkAction.WebViewUrl(uri, null), action)
    }

    @Test
    fun `https 直链原样进 WebView`() {
        val uri = "https://smartwash.example.com/activity?foo=1&bar=2"
        val action = DeepLinkParser.parse(uri, registry)
        assertEquals(DeepLinkAction.WebViewUrl(uri, null), action)
    }

    @Test
    fun `大写 HTTPS 协议头同样进 WebView`() {
        val action = DeepLinkParser.parse("HTTPS://example.com/a", registry)
        assertEquals(DeepLinkAction.WebViewUrl("HTTPS://example.com/a", null), action)
    }

    // ---------- 2. smartwash://web 分支 ----------

    @Test
    fun `web 分支 url 与 title 均解码`() {
        val action = DeepLinkParser.parse(
            "smartwash://web?url=https%3A%2F%2Fa.b.com%2Fc%3Fd%3D1&title=%E6%B4%97%E8%A1%A3%E6%94%BB%E7%95%A5",
            registry
        )
        assertEquals(
            DeepLinkAction.WebViewUrl("https://a.b.com/c?d=1", "洗衣攻略"),
            action
        )
    }

    @Test
    fun `web 分支缺 title 时 title 为 null`() {
        val action = DeepLinkParser.parse(
            "smartwash://web?url=https%3A%2F%2Fa.b.com%2Fx",
            registry
        )
        assertEquals(DeepLinkAction.WebViewUrl("https://a.b.com/x", null), action)
    }

    @Test
    fun `web 分支缺 url 为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://web?title=abc", registry)
        assertEquals(DeepLinkAction.Unknown("smartwash://web?title=abc"), action)
    }

    @Test
    fun `web 分支无 query 为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://web", registry)
        assertEquals(DeepLinkAction.Unknown("smartwash://web"), action)
    }

    @Test
    fun `web 分支 url 非 http 为 Unknown`() {
        // javascript: 伪协议必须拒之门外
        val action = DeepLinkParser.parse(
            "smartwash://web?url=javascript%3Aalert(1)",
            registry
        )
        assertEquals(DeepLinkAction.Unknown("smartwash://web?url=javascript%3Aalert(1)"), action)
    }

    @Test
    fun `web 分支 url 畸形百分号编码时丢弃该参数走 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://web?url=http%3A%2F%2Fa.com%2F%ZZ", registry)
        assertEquals(DeepLinkAction.Unknown("smartwash://web?url=http%3A%2F%2Fa.com%2F%ZZ"), action)
    }

    @Test
    fun `web 分支字面加号不转空格`() {
        // URI query 语义：字面 + 是普通字符（区别于表单编码），解码后必须保留
        val action = DeepLinkParser.parse("smartwash://web?url=https%3A%2F%2Fa.com%2Fb+a", registry)
        assertEquals(DeepLinkAction.WebViewUrl("https://a.com/b+a", null), action)
    }

    // ---------- 3. smartwash://t/{code} 分支 ----------

    @Test
    fun `t 分支合法短码映射详情路由`() {
        val action = DeepLinkParser.parse("smartwash://t/ab12cd34", registry)
        assertEquals(
            DeepLinkAction.NativeRoute("ToolboxShortCodeDetail?code=ab12cd34"),
            action
        )
    }

    @Test
    fun `t 分支 4 位下界合法`() {
        val action = DeepLinkParser.parse("smartwash://t/abcd", registry)
        assertEquals(DeepLinkAction.NativeRoute("ToolboxShortCodeDetail?code=abcd"), action)
    }

    @Test
    fun `t 分支 16 位上界合法`() {
        val code = "abcdefghijklmno1" // 16 位
        val action = DeepLinkParser.parse("smartwash://t/$code", registry)
        assertEquals(DeepLinkAction.NativeRoute("ToolboxShortCodeDetail?code=$code"), action)
    }

    @Test
    fun `t 分支 3 位过短为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://t/abc", registry)
        assertEquals(DeepLinkAction.Unknown("smartwash://t/abc"), action)
    }

    @Test
    fun `t 分支 17 位过长为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://t/abcdefghijklmnop1", registry)
        assertEquals(DeepLinkAction.Unknown("smartwash://t/abcdefghijklmnop1"), action)
    }

    @Test
    fun `t 分支含非字母数字为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://t/ab-cd", registry)
        assertEquals(DeepLinkAction.Unknown("smartwash://t/ab-cd"), action)
    }

    @Test
    fun `t 分支缺 code 段为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://t", registry)
        assertEquals(DeepLinkAction.Unknown("smartwash://t"), action)
    }

    @Test
    fun `t 分支多余段为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://t/abcd/efgh", registry)
        assertEquals(DeepLinkAction.Unknown("smartwash://t/abcd/efgh"), action)
    }

    // ---------- 4. 注册表命中 / 未命中 ----------

    @Test
    fun `注册表命中 login`() {
        val action = DeepLinkParser.parse("smartwash://login", registry)
        assertEquals(DeepLinkAction.NativeRoute("Login"), action)
    }

    @Test
    fun `注册表 order 缺省 tab 为 0`() {
        val action = DeepLinkParser.parse("smartwash://order", registry)
        assertEquals(DeepLinkAction.NativeRoute("Order/0"), action)
    }

    @Test
    fun `注册表 order 带 tab 参数`() {
        val action = DeepLinkParser.parse("smartwash://order?tab=2", registry)
        assertEquals(DeepLinkAction.NativeRoute("Order/2"), action)
    }

    @Test
    fun `注册表 order tab 越界防御夹取`() {
        assertEquals(
            DeepLinkAction.NativeRoute("Order/4"),
            DeepLinkParser.parse("smartwash://order?tab=99", registry)
        )
        assertEquals(
            DeepLinkAction.NativeRoute("Order/0"),
            DeepLinkParser.parse("smartwash://order?tab=-3", registry)
        )
        assertEquals(
            DeepLinkAction.NativeRoute("Order/0"),
            DeepLinkParser.parse("smartwash://order?tab=abc", registry)
        )
    }

    @Test
    fun `注册表命中无参 host`() {
        assertEquals(
            DeepLinkAction.NativeRoute("RechargeRecord"),
            DeepLinkParser.parse("smartwash://recharge-record", registry)
        )
        assertEquals(
            DeepLinkAction.NativeRoute("DivHistory"),
            DeepLinkParser.parse("smartwash://divination-history", registry)
        )
        assertEquals(
            DeepLinkAction.NativeRoute("ToolboxShortCodeList"),
            DeepLinkParser.parse("smartwash://toolbox-short-code", registry)
        )
    }

    @Test
    fun `注册表未命中为 Unknown`() {
        val action = DeepLinkParser.parse("smartwash://nonexistent", registry)
        assertEquals(DeepLinkAction.Unknown("smartwash://nonexistent"), action)
    }

    @Test
    fun `web 与 t 为 Parser 内建分支不查注册表`() {
        // 注册表对内建 host 返回 null，但解析顺序保证走不到（此处仅锚定注册表自身行为）
        assertNull(registry.routeFor("web", emptyMap()))
        assertNull(registry.routeFor("t", emptyMap()))
    }

    // ---------- 5. 非 smartwash scheme / 畸形输入 ----------

    @Test
    fun `其他 scheme 为 Unknown`() {
        assertEquals(
            DeepLinkAction.Unknown("alipays://platformapi/startapp"),
            DeepLinkParser.parse("alipays://platformapi/startapp", registry)
        )
    }

    @Test
    fun `无 scheme 为 Unknown`() {
        assertEquals(DeepLinkAction.Unknown("just-a-string"), DeepLinkParser.parse("just-a-string", registry))
        assertEquals(DeepLinkAction.Unknown("://x"), DeepLinkParser.parse("://x", registry))
    }

    @Test
    fun `空串与空白为 Unknown 且不抛异常`() {
        assertEquals(DeepLinkAction.Unknown(""), DeepLinkParser.parse("", registry))
        assertEquals(DeepLinkAction.Unknown("   "), DeepLinkParser.parse("   ", registry))
    }

    @Test
    fun `smartwash 无 host 为 Unknown`() {
        assertEquals(DeepLinkAction.Unknown("smartwash://"), DeepLinkParser.parse("smartwash://", registry))
        assertEquals(DeepLinkAction.Unknown("smartwash://?a=1"), DeepLinkParser.parse("smartwash://?a=1", registry))
    }

    // ---------- 6. 大小写不敏感 ----------

    @Test
    fun `scheme 大小写不敏感`() {
        assertEquals(
            DeepLinkAction.NativeRoute("Login"),
            DeepLinkParser.parse("SMARTWASH://login", registry)
        )
    }

    @Test
    fun `host 大小写不敏感`() {
        assertEquals(
            DeepLinkAction.NativeRoute("Login"),
            DeepLinkParser.parse("smartwash://LOGIN", registry)
        )
        assertEquals(
            DeepLinkAction.NativeRoute("Order/0"),
            DeepLinkParser.parse("smartwash://Order", registry)
        )
    }

    // ---------- 7. hostOf（Handler 字典兜底查询用） ----------

    @Test
    fun `hostOf 提取 host 并小写`() {
        assertEquals("login", DeepLinkParser.hostOf("smartwash://LOGIN?x=1"))
        assertEquals("t", DeepLinkParser.hostOf("smartwash://t/abcd"))
        assertNull(DeepLinkParser.hostOf("https://a.com"))
        assertNull(DeepLinkParser.hostOf("smartwash://"))
        assertNull(DeepLinkParser.hostOf("任意输入"))
    }
}
