package com.smartwash.common.audit;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 审计日志分页筛选入参（GET /admin/audit-logs）。
 * 时间格式与 SearchPaymentFrom 同口径：yyyy-MM-dd HH:mm:ss。
 */
@Data
public class SearchAuditLogFrom extends com.smartwash.from.BaseSearchFrom {

    /** 操作人管理员用户名（精确匹配） */
    private String adminUser;

    /** 动作标识（精确匹配，如 LAUNDRY_ITEM_UPDATE） */
    private String action;

    /** 目标对象类型（精确匹配，如 laundry_item） */
    private String targetType;

    /** 目标对象 id（精确匹配，批量操作为逗号分隔串） */
    private String targetId;

    /** 操作时间区间-起（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 操作时间区间-止（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
