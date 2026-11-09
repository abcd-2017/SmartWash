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
 * 游戏关卡定义，对应表 game_level。
 * 一游戏一序号一行（uk_game_level）。
 *
 * <p>仅<b>关卡定义型游戏</b>使用（sokoban / nonogram / magic-tower / slingshot /
 * memory-cards / crossword / idiom / beat-tap）；纯分数型游戏（2048 / flappy / tetris…）不建关卡行。</p>
 *
 * <p>关卡与游戏包解耦：扩展关卡只改本表，不重传游戏包；data_json 结构由游戏自定，
 * 后台按游戏渲染专用编辑器（过渡期用通用 JSON 编辑器）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("game_level")
public class GameLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 游戏标识（game_config.game_key） */
    private String gameKey;

    /** 关卡序号（游戏内展示顺序） */
    private Integer levelNo;

    /** 关卡名 */
    private String name;

    /** 关卡定义（JSON 字符串，结构由游戏自定） */
    private String dataJson;

    /** 难度（展示用，语义由游戏自定） */
    private Integer difficulty;

    /** 1 下发 0 不下发 */
    private Integer enabled;

    /** 排序，越小越靠前 */
    private Integer sort;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
