package com.smartwash.game.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端成绩列表项。
 */
@Data
public class GameScoreAdminVo {

    /** 条目 ID */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 脱敏手机号 */
    private String maskedPhone;

    /** 游戏标识 */
    private String gameKey;

    /** 游戏名 */
    private String gameTitle;

    /** 主排序值 */
    private Long metricValue;

    /** 辅助值 */
    private Long metricExtra;

    /** 详情 JSON */
    private String metricJson;

    /** 累计上报次数 */
    private Integer playedCount;

    /** 最近上报时间 */
    private LocalDateTime lastPlayedAt;
}
