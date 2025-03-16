package com.smartwash.from.admin_users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserLoginFrom {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(message = "密码必须为6-16位", min = 6, max = 16)
    private String password;
}
