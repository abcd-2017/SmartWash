package com.smartwash.from.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录态修改密码请求体（用户端校验原密码后换新密码）
 * 新密码长度口径与注册/重置密码一致（6-16 位）
 */
@Data
public class ChangePasswordFrom {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 16, message = "密码长度应为6-16位")
    private String newPassword;
}
