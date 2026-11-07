package com.smartwash.subscription.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 管理端订阅记录分页筛选 DTO（GET /admin/subscription/list）。
 * 日期区间按下次续费日（到期时间）筛选。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AdminSearchSubscriptionFrom extends BaseSearchFrom {

    /** 按状态筛选（active/paused/canceled/finished，不传查全部） */
    private String status;

    /** 到期时间起始（含），yyyy-MM-dd，可空 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    /** 到期时间截止（含），yyyy-MM-dd，可空 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
