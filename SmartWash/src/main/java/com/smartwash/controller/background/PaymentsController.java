package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.PayType;
import com.smartwash.common.PaymentStatus;
import com.smartwash.common.Result;
import com.smartwash.from.payment.AddPaymentFrom;
import com.smartwash.from.payment.SearchPaymentFrom;
import com.smartwash.from.payment.UpdatePaymentFrom;
import com.smartwash.service.IPaymentsService;
import com.smartwash.vo.payment.PaymentVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
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
@RequestMapping("/admin/payments")
public class PaymentsController {
    @Autowired
    private IPaymentsService paymentsService;

    //获取支付类型
    @GetMapping("/payType")
    public Result<Map<String, String>> getPayType() {
        PayType[] values = PayType.values();
        Map<String, String> map = new HashMap<>();
        for (PayType status : values) {
            map.put(status.getType(), status.getDescription());
        }
        return Result.ok(map);
    }

    //获取支付状态
    @GetMapping("/payStatus")
    public Result<Map<String, String>> getPayStatus() {
        PaymentStatus[] values = PaymentStatus.values();
        Map<String, String> map = new HashMap<>();
        for (PaymentStatus status : values) {
            map.put(status.getStatus(), status.getDescription());
        }
        return Result.ok(map);
    }

    //获取所有付款记录
    @GetMapping("/all")
    public Result<Page<PaymentVo>> getAllPayments(SearchPaymentFrom paymentFrom) {
        String phoneNumber = paymentFrom.getPhoneNumber();
        if (StringUtils.hasText(phoneNumber)) {
            if (phoneNumber.length() > 11) return Result.failMsg("手机号长度错误");
            if (!phoneNumber.matches("^(\\+86)?1[3-9]\\d{9}$")) return Result.failMsg("手机号格式错误");
        }
        return Result.ok(paymentsService.getAllPayments(paymentFrom));
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deletePayments(@PathVariable("ids") String ids) {
        return Result.ok(paymentsService.deletePayments(ids));
    }

    //添加付款记录
    @PostMapping("/add")
    public Result<String> addPayment(@RequestBody @Valid AddPaymentFrom paymentsFrom) {
//        if (paymentsService.getPaymentByName(PaymentFrom.getUsername()) != null) {
//            return Result.fail("给付款记录名已被使用");
//        }
        paymentsService.addPayment(paymentsFrom);
        return Result.ok("添加成功");
    }

    //修改付款记录
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdatePaymentFrom paymentsFrom) {
//        Payments user = paymentsService.getById(PaymentsFrom.getAdminId());
//        if (!Objects.equals(user.getUsername(), PaymentsFrom.getUsername()) && PaymentsService.getPaymentByName(PaymentsFrom.getUsername()) != null) {
//            return Result.fail("给付款记录名已被使用");
//        }
        paymentsService.updatePayment(paymentsFrom);
        return Result.ok("修改成功");
    }


}
