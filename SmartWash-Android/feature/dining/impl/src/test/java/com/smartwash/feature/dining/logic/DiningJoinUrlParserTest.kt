package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 扫码 join URL 解析 JVM 单测（spec 契约：`{origin}/dining/join/{orderId}?t={join_token}`）。
 *
 * 端内扫码不能走 DeepLinkParser 的 https WebView 分支，改由本纯函数自解析：
 * 仅识别 dining 加入路径，orderId 必须为正整数，token 必填。
 * 大小写策略：scheme/host 大小写不敏感；路径段大小写敏感（spec 固定 `/dining/join/`）；
 * 查询键 `t` 大小写不敏感（扫码器/短链可能改写）。
 */
class DiningJoinUrlParserTest {

    // ==================== 合法链接 ====================

    @Test
    fun `合法 https 链接解析出订单与凭证`() {
        val result = DiningJoinUrlParser.parse("https://api.example.com/dining/join/42?t=abc123")
        assertEquals(DiningJoinUrlParseResult.Join(orderId = 42L, token = "abc123"), result)
    }

    @Test
    fun `合法 http 链接（demo 明文环境）解析`() {
        val result = DiningJoinUrlParser.parse("http://demo.example.com/dining/join/7?t=tok")
        assertEquals(DiningJoinUrlParseResult.Join(orderId = 7L, token = "tok"), result)
    }

    @Test
    fun `scheme 与 host 大小写不敏感`() {
        val result = DiningJoinUrlParser.parse("HTTPS://API.EXAMPLE.COM/dining/join/9?t=t")
        assertEquals(DiningJoinUrlParseResult.Join(orderId = 9L, token = "t"), result)
    }

    @Test
    fun `查询键 T 大小写不敏感`() {
        val result = DiningJoinUrlParser.parse("https://api.example.com/dining/join/3?T=xyz")
        assertEquals(DiningJoinUrlParseResult.Join(orderId = 3L, token = "xyz"), result)
    }

    @Test
    fun `带端口与锚尾巴的链接仍解析`() {
        val result = DiningJoinUrlParser.parse("https://api.example.com:8443/dining/join/5?t=abc#frag")
        assertEquals(DiningJoinUrlParseResult.Join(orderId = 5L, token = "abc"), result)
    }

    @Test
    fun `builder 产物可被 parser 还原（构造与解析闭环）`() {
        val url = DiningJoinUrlBuilder.buildJoinUrl(
            baseUrl = "https://api.example.com/",
            orderId = 88L,
            joinToken = "token-1",
        )
        assertEquals(DiningJoinUrlParseResult.Join(orderId = 88L, token = "token-1"), DiningJoinUrlParser.parse(url))
    }

    // ==================== 非本应用码 ====================

    @Test
    fun `非 dining 路径判定为非本应用码`() {
        assertEquals(DiningJoinUrlParseResult.NotDining, DiningJoinUrlParser.parse("https://example.com/other/1?t=x"))
        assertEquals(DiningJoinUrlParseResult.NotDining, DiningJoinUrlParser.parse("https://example.com/"))
    }

    @Test
    fun `dining 路径大小写不符判定为非本应用码（路径段大小写敏感）`() {
        assertEquals(DiningJoinUrlParseResult.NotDining, DiningJoinUrlParser.parse("https://example.com/DINING/JOIN/1?t=x"))
    }

    @Test
    fun `非 http 族 scheme 判定为非本应用码`() {
        assertEquals(DiningJoinUrlParseResult.NotDining, DiningJoinUrlParser.parse("ftp://example.com/dining/join/1?t=x"))
        assertEquals(DiningJoinUrlParseResult.NotDining, DiningJoinUrlParser.parse("smartwash://dining"))
    }

    @Test
    fun `空串 null 与纯文本判定为非本应用码`() {
        assertEquals(DiningJoinUrlParseResult.NotDining, DiningJoinUrlParser.parse(null))
        assertEquals(DiningJoinUrlParseResult.NotDining, DiningJoinUrlParser.parse(""))
        assertEquals(DiningJoinUrlParseResult.NotDining, DiningJoinUrlParser.parse("   "))
        assertEquals(DiningJoinUrlParseResult.NotDining, DiningJoinUrlParser.parse("hello world"))
    }

    // ==================== 本应用路径但结构非法 ====================

    @Test
    fun `缺少 token 判定为非法链接`() {
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/42"))
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/42?"))
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/42?foo=bar"))
    }

    @Test
    fun `token 为空判定为非法链接`() {
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/42?t="))
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/42?t=   "))
    }

    @Test
    fun `orderId 缺失或非正整数判定为非法链接`() {
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/?t=x"))
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/abc?t=x"))
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/0?t=x"))
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/-1?t=x"))
    }

    @Test
    fun `orderId 段后多余路径判定为非法链接`() {
        assertEquals(DiningJoinUrlParseResult.Malformed, DiningJoinUrlParser.parse("https://example.com/dining/join/42/extra?t=x"))
    }
}
