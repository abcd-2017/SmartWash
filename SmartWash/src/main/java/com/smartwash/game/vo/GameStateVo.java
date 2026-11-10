package com.smartwash.game.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 游戏存档视图（GET /web/auth/game/state）。
 * 无存档时 stateJson 为 null（首次进入游戏的正常态）。
 */
@Data
@Accessors(chain = true)
public class GameStateVo {

    /** 游戏标识 */
    private String gameKey;

    /** 游戏自定义存档 JSON 字符串（无存档时 null） */
    private String stateJson;

    /** 存档结构版本 */
    private Integer schemaVer;

    /** 最近更新时间（无存档时 null） */
    private LocalDateTime updatedAt;
}
