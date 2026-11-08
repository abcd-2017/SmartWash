package com.smartwash.points.vo;

import lombok.Data;

import java.util.List;

/**
 * 我的积分概览（GET /web/auth/points/me）。
 */
@Data
public class PointsOverviewVo {

    /** 当前积分余额（无账户=0） */
    private Integer balance;

    /** 累计获得积分（无账户=0） */
    private Integer totalEarned;

    /** 今日是否已签 */
    private Boolean signedToday;

    /**
     * 当前连签天数：
     * 今日已签 = 签到时点记录的 streak；
     * 今日未签 = 连签预览（昨日有签到则昨日 streak+1，否则 0），提示端上"今天签将达 N 天"。
     */
    private Integer streak;

    /** 距下次连签奖励还需的签到次数（含今日，若今日未签） */
    private Integer nextBonusIn;

    /** 最近流水（时间倒序，条数见配置 points.leaderboard.recent-ledger-size） */
    private List<PointLedgerItemVo> recentLedger;
}
