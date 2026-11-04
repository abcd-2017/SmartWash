package com.smartwash.vo.locker;

import lombok.Data;

/**
 * 批量新增寄存柜结果：回传实际生成的编号范围，便于前端提示。
 */
@Data
public class BatchAddLockerVo {

    /** 实际新增数量 */
    private Integer addedCount;

    /** 实际生成的最小柜号 */
    private Integer firstNumber;

    /** 实际生成的最大柜号 */
    private Integer lastNumber;
}
