package com.smartwash.game.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.game.entity.GameScoreEntry;
import com.smartwash.game.from.GameScoreSearchFrom;
import com.smartwash.game.vo.GameLeaderboardVo;
import com.smartwash.game.vo.GameScoreAdminVo;

import java.util.List;

/**
 * 游戏成绩 / 排行榜服务。
 * 事实源为 game_score_entry，Redis ZSet（game:lb:{gameKey}）仅作展示缓存。
 *
 * <p>排序方向由 game_config.metric_order 决定：desc 大在前（分数类）/ asc 小在前（步数、用时类）。
 * 未开启排行榜（metric_enabled=0）的游戏不上报、不下发。</p>
 */
public interface GameScoreService extends IService<GameScoreEntry> {

    /**
     * 上报成绩：按 (userId, gameKey) 取历史最佳，刷新才更新；played_count 自增。
     * 同步 Redis ZSet（失败仅告警，允许短暂不一致）。
     *
     * @return 落库后的条目
     */
    GameScoreEntry reportScore(Long userId, String gameKey, Long metricValue, Long metricExtra, String metricJson);

    /**
     * 排行榜 TOP N（含当前用户排名）。
     *
     * @param gameKey 游戏标识
     * @param limit   取前 N
     * @param userId  当前用户（用于算「我的排名」，可空）
     * @return 榜单视图；未开启排行榜时 entries 为空
     */
    GameLeaderboardVo getLeaderboard(String gameKey, int limit, Long userId);

    /**
     * 管理端成绩分页列表（按 gameKey 筛选，联表补脱敏手机号与游戏名，按 metric_value 降序）。
     */
    Page<GameScoreAdminVo> adminPage(GameScoreSearchFrom from);
}
