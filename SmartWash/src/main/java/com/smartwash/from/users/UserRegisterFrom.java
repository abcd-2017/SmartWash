package com.smartwash.from.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterFrom {
    @NotNull(message = "手机号码不能为空")
    @Pattern(regexp = "^(\\+86)?1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phoneNumber;

    @NotBlank(message = "密码不能为空")
    @Size(message = "密码长度在6-16位", min = 6, max = 16)
    private String password;

    @NotBlank(message = "验证码不能为空")
    @Size(message = "验证码长度只能为6位", min = 6, max = 6)
    private String code;
}
