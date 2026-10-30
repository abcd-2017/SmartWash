package com.smartwash.mood.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 月历单日情绪视图对象（GET /web/auth/mood/calendar）。
 * 仅返回有记录的日期，无记录日期由端上缺省灰格。
 */
@Data
@Accessors(chain = true)
public class MoodCalendarDayVo {

    /** 记录日期，yyyy-MM-dd */
    private String date;

    /** 1暴雨 2阴 3多云 4晴 5大晴 */
    private Integer level;

    /** 快照当日颜色 */
    private String color;

    /** 一个词，可空 */
    private String word;
}
