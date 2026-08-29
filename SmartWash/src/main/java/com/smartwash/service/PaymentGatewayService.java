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
     * 创建支付订单并注册支付结果回调（两段式：处理中 → 回调完成）
     * 桩实现在下单成功后于同一调用栈内同步回调“支付成功”；
     * 真实网关实现应在收到异步支付通知（须验签）后再驱动回调。
     *
     * @param outTradeNo  网关统一订单号（幂等键，与 payments/recharge_records.out_trade_no 对应）
     * @param amount      支付金额
     * @param description 订单描述
     * @param payType     支付类型
     * @param callback    支付成功回调
     * @return 支付平台返回的预支付信息
     */
    String createPayment(String outTradeNo, BigDecimal amount, String description, String payType, PaymentResultCallback callback);

    /**
     * 支付结果回调（由网关实现驱动“回调完成”阶段）
     */
    @FunctionalInterface
    interface PaymentResultCallback {

        /**
         * 支付成功回调
         *
         * @param outTradeNo 网关统一订单号（幂等键）
         */
        void onPaymentSuccess(String outTradeNo);
    }

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
