package com.smartwash.mood.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 月度情绪趋势统计视图对象（GET /web/auth/mood/stats）。
 */
@Data
@Accessors(chain = true)
public class MoodStatsVo {

    /** 统计月份，yyyy-MM */
    private String ym;

    /** 该月有记录的天数（uk_user_date 保证一天一条，即记录条数） */
    private Long recordedDays;

    /** 月均值（等级均值，保留2位；无记录时为 null） */
    private Double averageLevel;

    /** 各等级计数分布（按等级升序；空月为空数组） */
    private List<MoodLevelCountVo> distribution;
}
