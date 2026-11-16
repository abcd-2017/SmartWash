package com.smartwash.game.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GameScoreAdminVo {

    private Long id;

    private Long userId;

    private String maskedPhone;

    private String gameKey;

    private String gameTitle;

    /** 主排序值 */
    private Long metricValue;

    private Long metricExtra;

    private String metricJson;

    private Integer playedCount;

    private LocalDateTime lastPlayedAt;
}
