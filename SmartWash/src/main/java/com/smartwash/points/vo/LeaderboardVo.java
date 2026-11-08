package com.smartwash.points.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 积分月榜（GET /web/auth/points/leaderboard?scope=month）。
 * 数据源 Redis ZSet（key: points:lb:{yyyyMM}），以 point_ledger 为事实源，
 * 榜与库允许短暂不一致（同步失败仅告警）。
 */
@Data
public class LeaderboardVo {

    /** 榜单口径（当前仅 month） */
    private String scope;

    /** 榜单月份（yyyyMM） */
    private String month;

    /** TOP N（N 见配置 points.leaderboard.size） */
    private List<EntryVo> entries;

    /** 我的排名（未上榜=null） */
    private MyRankVo my;

    @Data
    public static class EntryVo {
        /** 名次（1 起） */
        private Integer rank;
        /** 用户 ID */
        private Long userId;
        /** 脱敏手机号（138****1234，对齐既有 maskPhone 口径） */
        private String maskedPhone;
        /** 头像 URL（可空） */
        private String avatar;
        /** 月内签到积分 */
        private Integer points;
    }

    @Data
    public static class MyRankVo {
        /** 名次（1 起，未上榜=null） */
        private Integer rank;
        /** 月内签到积分 */
        private Integer points;
    }

    /**
     * ZSet 分数统一转整型，避免双精度表示污染响应。
     */
    public static Integer toIntPoints(BigDecimal score) {
        return score == null ? 0 : score.intValue();
    }
}
