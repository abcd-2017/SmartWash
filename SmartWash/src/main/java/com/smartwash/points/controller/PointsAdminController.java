package com.smartwash.points.controller;

import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import com.smartwash.points.service.IPointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分管理端控制器。
 * 路径前缀 /admin/points，需 ROLE_ADMIN（SecurityConfig 统一拦截）。
 * 当前仅月榜重建（B6A）：ZSet 残留/漂移时以 point_ledger 当月聚合为事实源一次性对账。
 */
@Tag(name = "积分-管理端", description = "积分运营维护（ROLE_ADMIN）")
@Slf4j
@RestController
@RequestMapping("/admin/points")
@RequiredArgsConstructor
public class PointsAdminController {

    private final IPointsService pointsService;

    @Operation(summary = "重建月榜 ZSet", description = "以 point_ledger 当月聚合为事实源重置 Redis 月榜（ZSet 残留/漂移对账）；month=yyyyMM，不传为当月")
    @Audited(action = "POINTS_LEADERBOARD_REBUILD", targetType = "points_leaderboard", targetIdSpEL = "#month")
    @PostMapping("/leaderboard/rebuild")
    public Result<String> rebuildLeaderboard(
            @Parameter(description = "yyyyMM，不传为当月") @RequestParam(required = false) String month) {
        int count = pointsService.rebuildMonthLeaderboard(month);
        return Result.ok("月榜重建完成，入榜 " + count + " 人");
    }
}
