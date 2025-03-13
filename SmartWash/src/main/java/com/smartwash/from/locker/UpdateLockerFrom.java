package com.smartwash.from.locker;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateLockerFrom {
    @NotNull(message = "寄存柜不能为空")
    private Long lockerId;

    private String status;
}
