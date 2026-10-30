package com.smartwash.mood.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单个情绪等级的月度计数（GROUP BY mood_level，star_book MoodFrequency 模式）。
 */
@Data
@Accessors(chain = true)
public class MoodLevelCountVo {

    /** 1暴雨 2阴 3多云 4晴 5大晴 */
    private Integer level;

    /** 该等级当月的记录天数 */
    private Long recordCount;
}
