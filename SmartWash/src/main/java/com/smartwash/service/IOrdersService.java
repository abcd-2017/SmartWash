package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Orders;
import com.smartwash.from.order.*;
import com.smartwash.utils.LoginUser;
import com.smartwash.vo.order.OrderItemCountVo;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.order.ShowOrderVo;

import java.util.List;

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
}
