package com.smartwash.vo.locker;

import lombok.Data;

@Data
public class LockerStatusSummaryVo {

    private Long schoolId;

    private String schoolName;

    private int totalCount;

    private int freeCount;

    private int usedCount;

    private int faultCount;
}
