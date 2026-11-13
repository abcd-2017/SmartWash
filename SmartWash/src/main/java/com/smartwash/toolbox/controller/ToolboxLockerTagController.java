package com.smartwash.toolbox.controller;

import com.smartwash.common.Result;
import com.smartwash.common.ratelimit.RateLimit;
import com.smartwash.toolbox.service.ToolboxLockerTagService;
import com.smartwash.toolbox.vo.LockerTagVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 储物柜资产码公开解析控制器（一物一码 M3）。
 * 路径前缀 /web/locker-tags/**，匿名访问（扫码流量，码即凭证）。
 * 扫码链路：相机扫柜上二维码 → /web/t/{code} 302（此处记访问明细 = 扫码量）
 * → Web 公开落地页 /l/{lockerId} → 本控制器取信息卡（纯读，不重复计数）。
 */
@Tag(name = "工作台-柜码", description = "储物柜资产码公开解析（匿名访问，扫码落地页数据源）")
@Slf4j
@RestController
@RequestMapping("/web/locker-tags")
@RequiredArgsConstructor
public class ToolboxLockerTagController {

    private final ToolboxLockerTagService toolboxLockerTagService;

    @Operation(summary = "柜码解析",
            description = "别名 L{lockerId} 活码校验（存在/类型/绑定一致）后返回柜子信息卡；不存在/不匹配 404。扫码量由 /web/t 302 链路计入访问明细，本接口为纯读不重复计数")
    @GetMapping("/{code}")
    @RateLimit(name = "locker-tag", window = 60, limit = 30, message = "访问过于频繁，请稍后再试")
    public Result<LockerTagVo> resolve(@PathVariable("code") String code) {
        return Result.ok(toolboxLockerTagService.resolve(code));
    }
}
