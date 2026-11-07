package com.smartwash.divination.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应验回访提醒视图（/web/auth/div/nudges）。
 */
@Data
public class DivNudgeVo {

    /** 提醒 ID */
    private Long id;

    /** 卦例 ID */
    private Long recordId;

    /** liuyao/meihua/qimen/liuren */
    private String method;

    /** general/career/wealth/... */
    private String category;

    /** 卦例问题 */
    private String question;

    /** 起卦时刻 */
    private LocalDateTime castAt;

    /** 回访日期（解读完成日 + 3 天） */
    private LocalDate nudgeDate;
}
