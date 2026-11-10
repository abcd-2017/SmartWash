package com.smartwash.game.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端用户存档分页查询 DTO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GameSaveSearchFrom extends BaseSearchFrom {

    /** 游戏标识（精确，可空=全部） */
    private String gameKey;

    /** 用户 ID（精确，可空=全部） */
    private Long userId;
}
