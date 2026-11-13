package com.smartwash.toolbox.controller;

import com.smartwash.common.Result;
import com.smartwash.common.ratelimit.RateLimit;
import com.smartwash.toolbox.from.SecretViewFrom;
import com.smartwash.toolbox.listener.ShortCodeVisitedEvent;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.vo.SecretMetaVo;
import com.smartwash.toolbox.vo.SecretViewVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具箱私密文本（阅后即焚）公开查看控制器。
 * 路径前缀 /web/s/**，匿名访问（接收者无需登录，码即凭证）。
 * 语义：meta 只读不焚毁；view 必须 POST 显式触发（防链接预览爬虫提前烧毁），成功即焚毁。
 */
@Tag(name = "工作台-阅后即焚", description = "私密文本公开查看（匿名访问，一次性揭示）")
@Slf4j
@RestController
@RequestMapping("/web/s")
@RequiredArgsConstructor
public class ToolboxSecretController {

    private final ToolboxShortCodeService toolboxShortCodeService;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "私密文本元信息",
            description = "仅返回是否需要口令；404 不存在/非私密文本；410 已焚毁/已过期（信封业务码，落地页据此分支）")
    @GetMapping("/{code}/meta")
    @RateLimit(name = "secret-meta", window = 60, limit = 30, message = "访问过于频繁，请稍后再试")
    public Result<SecretMetaVo> meta(@PathVariable("code") String code) {
        return Result.ok(toolboxShortCodeService.secretMeta(code));
    }

    @Operation(summary = "一次性揭示",
            description = "POST 显式触发焚毁；口令错误 403 且不焚毁（延迟 500ms 防爆破）；成功即焚毁并返回明文，二次访问 410")
    @PostMapping("/{code}/view")
    @RateLimit(name = "secret-view", window = 60, limit = 10, message = "操作过于频繁，请稍后再试")
    public Result<SecretViewVo> view(@PathVariable("code") String code,
                                     @RequestBody(required = false) @Valid SecretViewFrom from,
                                     HttpServletRequest request) {
        SecretViewVo vo = toolboxShortCodeService.viewSecret(code, from == null ? null : from.getPassword());
        // 统计异步化：仅领取成功计入访问明细；失败尝试不计入（不为爆破者留下探测反馈）
        eventPublisher.publishEvent(new ShortCodeVisitedEvent(code, request));
        return Result.ok(vo);
    }
}
