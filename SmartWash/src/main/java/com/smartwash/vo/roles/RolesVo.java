package com.smartwash.vo.roles;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RolesVo {
    private Integer roleId;

    private String roleName;

    private String description;

    private LocalDateTime createdAt;
}
