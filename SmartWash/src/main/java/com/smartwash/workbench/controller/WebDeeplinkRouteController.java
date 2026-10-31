package com.smartwash.workbench.controller;

import com.smartwash.common.Result;
import com.smartwash.workbench.service.DeeplinkRouteService;
import com.smartwash.workbench.vo.DeeplinkRouteVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * App deeplink 路由字典用户端控制器（公开接口）。
 * 路径前缀 /web/deeplink，无需认证（SecurityConfig permitAll 放行）。
 * 四端联动说明：本接口为新增接口，不改动任何既有接口；Android 端后续接入，
 * 鸿蒙端本次不接入（端上冷启动解析对未知 host 仅能展示兜底元数据）。
 */
@Tag(name = "工作台-路由字典", description = "App deeplink 路由字典公开下发")
@Slf4j
@RestController
@RequestMapping("/web/deeplink")
@RequiredArgsConstructor
public class WebDeeplinkRouteController {

    private final DeeplinkRouteService deeplinkRouteService;

    @Operation(summary = "deeplink 路由字典", description = "仅启用中的路由，按 id 升序；端上冷启动拉取做未知 host 的元数据兜底（name/requireLogin）")
    @GetMapping("/routes")
    public Result<List<DeeplinkRouteVo>> routes() {
        return Result.ok(deeplinkRouteService.listEnabledRoutes());
    }
}
