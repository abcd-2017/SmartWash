package com.smartwash.game.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 游戏关卡下发视图（GET /web/auth/game/levels）。
 * version 供端上判断缓存是否失效：版本一致直接用本地缓存，不一致重新拉取。
 */
@Data
@Accessors(chain = true)
public class GameLevelsVo {

    /** 游戏标识 */
    private String gameKey;

    /** 关卡数据版本号（game_config.levels_version） */
    private Integer version;

    /** 启用中的关卡（按 sort / levelNo 排序） */
    private List<GameLevelVo> levels;
}
