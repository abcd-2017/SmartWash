package com.smartwash.game.vo;

import lombok.Data;

import java.util.List;

/**
 * 游戏排行榜视图（GET /web/auth/game/leaderboard）。
 * 数据源：Redis ZSet（game:lb:{gameKey}）为展示缓存，game_score_entry 为事实源。
 * 同步失败时降级为直接查库（见 GameScoreServiceImpl）。
 */
@Data
public class GameLeaderboardVo {

    /** 游戏标识 */
    private String gameKey;

    /** 榜单列名（最高分/最少步数/最高关卡） */
    private String metricLabel;

    /** 排序方向：desc / asc */
    private String metricOrder;

    /** TOP N（未开启排行榜时为空列表） */
    private List<EntryVo> entries;

    /** 我的排名（未上榜或未登录为 null） */
    private MyRankVo my;

    @Data
    public static class EntryVo {
        /** 名次（1 起） */
        private Integer rank;
        /** 用户 ID */
        private Long userId;
        /** 脱敏手机号（138****1234） */
        private String maskedPhone;
        /** 头像 URL（可空） */
        private String avatar;
        /** 主排序值 */
        private Long metricValue;
        /** 辅助值（用时/连击等，可空） */
        private Long metricExtra;
    }

    @Data
    public static class MyRankVo {
        /** 名次（1 起，未上榜 null） */
        private Integer rank;
        /** 主排序值 */
        private Long metricValue;
    }
}
