package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.entity.Orders;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.order.ShowOrderVo;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface OrdersMapper extends BaseMapper<Orders> {

    Orders getOrderByOrderNo(String orderNo);

    Page<OrdersVo> searchOrders(Page<OrdersVo> page, @Param("searchForm") SearchOrderFrom searchForm);

    OrdersVo getOrderByOrderId(Long orderId);

    void updateOrderStatus(Long orderId, String status);

    Page<ShowOrderVo> getOrderList(Page<ShowOrderVo> page, String status, Long userId);
}
