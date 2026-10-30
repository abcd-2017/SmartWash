package com.smartwash.mood.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 今日情绪记录视图对象（PUT /web/auth/mood/today 的返回体）。
 */
@Data
@Accessors(chain = true)
public class MoodRecordVo {

    /** 记录日期，yyyy-MM-dd */
    private String date;

    /** 1暴雨 2阴 3多云 4晴 5大晴 */
    private Integer level;

    /** 快照当日颜色 */
    private String color;

    /** 一个词，可空 */
    private String word;

    /** 一句话备注，可空 */
    private String note;

    private LocalDateTime updatedAt;
}
