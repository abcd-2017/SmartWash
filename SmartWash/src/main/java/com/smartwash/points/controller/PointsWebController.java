package com.smartwash.points.controller;

import com.smartwash.common.Result;
import com.smartwash.points.service.IPointsService;
import com.smartwash.points.vo.LeaderboardVo;
import com.smartwash.points.vo.PointsOverviewVo;
import com.smartwash.points.vo.SignInResultVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台积分用户端控制器。
 * 路径前缀 /web/auth/points/**（SecurityConfig 已有 /web/auth/** → ROLE_USER 通配，无需新增规则）。
 * v1 范围：签到 / 概览 / 月榜；管理端调分、积分商城、过期、发券联动全部后置。
 */
@Tag(name = "积分-用户端", description = "每日签到、我的积分、积分月榜接口")
@Slf4j
@RestController
@RequestMapping("/web/auth/points")
@RequiredArgsConstructor
public class PointsWebController {

    private final IPointsService pointsService;

    @Operation(summary = "我的积分概览", description = "余额 + 累计获得 + 今日是否已签 + 连签天数 + 距下次奖励 + 最近流水")
    @GetMapping("/me")
    public Result<PointsOverviewVo> me() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(pointsService.getOverview(user.getUserId()));
    }

    @Operation(summary = "每日签到", description = "当日签到（幂等：重复请求返回 alreadySigned=true 不重复加分）；连签每满周期天数额外发奖励")
    @PostMapping("/sign-in")
    public Result<SignInResultVo> signIn() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(pointsService.signIn(user.getUserId()));
    }

    @Operation(summary = "积分月榜", description = "Redis 月榜 TOP 50（手机号脱敏展示）+ 我的排名；当前仅支持 scope=month")
    @GetMapping("/leaderboard")
    public Result<LeaderboardVo> leaderboard(
            @RequestParam("scope") @Parameter(description = "榜单口径，当前仅 month") String scope) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(pointsService.getMonthLeaderboard(scope, user.getUserId()));
    }
}
