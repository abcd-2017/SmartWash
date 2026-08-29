package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Orders;
import com.smartwash.from.order.*;
import com.smartwash.utils.LoginUser;
import com.smartwash.vo.order.OrderGroupVo;
import com.smartwash.vo.order.OrderItemCountVo;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.order.ShowOrderVo;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface IOrdersService extends IService<Orders> {

    Page<OrdersVo> getAllOrders(SearchOrderFrom searchOrderFrom);

    Boolean deleteOrders(String ids);

    Long createOrder(ReservationOrderFrom reservationOrderFrom, LoginUser loginUser);

    OrdersVo getOrderByOrderId(Long orderId);

    List<ShowOrderVo> getOrderList(OrderListFrom orderListFrom, LoginUser loginUser);

    //获取各状态订单数量
    OrderItemCountVo getOrderItemCount(OrderItemCountFrom itemCountFrom, Long userId);

    Boolean updateOrderStatus(UpdateOrderStatus orderStatus);

    Boolean pickupOrder(OrderNextStatusFrom statusFrom, LoginUser loginUser);

    Boolean shippingOrder(OrderNextStatusFrom statusFrom, LoginUser loginUser);

    List<Orders> getWashingOrder(LoginUser loginUser, int size);

    List<ShowOrderVo> getWashingOrderShowVo(LoginUser loginUser, int size);

    Boolean cancelOrder(Long orderId, Long userId);

    OrdersVo calculationOrder(Long userId, Long orderId, Long userCouponId);

    Map<String, OrderGroupVo> getOrderSummary(LoginUser loginUser, int size);

    /**
     * 取件二维码归属校验：校验当前用户确有匹配该取件码片段的订单（评审报告后端 #42）
     *
     * @param userId   当前登录用户 ID
     * @param pickCode 取件码随机段（客户端从完整取件码拆出的末段）
     * @return true=该用户名下存在匹配订单，允许生成二维码
     */
    boolean ownsPickupCode(Long userId, String pickCode);
}
