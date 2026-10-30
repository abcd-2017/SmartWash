package com.smartwash.mood.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪气象站每日情绪记录。
 * 一天一条（uk_user_date 唯一索引裁决），重复提交为覆盖（upsert）语义；
 * color 为当日颜色的快照，防止日后调整端上词典颜色改写历史观感。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("mood_record")
public class MoodRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** users.id */
    private Long userId;

    /** 记录日期（自然日），与 user_id 组成 uk_user_date 一天一条 */
    private LocalDate moodDate;

    /** 1暴雨 2阴 3多云 4晴 5大晴（DB CHECK 1..5 兜底） */
    private Integer moodLevel;

    /** 快照当日颜色(#RRGGBB/#RRGGBBAA)，可空 */
    private String color;

    /** 一个词（累/顺/静/燥/燃等），可空 */
    private String word;

    /** 一句话备注，可空 */
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
