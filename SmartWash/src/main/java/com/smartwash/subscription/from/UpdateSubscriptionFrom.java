package com.smartwash.subscription.from;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 编辑订阅请求 DTO。
 * 全字段可选，null = 不修改；canceled/finished 终态订阅拒绝编辑。
 * 说明：受 mybatis-plus update-strategy 约束，清空可选字段请置为对应空语义由前端约定（v1 不支持置空清除）。
 */
@Data
public class UpdateSubscriptionFrom {

    /** 订阅名称 */
    @Size(max = 100, message = "订阅名称过长（上限100字符）")
    private String name;

    /** 每期金额 */
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    @Digits(integer = 8, fraction = 2, message = "金额格式非法（整数8位小数2位）")
    private BigDecimal amount;

    /** 计费周期 month/year */
    @Pattern(regexp = "^(month|year)$", message = "计费周期仅支持 month/year")
    private String cycleType;

    /** 间隔期数 */
    @Min(value = 1, message = "间隔期数最小为1")
    @Max(value = 36, message = "间隔期数最大为36")
    private Integer cycleInterval;

    /** 下次续费日 */
    private LocalDate nextRenewDate;

    /** 起始锚点日 */
    private LocalDate firstAnchorDate;

    /** 试用截止日 */
    private LocalDate trialEndDate;

    /** 自动续费 0否 1是 */
    @Min(value = 0, message = "自动续费参数非法")
    @Max(value = 1, message = "自动续费参数非法")
    private Integer autoRenew;

    /** 续费/试用截止前提醒天数 */
    @Min(value = 0, message = "提醒天数最小为0")
    @Max(value = 60, message = "提醒天数最大为60")
    private Integer remindDaysBefore;

    /** 取消入口网址 */
    @Size(max = 500, message = "取消网址过长（上限500字符）")
    private String cancelUrl;

    /** 取消路径步骤备忘 */
    @Size(max = 1000, message = "取消步骤过长（上限1000字符）")
    private String cancelSteps;

    /** 备注 */
    @Size(max = 500, message = "备注过长（上限500字符）")
    private String note;
}
