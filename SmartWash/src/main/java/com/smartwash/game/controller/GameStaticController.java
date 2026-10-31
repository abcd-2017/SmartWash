package com.smartwash.game.controller;

import com.smartwash.exception.GameServiceException;
import com.smartwash.game.service.GameStaticService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 游戏静态资源公开转发控制器。
 * 路径前缀 /web/game/**，匿名访问（WebView 内相对资源无 cookie 场景，方案 §四 权限取舍）。
 * 面向浏览器的 200/304/403/404 响应本身即语义，不走 Result 信封（对齐 ToolboxRedirectController 先例）。
 * 缓存：Cache-Control public, max-age=3600 + ETag（gameKey|updated_at|路径 摘要），If-None-Match 命中回 304。
 */
@Tag(name = "游戏中心-静态转发", description = "H5 游戏静态资源公开转发（匿名访问，浏览器消费方）")
@Slf4j
@RestController
@RequestMapping("/web/game")
@RequiredArgsConstructor
public class GameStaticController {

    /** 静态资源缓存时长：1 小时（重传游戏靠 updated_at 变化驱动 ETag 失效） */
    private static final Duration CACHE_MAX_AGE = Duration.ofHours(1);

    private final GameStaticService gameStaticService;

    @Operation(summary = "游戏静态资源转发", description = "公开转发 MinIO game/{gameKey}/ 前缀对象；未知/停用游戏 404，路径穿越 403；入口路径缺省回 entry_path（默认 index.html）；支持 If-None-Match 304")
    @GetMapping("/{gameKey}/**")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> serve(
            @PathVariable("gameKey") String gameKey, HttpServletRequest request) {
        String relativePath = extractRelativePath(request, gameKey);

        GameStaticService.ResolvedObject resolved;
        try {
            resolved = gameStaticService.resolve(gameKey, relativePath);
        } catch (GameServiceException e) {
            // 路径穿越（403）/编码非法（400）：浏览器资源语义，裸状态码，不走信封
            log.warn("游戏转发请求被拒绝, gameKey: {}, path: {}, code: {}", gameKey, relativePath, e.getCode());
            return ResponseEntity.status(e.getCode()).build();
        }
        if (resolved == null) {
            log.debug("游戏转发未命中, gameKey: {}, path: {}", gameKey, relativePath);
            return ResponseEntity.notFound().build();
        }

        String etag = resolved.etag();
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (etag.equals(ifNoneMatch)) {
            closeQuietly(resolved);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE).cachePublic())
                    .eTag(etag)
                    .build();
        }

        MediaType mediaType = MediaType.parseMediaType(resolved.contentType());
        org.springframework.core.io.InputStreamResource body =
                new org.springframework.core.io.InputStreamResource(resolved.stream().input());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE).cachePublic())
                .eTag(etag)
                .contentType(mediaType);
        if (resolved.contentLength() >= 0) {
            builder.contentLength(resolved.contentLength());
        }
        return builder.body(body);
    }

    /**
     * 提取 /web/game/{gameKey} 之后的原始（未解码）相对路径；无尾段返回空串（由 service 回退 entry_path）。
     * 手工截取而非 AntPathMatcher（{gameKey}/** 混合模板下 extractPathWithinPattern 语义不可靠）；
     * 返回值可能保留前导 "/"（URL 结构本身），由 service 规范化消化。
     */
    private String extractRelativePath(HttpServletRequest request, String gameKey) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        String prefix = "/web/game/" + gameKey;
        if (uri.length() <= prefix.length() || !uri.startsWith(prefix)) {
            return "";
        }
        return uri.substring(prefix.length());
    }

    private void closeQuietly(GameStaticService.ResolvedObject resolved) {
        try {
            resolved.stream().close();
        } catch (Exception ignore) {
            // 304 路径下流尽快释放，失败静默
        }
    }
}
