package com.smartwash.subscription.from;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建订阅请求 DTO。
 */
@Data
public class AddSubscriptionFrom {

    /** 订阅名称 */
    @NotBlank(message = "订阅名称不能为空")
    @Size(max = 100, message = "订阅名称过长（上限100字符）")
    private String name;

    /** 每期金额 */
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    @Digits(integer = 8, fraction = 2, message = "金额格式非法（整数8位小数2位）")
    private BigDecimal amount;

    /** 计费周期 month/year */
    @NotBlank(message = "计费周期不能为空")
    @Pattern(regexp = "^(month|year)$", message = "计费周期仅支持 month/year")
    private String cycleType;

    /** 间隔期数（如每2月=2），不传默认1 */
    @Min(value = 1, message = "间隔期数最小为1")
    @Max(value = 36, message = "间隔期数最大为36")
    private Integer cycleInterval = 1;

    /** 下次续费日（必填，定时推进的起点） */
    @NotNull(message = "下次续费日不能为空")
    private LocalDate nextRenewDate;

    /** 起始锚点日（可选，月末策略锚，v1 仅保留） */
    private LocalDate firstAnchorDate;

    /** 试用截止日（可选，NULL=非试用） */
    private LocalDate trialEndDate;

    /** 自动续费 0否 1是，不传默认1 */
    @Min(value = 0, message = "自动续费参数非法")
    @Max(value = 1, message = "自动续费参数非法")
    private Integer autoRenew = 1;

    /** 续费/试用截止前提醒天数，不传默认3 */
    @Min(value = 0, message = "提醒天数最小为0")
    @Max(value = 60, message = "提醒天数最大为60")
    private Integer remindDaysBefore = 3;

    /** 取消入口网址（可选，须为 http/https 地址） */
    @Size(max = 500, message = "取消网址过长（上限500字符）")
    private String cancelUrl;

    /** 取消路径步骤备忘（可选） */
    @Size(max = 1000, message = "取消步骤过长（上限1000字符）")
    private String cancelSteps;

    /** 备注（可选） */
    @Size(max = 500, message = "备注过长（上限500字符）")
    private String note;
}
