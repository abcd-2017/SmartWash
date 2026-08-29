package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.entity.Orders;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.vo.order.OrderStatusCountVo;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.order.ShowOrderVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * 普通流转的条件更新（管理员非取消/非完成流转的并发闸门）：仅当当前状态等于 expectStatus 时流转。
     * 与 casStatus 的区别：只改 status、不改 locker_id——待支付→待寄件等在途流转柜子仍被订单持有，
     * 清空 locker_id 会造成在途订单柜子悬挂泄漏（柜子行仍为占用态却无订单引用）
     *
     * @param orderId      订单 ID
     * @param expectStatus 期望的当前状态（以读取时快照传入，SQL 层再次校验）
     * @param targetStatus 目标状态
     * @return 影响行数：1=流转成功；0=状态已被并发变更，调用方必须拒绝
     */
    int casStatusKeepLocker(@Param("orderId") Long orderId, @Param("expectStatus") String expectStatus, @Param("targetStatus") String targetStatus);

    /**
     * 流转到待取件的条件更新（并发闸门）：状态 + 新分配柜子 + 取件码一次条件写入，
     * 避免先 updateById 状态再补写柜子/取件码的多步 check-then-act
     *
     * @return 影响行数：1=流转成功；0=状态已被并发变更（如已取消/已退款），调用方必须拒绝
     */
    int casStatusAssignPickup(@Param("orderId") Long orderId, @Param("expectStatus") String expectStatus,
                              @Param("targetStatus") String targetStatus, @Param("lockerId") Long lockerId,
                              @Param("pickupCode") String pickupCode);

    /**
     * 按状态聚合统计某用户的订单数量（GROUP BY status 单条 SQL），
     * 供订单摘要（getOrderSummary）与订单条目计数（getOrderItemCount）合并原先的多次逐状态 count
     *
     * @param userId 用户 ID
     * @return 每个状态一行：状态码 + 数量；用户无订单时返回空列表
     */
    List<OrderStatusCountVo> countGroupByStatus(@Param("userId") Long userId);
}
