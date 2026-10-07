package com.smartwash.toolbox.controller;

import com.smartwash.toolbox.listener.ShortCodeVisitedEvent;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.service.ToolboxShortCodeService.ResolveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 公开跳转控制器测试（方案 §十 4 项的服务层部分）。
 * 语义：302 + Cache-Control: no-store；不存在/私有 404；生命周期 410；
 * 仅成功跳转发布点击统计事件。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ToolboxRedirectController 302/404/410 语义测试")
class ToolboxRedirectControllerTest {

    private static final String CODE = "abc2345";

    @Mock
    private ToolboxShortCodeService shortCodeService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ToolboxRedirectController controller;

    @BeforeEach
    void setUp() {
        controller = new ToolboxRedirectController(shortCodeService, eventPublisher);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/web/t/" + CODE);
        request.setRemoteAddr("1.2.3.4");
        return request;
    }

    @Test
    @DisplayName("成功解析：302 + Location 指向目标 + Cache-Control: no-store，并发布统计事件")
    void redirect_success_302WithNoStore() {
        when(shortCodeService.resolveForRedirect(CODE)).thenReturn(ResolveResult.ok("https://example.com/page"));

        ResponseEntity<Void> response = controller.redirect(CODE, request());

        assertEquals(HttpStatus.FOUND, response.getStatusCode(), "一律 302 临时重定向");
        assertEquals("https://example.com/page", response.getHeaders().getLocation().toString());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"), "必须带 no-store 保证改向即时生效");
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(ShortCodeVisitedEvent.class));    }

    @Test
    @DisplayName("未命中/私有码：404 且不发布统计事件")
    void redirect_notFound_404WithoutStats() {
        when(shortCodeService.resolveForRedirect(CODE)).thenReturn(ResolveResult.notFound());

        ResponseEntity<Void> response = controller.redirect(CODE, request());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("过期/未解锁/达上限：410 且不发布统计事件")
    void redirect_unavailable_410WithoutStats() {
        when(shortCodeService.resolveForRedirect(CODE)).thenReturn(ResolveResult.unavailable());

        ResponseEntity<Void> response = controller.redirect(CODE, request());

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}
