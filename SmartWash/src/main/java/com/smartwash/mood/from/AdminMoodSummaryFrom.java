package com.smartwash.mood.from;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 管理端情绪聚合报表查询参数（GET /admin/mood/summary）。
 * dateFrom/dateTo 均可空：默认近 30 天（含今日）。
 */
@Data
public class AdminMoodSummaryFrom {

    /** 起始日期（含），yyyy-MM-dd，可空 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    /** 结束日期（含），yyyy-MM-dd，可空 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
