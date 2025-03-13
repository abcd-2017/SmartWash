package com.smartwash.from.roles;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRolesFrom {
    @NotNull(message = "请选择角色")
    private Integer roleId;

    private String roleName;

    private String description;

}
