package com.smartwash.vo.payment;

import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.users.UserVo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentVo {
    private Long paymentId;

    private OrdersVo order;

    private UserVo user;

    private BigDecimal amount;

    private String paymentMethod;

    private String status;

    private LocalDateTime paidAt;
}
