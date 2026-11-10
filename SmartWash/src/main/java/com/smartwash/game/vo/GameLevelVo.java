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

    /** 关卡序号 */
    private Integer levelNo;

    /** 关卡名 */
    private String name;

    /** 关卡定义 JSON 字符串 */
    private String dataJson;

    /** 难度 */
    private Integer difficulty;

    /** 排序 */
    private Integer sort;
}
