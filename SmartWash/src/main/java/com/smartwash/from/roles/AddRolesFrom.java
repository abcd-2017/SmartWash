package com.smartwash.from.roles;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddRolesFrom {
    @NotBlank(message = "角色名字不能为空")
    private String roleName;

    private String description;
}
