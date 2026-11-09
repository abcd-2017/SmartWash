package com.smartwash.game.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 游戏成绩上报请求（POST /web/auth/game/score）。
 * metricValue 语义由游戏自定（分数 / 步数 / 到达关卡 / 胜场…），服务端只比较大小。
 */
@Data
@Accessors(chain = true)
public class GameScoreReportFrom {

    /** 游戏标识 */
    @NotBlank(message = "gameKey 不能为空")
    private String gameKey;

    /** 主排序值（游戏自定义语义） */
    @NotNull(message = "成绩值不能为空")
    private Long metricValue;

    /** 辅助值（用时 / 连击等，可空） */
    private Long metricExtra;

    /** 详情 JSON（评级 / 关卡快照等，可空） */
    private String metricJson;
}
