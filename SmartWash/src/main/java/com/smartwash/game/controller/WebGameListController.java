package com.smartwash.game.controller;

import com.smartwash.common.Result;
import com.smartwash.game.service.GameConfigService;
import com.smartwash.game.vo.GameVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 游戏中心用户端控制器。
 * 路径前缀 /web/auth/game，需 ROLE_USER（SecurityConfig 统一拦截）。
 * 四端联动说明：本接口为新增接口，不改动任何既有接口；Android 端 :feature:game 为消费方，
 * 鸿蒙端后续按同契约接入；卡片点击跳 smartwash://web?url={url}&title={title} 复用既有 WebView 容器。
 */
@Tag(name = "游戏中心-用户端", description = "游戏中心列表下发")
@Slf4j
@RestController
@RequestMapping("/web/auth/game")
@RequiredArgsConstructor
public class WebGameListController {

    private final GameConfigService gameConfigService;

    @Operation(summary = "游戏列表", description = "仅启用中（enabled=1）的游戏，按 sort 升序、id 升序，不分页；icon/color/category 为端上白名单枚举 key，未知值显示默认样式；url 为可直接加载的可玩地址")
    @GetMapping("/list")
    public Result<List<GameVo>> list() {
        return Result.ok(gameConfigService.listEnabledGames());
    }
}
