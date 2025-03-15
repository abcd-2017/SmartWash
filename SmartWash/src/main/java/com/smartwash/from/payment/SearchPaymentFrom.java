package com.smartwash.from.payment;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class SearchPaymentFrom extends BaseSearchFrom {
    private Long paymentId;

    private String orderNo;

    private String phoneNumber;

    private String paymentMethod;

    private String status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
