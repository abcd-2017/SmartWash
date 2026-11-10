package com.smartwash.game.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端成绩分页查询 DTO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GameScoreSearchFrom extends BaseSearchFrom {

    /** 游戏标识（精确，可空=全部） */
    private String gameKey;
}
