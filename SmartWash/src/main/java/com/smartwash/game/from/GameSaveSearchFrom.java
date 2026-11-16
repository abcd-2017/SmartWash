package com.smartwash.game.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GameSaveSearchFrom extends BaseSearchFrom {

    /** 游戏标识（精确，可空=全部） */
    private String gameKey;

    /** 用户 ID（精确，可空=全部） */
    private Long userId;
}
