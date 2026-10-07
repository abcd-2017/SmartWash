package com.smartwash.toolbox.controller;

import com.smartwash.toolbox.listener.ShortCodeVisitedEvent;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 工作台工具箱公开跳转控制器。
 * 路径前缀 /web/t/**，匿名访问（浏览器/扫码流量）。
 * 面向浏览器的 302/404/410 响应本身即语义，不走 Result 信封（方案 §6）。
 */
@Tag(name = "工作台-短链跳转", description = "公开短码跳转（匿名访问，非 JSON 消费方）")
@Slf4j
@RestController
@RequestMapping("/web/t")
@RequiredArgsConstructor
public class ToolboxRedirectController {

    private final ToolboxShortCodeService toolboxShortCodeService;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "短码 302 跳转", description = "公开码 302 至目标；不存在/私有 404；过期/未解锁/达上限 410")
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable("code") String code, HttpServletRequest request) {
        ToolboxShortCodeService.ResolveResult r = toolboxShortCodeService.resolveForRedirect(code);
        if (r.isNotFound()) {
            log.debug("短码跳转 404, code: {}", code);
            return ResponseEntity.notFound().build();
        }
        if (r.isUnavailable()) {
            log.debug("短码跳转 410, code: {}", code);
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        // 统计异步化，不阻塞跳转
        eventPublisher.publishEvent(new ShortCodeVisitedEvent(code, request));
        // 一律临时重定向 + no-store：保证活码改向立即生效，浏览器不缓存 302 结果
        return ResponseEntity.status(HttpStatus.FOUND)
                .cacheControl(CacheControl.noStore())
                .location(URI.create(r.getTarget()))
                .build();
    }
}
