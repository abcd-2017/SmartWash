package com.smartwash.controller.background;

import com.smartwash.common.Result;
import com.smartwash.service.IDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.vo.dashboard.DashboardTrendVo;
import com.smartwash.vo.dashboard.DashboardVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作台统计控制器
 */
@Tag(name = "仪表盘", description = "后台工作台统计数据")
@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final IDashboardService dashboardService;

    @Operation(summary = "获取仪表盘统计数据", description = "获取学校总数、用户总数、今日订单数、今日收入等统计信息")
    @GetMapping("/stats")
    public Result<DashboardVo> getStats() {
        return Result.ok(dashboardService.getDashboardStats());
    }

    @Operation(summary = "获取收入与订单趋势", description = "最近 N 天的逐日收入（支付成功流水合计，口径与今日收入一致）与订单创建数，无数据日补零保证折线连续")
    @GetMapping("/trend")
    public Result<List<DashboardTrendVo>> getTrend(
            @RequestParam(required = false)
            @Parameter(description = "统计天数，默认 30，上限 90", example = "30") Integer days) {
        return Result.ok(dashboardService.getDashboardTrend(days));
    }
}
