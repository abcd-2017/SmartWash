package com.smartwash.vo.admin_users;

import com.smartwash.entity.Roles;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserVo {
    private Long adminId;

    private String username;

    private Roles roles;

    private LocalDateTime createdAt;
}
