package com.smartwash.toolbox.listener;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.smartwash.toolbox.entity.ToolboxShortVisit;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import cn.hutool.crypto.digest.DigestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 短码点击统计监听器测试（方案 §5.4）。
 * 闸门语义：HEAD 与爬虫 UA 不计数；ip_hash 为 MD5 前 8 位不存明文；
 * visit 明细落库 + 主表 click_count 原子自增（setSql）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShortCodeVisitedListener 统计卫生测试")
class ShortCodeVisitedListenerTest {

    private static final String CODE = "abc2345";
    private static final String CLIENT_IP = "192.168.1.100";

    @Mock
    private ToolboxShortVisitService visitService;
    @Mock
    private ToolboxShortCodeService shortCodeService;

    private ShortCodeVisitedListener listener;

    @BeforeEach
    void setUp() {
        listener = new ShortCodeVisitedListener(visitService, shortCodeService);
    }

    private ShortCodeVisitedEvent event(String method, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI("/web/t/" + CODE);
        request.setRemoteAddr(CLIENT_IP);
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        return new ShortCodeVisitedEvent(CODE, request);
    }

    @Test
    @DisplayName("普通 GET：visit 明细落库（ip_hash=MD5 前 8 位）且 click_count 原子自增")
    void onVisited_normalGet_savesVisitAndIncrementsClick() {
        listener.onVisited(event("GET", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"));

        ArgumentCaptor<ToolboxShortVisit> captor = ArgumentCaptor.forClass(ToolboxShortVisit.class);
        verify(visitService).save(captor.capture());
        ToolboxShortVisit visit = captor.getValue();
        assertEquals(CODE, visit.getCode());
        assertEquals(DigestUtil.md5Hex(CLIENT_IP).substring(0, 8), visit.getIpHash(), "ip_hash 必须为 MD5 前 8 位");
        assertNull(visit.getReferer(), "无 Referer 时存 NULL");
        verify(shortCodeService).update(any(Wrapper.class));
    }

    @Test
    @DisplayName("HEAD 请求不计数")
    void onVisited_headRequest_notCounted() {
        listener.onVisited(event("HEAD", "Mozilla/5.0"));

        verify(visitService, never()).save(any(ToolboxShortVisit.class));
        verify(shortCodeService, never()).update(any(Wrapper.class));
    }

    @Test
    @DisplayName("爬虫 UA 不计数（bot/spider/crawler）")
    void onVisited_botUserAgent_notCounted() {
        listener.onVisited(event("GET", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
        listener.onVisited(event("GET", "SomeSpider/1.0"));
        listener.onVisited(event("GET", "web-crawler/3.0"));

        verify(visitService, never()).save(any(ToolboxShortVisit.class));
        verify(shortCodeService, never()).update(any(Wrapper.class));
    }

    @Test
    @DisplayName("空 UA 不计数")
    void onVisited_nullUserAgent_notCounted() {
        listener.onVisited(event("GET", null));

        verify(visitService, never()).save(any(ToolboxShortVisit.class));
    }

    @Test
    @DisplayName("可信内网直连（本机反代）时采信 X-Forwarded-For 首个 IP；超长 UA 截断到 512")
    void onVisited_xffAndLongUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        request.addHeader("Referer", "https://portal.example.com");
        request.addHeader("User-Agent", "Mozilla/5.0".repeat(120));

        listener.onVisited(new ShortCodeVisitedEvent(CODE, request));

        ArgumentCaptor<ToolboxShortVisit> captor = ArgumentCaptor.forClass(ToolboxShortVisit.class);
        verify(visitService).save(captor.capture());
        ToolboxShortVisit visit = captor.getValue();
        assertEquals(DigestUtil.md5Hex("203.0.113.7").substring(0, 8), visit.getIpHash(), "必须取 XFF 首个 IP");
        assertEquals("https://portal.example.com", visit.getReferer());
        assertEquals(512, visit.getUserAgent().length(), "UA 必须截断到 512，防超库列长度");
    }

    @Test
    @DisplayName("公网直连伪造 X-Forwarded-For：不采信代理头，按直连地址做摘要")
    void onVisited_publicRemoteAddr_ignoresSpoofedXff() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRemoteAddr("203.0.113.99");
        request.addHeader("X-Forwarded-For", "8.8.8.8");
        request.addHeader("User-Agent", "Mozilla/5.0");

        listener.onVisited(new ShortCodeVisitedEvent(CODE, request));

        ArgumentCaptor<ToolboxShortVisit> captor = ArgumentCaptor.forClass(ToolboxShortVisit.class);
        verify(visitService).save(captor.capture());
        assertEquals(DigestUtil.md5Hex("203.0.113.99").substring(0, 8),
                captor.getValue().getIpHash(), "公网直连不得采信伪造的 XFF 头");
    }
}
