package com.smartwash.points.entity;

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
 * 积分流水，对应表 point_ledger。
 * append-only 事实源：只允许 insert，禁删禁改（不提供任何删除/更新入口）。
 * balance_after 为本笔变动后的余额快照，用于流水连续性对账。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("point_ledger")
public class PointLedger implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ledger_id", type = IdType.AUTO)
    private Long ledgerId;

    private Long userId;

    /** 积分变动（正=入账 负=出账） */
    private Integer delta;

    /** 业务原因（枚举字符串，见 PointReason） */
    private String reason;

    /** 业务日期维度（签到类=签到日，业务防重对账依据） */
    private LocalDate refDate;

    /** 本笔变动后余额快照 */
    private Integer balanceAfter;

    private LocalDateTime createdAt;
}
