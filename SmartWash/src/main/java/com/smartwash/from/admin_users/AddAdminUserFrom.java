package com.smartwash.from.admin_users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AddAdminUserFrom {
    @NotBlank(message = "用户名不能为空")
    private String username;

    private String passwordHash;

    @NotNull(message = "请选择角色")
    private Long roleId;

    private LocalDateTime createdAt;
}
