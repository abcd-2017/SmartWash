package com.smartwash.controller.background;

import com.smartwash.common.Result;
import com.smartwash.service.IDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.vo.dashboard.DashboardVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
