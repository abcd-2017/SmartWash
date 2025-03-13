package com.smartwash.from.locker;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddLockerFrom {
    @NotNull(message = "请选择学校")
    private Long schoolId;

    @NotNull(message = "寄存柜编号不能为空")
    private Integer lockerNumber;

    private String status;
}
