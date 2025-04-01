package com.smartwash.from.order;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

@Data
public class OrderListFrom extends BaseSearchFrom {
    private String status;
    private Long lastOrderId;
}
