package com.smartwash.vo.schools;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchoolsVo {
    private Long schoolId;

    private String schoolName;

    private String location;

    private Integer lockerCount;

    private LocalDateTime createdAt;

}
