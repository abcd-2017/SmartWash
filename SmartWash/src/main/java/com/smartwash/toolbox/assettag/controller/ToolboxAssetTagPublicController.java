package com.smartwash.toolbox.assettag.controller;

import com.smartwash.common.Result;
import com.smartwash.common.ratelimit.RateLimit;
import com.smartwash.toolbox.assettag.service.ToolboxAssetTagService;
import com.smartwash.toolbox.assettag.vo.AssetTagPublicVo;
import com.smartwash.toolbox.listener.ShortCodeVisitedEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物品标签公开解析控制器（一物一码 M2）。
 * 路径前缀 /web/tag/**，匿名访问（捡到物品的人扫码，无需登录）。
 * 扫码线索：访问明细事件（IP 仅摘要）+ 明细表最近扫码时间，仅失主可见。
 */
@Tag(name = "工作台-物品标签公开", description = "物品标签扫码解析（匿名访问，寻物页数据源）")
@Slf4j
@RestController
@RequestMapping("/web/tag")
@RequiredArgsConstructor
public class ToolboxAssetTagPublicController {

    private final ToolboxAssetTagService toolboxAssetTagService;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "标签解析",
            description = "返回物品信息卡；联系方式仅「寻物中 + 失主已留」时解密下发；每次解析计入失主扫码线索")
    @GetMapping("/{code}")
    @RateLimit(name = "asset-tag-view", window = 60, limit = 30, message = "访问过于频繁，请稍后再试")
    public Result<AssetTagPublicVo> resolve(@PathVariable("code") String code, HttpServletRequest request) {
        AssetTagPublicVo vo = toolboxAssetTagService.resolvePublic(code);
        // 扫码线索异步化：访问明细（IP 摘要）+ 明细表最近扫码时间，均只对失主可见
        eventPublisher.publishEvent(new ShortCodeVisitedEvent(code, request));
        return Result.ok(vo);
    }
}
