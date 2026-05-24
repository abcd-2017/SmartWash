package com.smartwash.service;

import java.math.BigDecimal;

/**
 * 支付网关服务接口
 * 集成真实支付 SDK 时实现此接口（如微信支付、支付宝支付）
 */
public interface PaymentGatewayService {

    /**
     * 创建支付订单
     *
     * @param orderNo     商户订单号
     * @param amount      支付金额
     * @param description 订单描述
     * @param payType     支付类型（wechat/alipay）
     * @return 支付平台返回的预支付信息
     */
    String createPayment(String orderNo, BigDecimal amount, String description, String payType);

    /**
     * 查询支付状态
     *
     * @param orderNo 商户订单号
     * @return 支付状态（SUCCESS/FAIL/PENDING）
     */
    String queryPaymentStatus(String orderNo);

    /**
     * 关闭支付订单
     *
     * @param orderNo 商户订单号
     * @return 是否关闭成功
     */
    boolean closePayment(String orderNo);
}
