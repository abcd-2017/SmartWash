package com.smartwash.from.locker;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class SearchLockersFrom extends BaseSearchFrom {
    private Long lockerId;

    private Long schoolId;

    private Integer lockerNumber;

    private String status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
