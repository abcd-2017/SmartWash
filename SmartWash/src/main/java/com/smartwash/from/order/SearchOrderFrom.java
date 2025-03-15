package com.smartwash.from.order;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SearchOrderFrom extends BaseSearchFrom {
    private String phoneNumber;

    private String schoolName;

    private String orderNo;

    private Long laundryItemsId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

}
