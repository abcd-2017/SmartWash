package com.smartwash.subscription.from;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 使用打卡请求 DTO。
 */
@Data
public class AddUsageFrom {

    /** 使用日期，不传默认今天；不允许晚于今天 */
    private LocalDate usageDate;

    /** 使用备注 */
    @Size(max = 200, message = "使用备注过长（上限200字符）")
    private String note;
}
