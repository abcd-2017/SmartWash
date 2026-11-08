package com.smartwash.points.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 积分流水条目（/me 最近流水列表元素）。
 */
@Data
public class PointLedgerItemVo {

    /** 积分变动（正=入账） */
    private Integer delta;

    /** 业务原因（枚举字符串：SIGN_IN / STREAK_BONUS，见 PointReason） */
    private String reason;

    /** 业务原因中文描述（双端直接展示用） */
    private String reasonDesc;

    /** 业务日期维度（签到类=签到日） */
    private LocalDate refDate;

    /** 本笔变动后余额快照 */
    private Integer balanceAfter;

    private LocalDateTime createdAt;
}
