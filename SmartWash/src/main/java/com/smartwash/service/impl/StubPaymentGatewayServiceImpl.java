package com.smartwash.service.impl;

import com.smartwash.service.PaymentGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 支付网关桩实现（开发环境）
 * 生产环境请替换为真实的支付 SDK 实现（如微信支付、支付宝支付）。
 *
 * <p>条件装配围栏：仅当 smartwash.stub.payment-enabled=true 或未配置该属性（demo 开箱即跑）时装配；
 * application-prod.yaml 中显式置为 false——生产若未提供真实 PaymentGatewayService 实现，
 * 容器缺少该 Bean 将启动失败，防止带桩上线（对应评审报告后端 #31）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "smartwash.stub.payment-enabled", havingValue = "true", matchIfMissing = true)
public class StubPaymentGatewayServiceImpl implements PaymentGatewayService {

    @Override
    public String createPayment(String orderNo, BigDecimal amount, String description, String payType) {
        log.info("[桩实现] 创建支付订单, orderNo: {}, amount: {}, payType: {}", orderNo, amount, payType);
        // 开发环境：直接返回成功标识
        // 生产环境：替换为真实支付 SDK 调用
        return "STUB_PREPAY_" + orderNo;
    }

    @Override
    public String createPayment(String outTradeNo, BigDecimal amount, String description, String payType, PaymentResultCallback callback) {
        log.info("[桩实现] 创建支付订单(两段式), outTradeNo: {}, amount: {}, payType: {}", outTradeNo, amount, payType);
        // 开发环境：模拟网关下单成功后，在同一调用栈内同步回调“支付成功”
        // 生产环境：替换为真实支付 SDK——预支付信息先返回，支付结果经异步通知验签后再驱动回调，
        //           回调处理须在独立事务中执行（见 PaymentsServiceImpl/RechargeRecordsServiceImpl 的回调方法注释）
        if (callback != null) {
            callback.onPaymentSuccess(outTradeNo);
        }
        return "STUB_PREPAY_" + outTradeNo;
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
