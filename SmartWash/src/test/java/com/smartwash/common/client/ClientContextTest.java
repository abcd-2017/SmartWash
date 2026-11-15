package com.smartwash.common.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ClientContext} 请求头解析与归一化单测（纯函数）。
 */
@DisplayName("ClientContext 平台上下文解析")
class ClientContextTest {

    @Test
    @DisplayName("合法头原样归一化：平台/网络小写化，版本/UA 保留")
    void fromHeaders_validHeaders_normalized() {
        ClientContext context = ClientContext.fromHeaders("android", "1.2.3", "wifi", "Mozilla/5.0");

        assertEquals("android", context.getPlatform());
        assertEquals("1.2.3", context.getAppVersion());
        assertEquals("wifi", context.getNetworkType());
        assertEquals("Mozilla/5.0", context.getUserAgent());
    }

    @Test
    @DisplayName("平台大小写不敏感：Android/HarmonyOS 归一化为小写")
    void fromHeaders_platformCaseInsensitive() {
        assertEquals("android", ClientContext.fromHeaders("Android", null, null, null).getPlatform());
        assertEquals("harmonyos", ClientContext.fromHeaders("HarmonyOS", null, null, null).getPlatform());
        assertEquals("web", ClientContext.fromHeaders("WEB", null, null, null).getPlatform());
    }

    @Test
    @DisplayName("缺失/非法平台回退 unknown，绝不抛异常")
    void fromHeaders_missingOrInvalidPlatform_fallbackUnknown() {
        assertEquals("unknown", ClientContext.fromHeaders(null, null, null, null).getPlatform());
        assertEquals("unknown", ClientContext.fromHeaders("", null, null, null).getPlatform());
        assertEquals("unknown", ClientContext.fromHeaders("pc", null, null, null).getPlatform());
    }

    @Test
    @DisplayName("缺失/非法网络类型回退 unknown；合法值小写化")
    void fromHeaders_networkTypeNormalized() {
        assertEquals("unknown", ClientContext.fromHeaders(null, null, null, null).getNetworkType());
        assertEquals("unknown", ClientContext.fromHeaders(null, null, "5g", null).getNetworkType());
        assertEquals("cellular", ClientContext.fromHeaders(null, null, "Cellular", null).getNetworkType());
        assertEquals("none", ClientContext.fromHeaders(null, null, "NONE", null).getNetworkType());
    }

    @Test
    @DisplayName("版本号/UA 缺失为 null；超长截断（版本 64、UA 500）")
    void fromHeaders_truncation() {
        ClientContext missing = ClientContext.fromHeaders(null, null, null, null);
        assertNull(missing.getAppVersion());
        assertNull(missing.getUserAgent());

        String version65 = "v".repeat(65);
        assertEquals(64, ClientContext.fromHeaders(null, version65, null, null).getAppVersion().length());

        String ua600 = "A".repeat(600);
        assertEquals(500, ClientContext.fromHeaders(null, null, null, ua600).getUserAgent().length());
    }

    @Test
    @DisplayName("UA 清洗：CR/LF/Tab 替换为空格，防止日志/页面注入")
    void fromHeaders_userAgentSanitized() {
        ClientContext context = ClientContext.fromHeaders("web", null, null, "ua\r\ninjection\tend");
        assertEquals("ua  injection end", context.getUserAgent());
    }
}
