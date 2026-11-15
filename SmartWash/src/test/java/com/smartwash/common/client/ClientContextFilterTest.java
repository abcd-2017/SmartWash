package com.smartwash.common.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClientContextFilter} 生命周期单测：请求期写入上下文，结束后清理，缺失头不阻断。
 */
@DisplayName("ClientContextFilter 平台上下文过滤器")
class ClientContextFilterTest {

    private final ClientContextFilter filter = new ClientContextFilter();

    @Test
    @DisplayName("请求期链路内可读到解析后的平台上下文")
    void doFilter_setsContextDuringChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientContext.HEADER_PLATFORM, "android");
        request.addHeader(ClientContext.HEADER_APP_VERSION, "1.0.0");
        request.addHeader(ClientContext.HEADER_NETWORK_TYPE, "wifi");
        request.addHeader(ClientContext.HEADER_USER_AGENT, "test-ua");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean[] chainInvoked = {false};
        FilterChain chain = (req, res) -> {
            chainInvoked[0] = true;
            ClientContext context = ClientContextHolder.get();
            assertEquals("android", context.getPlatform());
            assertEquals("1.0.0", context.getAppVersion());
            assertEquals("wifi", context.getNetworkType());
            assertEquals("test-ua", context.getUserAgent());
        };

        filter.doFilter(request, response, chain);

        assertTrue(chainInvoked[0], "过滤器必须放行到下游链路");
        assertNull(ClientContextHolder.get(), "请求结束后必须清理 ThreadLocal，防线程复用串扰");
    }

    @Test
    @DisplayName("缺失请求头：退化为默认值且照常放行")
    void doFilter_missingHeaders_defaultsAndPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ClientContext context = ClientContextHolder.get();
            assertEquals("unknown", context.getPlatform());
            assertEquals("unknown", context.getNetworkType());
            assertNull(context.getAppVersion());
            assertNull(context.getUserAgent());
        };

        filter.doFilter(request, response, chain);
        assertNull(ClientContextHolder.get());
    }
}
