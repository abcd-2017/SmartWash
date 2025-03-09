package com.smartwash.from.users_from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddUserFrom {
    @NotNull(message = "学校不能为空")
    private Long schoolId;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^(\\+86)?1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phoneNumber;

    @NotBlank(message = "学号不能为空")
    private String studentId;

    private String password;

    private String campusCard;

    private BigDecimal balance;
}
