package com.smartwash.points.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 平台积分配置绑定（points.*，application.yaml 集中管理）。
 * 积分值规则只允许在此配置，业务代码禁止散落魔法数。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "points")
public class PointsProperties {

    private final SignIn signIn = new SignIn();

    private final Leaderboard leaderboard = new Leaderboard();

    @Data
    public static class SignIn {
        /** 每日签到基础分 */
        private int basePoints = 5;
        /** 连签奖励周期：连签天数每满 N 天（第 N/2N/3N…天）额外发放一次奖励 */
        private int streakBonusStep = 7;
        /** 连签奖励积分 */
        private int streakBonusPoints = 10;
        /** 签到接口每日尝试次数上限（Redis 计数防刷,幂等接口重复请求无副作用,仅防恶意打库） */
        private int attemptDailyLimit = 20;
    }

    @Data
    public static class Leaderboard {
        /** 月榜返回条数上限 */
        private int size = 50;
        /** /me 接口返回的最近流水条数 */
        private int recentLedgerSize = 20;
    }
}
