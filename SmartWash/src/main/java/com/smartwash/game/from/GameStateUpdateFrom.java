package com.smartwash.game.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 游戏存档写入请求（PUT /web/auth/game/state）。
 * stateJson 为游戏自定义结构，服务端只校验 JSON 合法性，不解析语义。
 */
@Data
@Accessors(chain = true)
public class GameStateUpdateFrom {

    /** 游戏标识 */
    @NotBlank(message = "gameKey 不能为空")
    private String gameKey;

    /** 游戏自定义存档 JSON 字符串 */
    @NotBlank(message = "存档内容不能为空")
    private String stateJson;

    /** 存档结构版本（游戏侧升级时递增，可空按 1 处理） */
    private Integer schemaVer;
}
