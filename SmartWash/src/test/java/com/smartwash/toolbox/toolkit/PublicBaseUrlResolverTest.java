package com.smartwash.toolbox.toolkit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短链对外域名解析测试。
 * 覆盖：显式配置优先（含尾斜杠归一）、按请求推导、可信反代采信 X-Forwarded-*、
 * 不可信对端伪造头部被忽略、多级代理取首段、无请求上下文降级。
 */
@DisplayName("PublicBaseUrlResolver 对外域名解析测试")
class PublicBaseUrlResolverTest {

    private static final String CONFIGURED_FIELD = "configuredBaseUrl";

    private PublicBaseUrlResolver resolverWith(String configuredBaseUrl) {
        PublicBaseUrlResolver resolver = new PublicBaseUrlResolver();
        ReflectionTestUtils.setField(resolver, CONFIGURED_FIELD, configuredBaseUrl);
        return resolver;
    }

    private HttpServletRequest request(String remoteAddr, String host, String scheme,
                                       String forwardedHost, String forwardedProto) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("Host")).thenReturn(host);
        when(request.getScheme()).thenReturn(scheme);
        when(request.getHeader("X-Forwarded-Host")).thenReturn(forwardedHost);
        when(request.getHeader("X-Forwarded-Proto")).thenReturn(forwardedProto);
        return request;
    }

    @Test
    @DisplayName("显式配置优先于请求头（含尾斜杠归一，转发头不再被读取）")
    void configuredValue_takesPrecedenceOverRequest() {
        PublicBaseUrlResolver resolver = resolverWith("https://s.example.com/");
        HttpServletRequest request = request("127.0.0.1", "api.example.com", "http", "evil.com", "https");

        assertEquals("https://s.example.com", resolver.resolve(request),
                "配置非空时必须用配置值，且去掉尾斜杠");
        verify(request, never()).getHeader("X-Forwarded-Host");
        verify(request, never()).getHeader("X-Forwarded-Proto");
    }

    @Test
    @DisplayName("未配置时按请求推导 scheme + Host（直连部署）")
    void blankConfigured_derivesFromHostHeader() {
        PublicBaseUrlResolver resolver = resolverWith("");

        assertEquals("http://8.148.70.81:9000",
                resolver.resolve(request("8.148.70.81", "8.148.70.81:9000", "http", null, null)));
        assertEquals("https://smartwash.example.com",
                resolver.resolve(request("203.0.113.9", "smartwash.example.com", "https", null, null)));
    }

    @Test
    @DisplayName("可信反代（回环直连）采信 X-Forwarded-Host / X-Forwarded-Proto")
    void trustedProxy_forwardedHeadersHonored() {
        PublicBaseUrlResolver resolver = resolverWith("");

        assertEquals("https://smartwash.example.com",
                resolver.resolve(request("127.0.0.1", "smartwash-backend:8080", "http",
                        "smartwash.example.com", "https")),
                "反代场景必须用客户端实际使用的域名与协议，而非内网上游名");
    }

    @Test
    @DisplayName("不可信对端（公网直连）伪造的 X-Forwarded-* 一律忽略")
    void untrustedPeer_forwardedHeadersIgnored() {
        PublicBaseUrlResolver resolver = resolverWith("");

        assertEquals("http://8.148.70.81:9000",
                resolver.resolve(request("203.0.113.9", "8.148.70.81:9000", "http",
                        "evil.example.com", "https")),
                "无条件采信转发头会把短链指向任意域名（钓鱼面）");
    }

    @Test
    @DisplayName("多级代理时取逗号列表最左段（原始客户端请求的值）")
    void multiProxyTakesFirstSegment() {
        PublicBaseUrlResolver resolver = resolverWith("");

        assertEquals("https://smartwash.example.com",
                resolver.resolve(request("10.0.0.1", "smartwash-backend:8080", "http",
                        "smartwash.example.com, 10.0.0.2", "https, http")));
    }

    @Test
    @DisplayName("可信反代未传转发头时回退到 Host 头与实际协议")
    void trustedProxyWithoutForwardedHeaders_fallsBackToHost() {
        PublicBaseUrlResolver resolver = resolverWith("");

        assertEquals("http://smartwash-backend:8080",
                resolver.resolve(request("192.168.1.10", "smartwash-backend:8080", "http", null, null)));
    }

    @Test
    @DisplayName("无请求上下文（定时任务等非请求线程）返回空串，由调用方降级")
    void nullRequest_returnsBlank() {
        assertEquals("", resolverWith("").resolve(null));
    }
}
