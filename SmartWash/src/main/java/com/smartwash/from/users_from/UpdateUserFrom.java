package com.smartwash.from.users_from;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateUserFrom {
    @NotNull(message = "请选择学生")
    private Long userId;

    private Long schoolId;

    private String phoneNumber;

    private String studentId;

    private String campusCard;

    private String password;

    private BigDecimal balance;
}
