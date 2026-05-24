package com.smartwash.from.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordFrom {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^(\\+86)?1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phoneNumber;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码必须为6位")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 16, message = "密码长度应为6-16位")
    private String newPassword;
}
