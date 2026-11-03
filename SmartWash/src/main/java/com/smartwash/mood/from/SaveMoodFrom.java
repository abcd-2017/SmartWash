package com.smartwash.mood.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 记录/覆盖今日情绪请求 DTO（Save = upsert 语义，同日重复提交即覆盖）。
 */
@Data
public class SaveMoodFrom {

    /** 情绪等级：1暴雨 2阴 3多云 4晴 5大晴（必填，DB CHECK 1..5 最终兜底） */
    @NotNull(message = "情绪等级不能为空")
    @Min(value = 1, message = "情绪等级须为1..5")
    @Max(value = 5, message = "情绪等级须为1..5")
    private Integer level;

    /** 快照当日颜色（必填，#RRGGBB 或 #RRGGBBAA，上限9字符） */
    @NotBlank(message = "颜色不能为空")
    @Size(max = 9, message = "颜色格式非法（#RRGGBB/#RRGGBBAA）")
    private String color;

    /** 一个词（累/顺/静/燥/燃等），可空，上限20字符 */
    @Size(max = 20, message = "情绪词过长（上限20字符）")
    private String word;

    /** 一句话备注，可空，上限200字符 */
    @Size(max = 200, message = "备注过长（上限200字符）")
    private String note;
}
