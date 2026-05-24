package com.smartwash.service.impl;

import com.smartwash.service.PaymentGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 支付网关桩实现（开发环境）
 * 生产环境请替换为真实的支付 SDK 实现（如微信支付、支付宝支付）
 */
@Slf4j
@Service
public class StubPaymentGatewayServiceImpl implements PaymentGatewayService {

    @Override
    public String createPayment(String orderNo, BigDecimal amount, String description, String payType) {
        log.info("[桩实现] 创建支付订单, orderNo: {}, amount: {}, payType: {}", orderNo, amount, payType);
        // 开发环境：直接返回成功标识
        // 生产环境：替换为真实支付 SDK 调用
        return "STUB_PREPAY_" + orderNo;
    }

    @Override
    public String queryPaymentStatus(String orderNo) {
        log.info("[桩实现] 查询支付状态, orderNo: {}", orderNo);
        return "SUCCESS";
    }

    @Override
    public boolean closePayment(String orderNo) {
        log.info("[桩实现] 关闭支付订单, orderNo: {}", orderNo);
        return true;
    }
}
