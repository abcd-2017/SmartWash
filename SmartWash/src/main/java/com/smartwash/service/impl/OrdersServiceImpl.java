package com.smartwash.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.LockerStatusEnum;
import com.smartwash.common.OrderStatus;
import com.smartwash.entity.Lockers;
import com.smartwash.entity.Orders;
import com.smartwash.entity.Users;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.order.OrderListFrom;
import com.smartwash.from.order.ReservationOrderFrom;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.mapper.LockersMapper;
import com.smartwash.mapper.OrdersMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.IOrdersService;
import com.smartwash.utils.LoginUser;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.order.ShowOrderVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersService {
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private UsersMapper usersMapper;
    @Autowired
    private LockersMapper lockersMapper;

    @Override
    public Page<OrdersVo> getAllOrders(SearchOrderFrom searchOrderFrom) {
        Page<OrdersVo> page = new Page<>(searchOrderFrom.getPage(), searchOrderFrom.getSize());
        return ordersMapper.searchOrders(page, searchOrderFrom);
    }

    @Override
    public Boolean deleteOrders(String ids) {
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }

    @Override
    public Long createOrder(ReservationOrderFrom reservationOrderFrom, LoginUser loginUser) {
        Users user = usersMapper.selectById(loginUser.getUserId());
        List<Lockers> lockers = lockersMapper.getLockersBySchoolId(user.getSchoolId());
        Orders orders = new Orders();
        for (Lockers locker : lockers) {
            if (locker.getStatus().equals(LockerStatusEnum.FREE.getValue())) {
                locker.setStatus(LockerStatusEnum.USE.getValue());
                lockersMapper.updateById(locker);
                orders.setLockerId(locker.getLockerId());
                break;
            }
        }
        if (orders.getLockerId() == null) {
            throw new CustomExceptions("当前寄存柜已满，请稍后再试！");
        }

        orders.setUserId(user.getUserId());
        orders.setSchoolId(user.getSchoolId());
        //设置套餐，后续总价格可以叠加优惠券
        orders.setLaundryItemsId(reservationOrderFrom.getItemsId());
        orders.setTotalPrice(BigDecimal.valueOf(reservationOrderFrom.getTotalPrice()));

        //设置订单状态
        orders.setStatus(OrderStatus.PENDING_PAYMENT.getStatus());

        //唯一订单号
        Snowflake snowflake = IdUtil.getSnowflake();
        orders.setOrderNo(snowflake.nextIdStr());
        save(orders);
        return orders.getOrderId();
    }

    @Override
    public OrdersVo getOrderByOrderId(Long orderId) {
        return ordersMapper.getOrderByOrderId(orderId);
    }

    @Override
    public List<ShowOrderVo> getOrderList(OrderListFrom orderListFrom) {
        return List.of();
    }
}
