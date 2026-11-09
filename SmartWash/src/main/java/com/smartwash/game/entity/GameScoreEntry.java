package com.smartwash.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 游戏排行榜条目，对应表 game_score_entry。
 * 一人一游戏一行（uk_user_game），取历史最佳：desc 游戏比大、asc 游戏比小（由 game_config.metric_order 决定）。
 *
 * <p>本表是排行榜<b>事实源</b>；Redis ZSet（game:lb:{gameKey}）仅作展示缓存，
 * 同步失败只告警、允许短暂不一致（与 point_ledger / points:lb 同一纪律）。</p>
 *
 * <p>metric_value 语义由游戏自定（分数 / 步数 / 到达关卡 / 胜场…），服务端不解释、只比较大小。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("game_score_entry")
public class GameScoreEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 游戏标识（game_config.game_key） */
    private String gameKey;

    /** 主排序值（游戏自定义语义） */
    private Long metricValue;

    /** 辅助值（用时 / 连击等，用于同分 tie-break 或展示） */
    private Long metricExtra;

    /** 详情（评级 / 关卡快照等，JSON 字符串，结构由游戏自定） */
    private String metricJson;

    /** 累计上报次数 */
    private Integer playedCount;

    /** 最近一次上报时间 */
    private LocalDateTime lastPlayedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
