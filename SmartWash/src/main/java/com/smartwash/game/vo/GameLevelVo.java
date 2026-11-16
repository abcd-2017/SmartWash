package com.smartwash.game.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 游戏关卡视图（下发项）。
 * dataJson 结构由游戏自定，端上按游戏解析。
 */
@Data
@Accessors(chain = true)
public class GameLevelVo {

    private Integer levelNo;

    private String name;

    private String dataJson;

    private Integer difficulty;

    private Integer sort;
}
