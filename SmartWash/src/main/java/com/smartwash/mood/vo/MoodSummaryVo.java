package com.smartwash.mood.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 管理端情绪记录聚合报表视图对象（GET /admin/mood/summary）。
 * 口径与用户端 stats 一致：情绪维度即 mood_level（1暴雨 2阴 3多云 4晴 5大晴）。
 */
@Data
@Accessors(chain = true)
public class MoodSummaryVo {

    /** 区间内情绪记录总条数（一天一条，即记录天数之和） */
    private Long recordCount;

    /** 区间内有记录的去重用户数 */
    private Long userCount;

    /** 各情绪等级计数（GROUP BY mood_level 升序，仅含区间内实际出现的等级） */
    private List<EmotionCount> byEmotion;

    /** 按日计数（区间内逐日连续补零，无记录日为 0） */
    private List<DateCount> byDate;

    /** 单个情绪等级计数 */
    @Data
    @Accessors(chain = true)
    public static class EmotionCount {

        /** 1暴雨 2阴 3多云 4晴 5大晴 */
        private Integer moodLevel;

        /** 该等级区间内的记录条数 */
        private Long count;
    }

    /** 单日计数 */
    @Data
    @Accessors(chain = true)
    public static class DateCount {

        /** 记录日期，yyyy-MM-dd */
        private String date;

        /** 当日记录条数（无记录补 0） */
        private Long count;
    }
}
