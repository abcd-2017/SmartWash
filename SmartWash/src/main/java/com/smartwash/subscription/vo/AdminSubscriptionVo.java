package com.smartwash.subscription.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端订阅记录视图对象（GET /admin/subscription/list）。
 * 只读下发：手机号脱敏（遵循仓库既有做法 DesensitizedUtil.mobilePhone），
 * 不下发取消网址/步骤备忘/备注等用户私有文本。
 */
@Data
@Accessors(chain = true)
public class AdminSubscriptionVo {

    private Long subscriptionId;

    private Long userId;

    /** 用户手机号（脱敏，如 138****5678） */
    private String userPhone;

    /** 订阅名称 */
    private String name;

    /** 每期金额 */
    private BigDecimal amount;

    /** 计费周期 month/year */
    private String cycleType;

    /** 间隔期数（如每2月=2） */
    private Integer cycleInterval;

    /** 下次续费日（到期时间） */
    private LocalDate nextRenewDate;

    /** 试用截止日（NULL=非试用） */
    private LocalDate trialEndDate;

    /** 自动续费 0否 1是 */
    private Integer autoRenew;

    /** active生效中/paused已暂停/canceled已取消/finished已结束 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
