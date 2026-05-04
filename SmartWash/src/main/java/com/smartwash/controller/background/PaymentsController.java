package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.PayType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "支付记录管理", description = "后台支付记录的增删改查及枚举查询")
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

    @Operation(summary = "批量删除支付记录", description = "根据ID批量删除支付记录，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deletePayments(@PathVariable("ids") @Parameter(description = "支付记录ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        return Result.ok(paymentsService.deletePayments(ids));
    }

    @Operation(summary = "新增支付记录", description = "手动新增一条支付记录")
    @PostMapping("/add")
    public Result<String> addPayment(@RequestBody @Valid AddPaymentFrom paymentsFrom) {
//        if (paymentsService.getPaymentByName(PaymentFrom.getUsername()) != null) {
//            return Result.fail("给付款记录名已被使用");
//        }
        paymentsService.addPayment(paymentsFrom);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新支付记录", description = "修改支付记录信息")
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
