package com.smartwash.from.locker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 批量新增寄存柜表单。
 *
 * <p>编号策略：从 startNumber 开始，自动跳过该学校已占用的柜号，
 * 取满 count 个可用编号；同校编号唯一由 uk_school_locker 兜底。</p>
 */
@Data
public class BatchAddLockerFrom {

    @NotNull(message = "请选择学校")
    private Long schoolId;

    @NotNull(message = "起始柜号不能为空")
    @Min(value = 1, message = "起始柜号必须大于0")
    private Integer startNumber;

    @NotNull(message = "新增数量不能为空")
    @Min(value = 1, message = "新增数量必须大于0")
    @Max(value = 1000, message = "单次最多新增1000个寄存柜")
    private Integer count;
}
