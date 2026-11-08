package com.smartwash.points.service;

import com.smartwash.points.vo.LeaderboardVo;
import com.smartwash.points.vo.PointsOverviewVo;
import com.smartwash.points.vo.SignInResultVo;

/**
 * 平台积分服务（v1：每日签到 + 月榜；管理端调分/积分商城/过期/发券联动全部后置）。
 */
public interface IPointsService {

    /**
     * 每日签到（幂等）：
     * 并发三防 = 唯一索引兜底 + 事务内 INSERT 捕获重复键判幂等返回 + 账户加分走条件 UPDATE 单语句自增。
     *
     * @param userId 用户 ID（来自登录上下文）
     */
    SignInResultVo signIn(Long userId);

    /**
     * 我的积分概览：余额 + 今日是否已签 + 连签天数 + 最近流水（只读，懒展示不落账户行）。
     */
    PointsOverviewVo getOverview(Long userId);

    /**
     * 积分月榜（当前仅 scope=month）：Redis ZSet TOP N（脱敏）+ 我的排名。
     */
    LeaderboardVo getMonthLeaderboard(String scope, Long userId);
}
