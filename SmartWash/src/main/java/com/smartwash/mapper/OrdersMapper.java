package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.entity.Orders;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.order.ShowOrderVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface OrdersMapper extends BaseMapper<Orders> {

    @Select("SELECT * FROM orders WHERE order_id = #{orderId} FOR UPDATE")
    Orders selectByIdForUpdate(@Param("orderId") Long orderId);

    Orders getOrderByOrderNo(String orderNo);

    Page<OrdersVo> searchOrders(Page<OrdersVo> page, @Param("searchForm") SearchOrderFrom searchForm);

    OrdersVo getOrderByOrderId(Long orderId);

    Page<ShowOrderVo> getOrderList(Page<ShowOrderVo> page, String status, Long userId);

    /**
     * 条件更新订单状态（取件/寄件完成等用户侧流转的并发闸门）：仅当当前状态等于 expectStatus 时流转，
     * 同时清空 pickup_code 与 locker_id。禁止无状态条件的覆写——否则已退款订单可被并发取件覆写为已完成。
     *
     * @param orderId      订单 ID
     * @param expectStatus 期望的当前状态（以读取时快照传入，SQL 层再次校验）
     * @param status       目标状态
     * @return 影响行数：1=流转成功；0=状态已被并发变更（如已退款/已取消），调用方必须拒绝
     */
    int nextStatus(@Param("orderId") Long orderId, @Param("expectStatus") String expectStatus, @Param("status") String status);

    /**
     * CAS 条件更新订单状态：仅当当前状态等于 expectStatus 时流转到 targetStatus，并顺带清空 locker_id
     * （避免取消/退款后 orders.locker_id 悬挂引用）。全项目取消/超时/退款场景统一走本方法作为并发闸门。
     *
     * @param orderId      订单 ID
     * @param expectStatus 期望的当前状态（如待支付）
     * @param targetStatus 目标状态（如已取消/已退款）
     * @return 影响行数：1=流转成功；0=当前状态不符，说明订单已被支付/取消等并发操作变更
     */
    int casStatus(@Param("orderId") Long orderId, @Param("expectStatus") String expectStatus, @Param("targetStatus") String targetStatus);
}
