package com.smartwash.game.from;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 关卡保存请求（管理端 upsert，POST /admin/game/levels）。
 * dataJson 结构由游戏自定，服务端只校验 JSON 合法性。
 */
@Data
@Accessors(chain = true)
public class GameLevelSaveFrom {

    /** 游戏标识 */
    @NotBlank(message = "gameKey 不能为空")
    private String gameKey;

    /** 关卡序号 */
    @NotNull(message = "关卡序号不能为空")
    @Min(value = 1, message = "关卡序号必须为正整数")
    private Integer levelNo;

    /** 关卡名 */
    private String name;

    /** 关卡定义 JSON 字符串 */
    @NotBlank(message = "关卡数据不能为空")
    private String dataJson;

    /** 难度（展示用） */
    private Integer difficulty;

    /** 1 下发 0 不下发（可空按 1） */
    private Integer enabled;

    /** 排序，越小越靠前（可空按关卡序号×10） */
    private Integer sort;

    /** 备注 */
    private String remark;
}
