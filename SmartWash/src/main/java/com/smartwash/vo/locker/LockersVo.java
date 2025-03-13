package com.smartwash.vo.locker;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LockersVo {
    private Long lockerId;

    private Long schoolId;

    private Integer lockerNumber;

    private String status;

    private LocalDateTime lastUsedAt;
}
