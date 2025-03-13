package com.smartwash.from.admin_users;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAdminUserFrom {
    @NotNull(message = "请选择用户")
    private Long adminId;

    private String username;

    private String passwordHash;

    private Long roleId;
}
