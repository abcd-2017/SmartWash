package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Orders;
import com.smartwash.from.order.OrderListFrom;
import com.smartwash.from.order.ReservationOrderFrom;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.utils.LoginUser;
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

    List<ShowOrderVo> getOrderList(OrderListFrom orderListFrom);
}
