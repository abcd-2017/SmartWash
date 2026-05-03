package com.smartwash.controller.web;


import com.smartwash.common.PayType;
import com.smartwash.common.PaymentStatus;
import com.smartwash.common.Result;
import com.smartwash.from.payment.PaymentOrderFrom;
import com.smartwash.service.IPaymentsService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Slf4j
@RestController
@RequestMapping("/web")
public class WebPaymentsController {
    @Autowired
    private IPaymentsService paymentsService;

    //获取支付类型
    @GetMapping("/payments/payType")
    public Result<Map<String, String>> getPayType() {
        PayType[] values = PayType.values();
        Map<String, String> map = new HashMap<>();
        for (PayType status : values) {
            map.put(status.getType(), status.getDescription());
        }
        return Result.ok(map);
    }

    //获取支付状态
    @GetMapping("/payments/payStatus")
    public Result<Map<String, String>> getPayStatus() {
        PaymentStatus[] values = PaymentStatus.values();
        Map<String, String> map = new HashMap<>();
        for (PaymentStatus status : values) {
            map.put(status.getStatus(), status.getDescription());
        }
        return Result.ok(map);
    }

    @PostMapping("/auth/payments/payment")
    public Result<String> authPayment(@RequestBody @Valid PaymentOrderFrom orderFrom) {
        LoginUser user = UserContextHolder.getUser();
        if (paymentsService.paymentOrder(user, orderFrom)) {
            return Result.ok("支付成功");
        } else {
            return Result.failMsg("支付失败");
        }
    }
}
