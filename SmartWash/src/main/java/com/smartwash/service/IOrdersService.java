package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.entity.Orders;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.vo.locker.LockersVo;
import com.smartwash.vo.order.OrdersVo;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 
 * @since 2025-03-06
 */
public interface IOrdersService extends IService<Orders> {

    Page<OrdersVo> getAllOrders(SearchOrderFrom searchOrderFrom);

    Boolean deleteOrders(String ids);
}
