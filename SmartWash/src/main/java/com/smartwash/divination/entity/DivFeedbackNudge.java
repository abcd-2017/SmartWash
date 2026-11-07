package com.smartwash.divination.entity;

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
 * 应验回访提醒（占卜闭环）。
 * 解读完成 ≥3 天且该解读无反馈时，由定时任务生成 status=0 的待回访记录；
 * 用户提交反馈即视为已回访（status=1），端上也可主动忽略（status=2）。
 * uk_interpretation 保证一个解读最多一条提醒，天然防重复。
 * 注意：本项目无 @TableLogic 逻辑删除，is_delete 由查询手动过滤。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_feedback_nudge")
public class DivFeedbackNudge implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long recordId;

    private Long interpretationId;

    /** 回访日期 = 解读完成日 + 3 天 */
    private LocalDate nudgeDate;

    /** 0待回访/1已回访/2已忽略 */
    private Integer status;

    /** 0正常/1删除 */
    private Integer isDelete;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
