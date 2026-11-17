package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.PayType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.common.PaymentStatus;
import com.smartwash.common.Result;
import com.smartwash.from.payment.SearchPaymentFrom;
import com.smartwash.service.IPaymentsService;
import com.smartwash.vo.payment.PaymentVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "支付记录管理", description = "后台支付记录查询及枚举（资金流水禁止手工新增/修改）")
@Slf4j
@RestController
@RequestMapping("/admin/payments")
public class PaymentsController {
    @Autowired
    private IPaymentsService paymentsService;

    @Operation(summary = "获取支付类型枚举", description = "获取所有支付类型的枚举值及描述")
    @GetMapping("/payType")
    public Result<Map<String, String>> getPayType() {
        PayType[] values = PayType.values();
        Map<String, String> map = new HashMap<>();
        for (PayType status : values) {
            map.put(status.getType(), status.getDescription());
        }
        return Result.ok(map);
    }

    @Operation(summary = "获取支付状态枚举", description = "获取所有支付状态的枚举值及描述")
    @GetMapping("/payStatus")
    public Result<Map<String, String>> getPayStatus() {
        PaymentStatus[] values = PaymentStatus.values();
        Map<String, String> map = new HashMap<>();
        for (PaymentStatus status : values) {
            map.put(status.getStatus(), status.getDescription());
        }
        return Result.ok(map);
    }

    @Operation(summary = "分页查询支付记录列表", description = "根据条件分页查询支付记录列表")
    @GetMapping("/all")
    public Result<Page<PaymentVo>> getAllPayments(SearchPaymentFrom paymentFrom) {
        String phoneNumber = paymentFrom.getPhoneNumber();
        if (StringUtils.hasText(phoneNumber)) {
            if (phoneNumber.length() > 11) return Result.failMsg("手机号长度错误");
            if (!phoneNumber.matches("^(\\+86)?1[3-9]\\d{9}$")) return Result.failMsg("手机号格式错误");
        }
        return Result.ok(paymentsService.getAllPayments(paymentFrom));
    }

    // 手工新增/修改支付流水入口已下线（资金流水只允许由支付网关链路产生，禁止手工写库）；
    // 服务层 addPayment/updatePayment 暂保留供内部补偿流程可能复用，不再暴露 HTTP 映射
}
