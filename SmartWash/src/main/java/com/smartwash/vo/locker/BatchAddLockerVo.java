package com.smartwash.vo.locker;

import lombok.Data;

/**
 * 批量新增寄存柜结果：回传实际生成的编号范围，便于前端提示。
 */
@Data
public class BatchAddLockerVo {

    private Integer addedCount;

    private Integer firstNumber;

    private Integer lastNumber;
}
