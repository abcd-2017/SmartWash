package com.smartwash.vo.users;

import com.smartwash.entity.Schools;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserVo {
    private Long userId;

    private Schools schools;

    private String phoneNumber;

    private String studentId;

    private String campusCard;

    private BigDecimal balance;

    private String avatar;

    private LocalDateTime createdAt;
}

