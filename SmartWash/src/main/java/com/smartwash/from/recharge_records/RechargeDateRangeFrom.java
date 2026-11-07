package com.smartwash.from.recharge_records;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 充值记录汇总/导出日期范围参数（均可空 = 不限）。
 * 日期格式与订单导出参数（SearchOrderFrom startTime/endTime）保持一致。
 */
@Data
public class RechargeDateRangeFrom {

    /** 起始时间（含），格式 yyyy-MM-dd HH:mm:ss，可空 = 不限 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dateFrom;

    /** 结束时间（含），格式 yyyy-MM-dd HH:mm:ss，可空 = 不限 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dateTo;
}
