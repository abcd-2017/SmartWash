package com.smartwash.mapper;

import com.smartwash.entity.Payments;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface PaymentsMapper extends BaseMapper<Payments> {

    /**
     * 支付成功幂等闸门：按网关统一订单号条件更新，仅当当前状态为“处理中”时置为“已支付”，
     * 并在同一语句内写入支付完成时间（Dashboard 收入统计依赖 status='1' 且按 paid_at 过滤）。
     *
     * @param outTradeNo 网关统一订单号
     * @return 影响行数：1=抢闸成功（首次回调）；0=已处理过或状态不符（重复回调/重放，幂等跳过）
     */
    int markSuccess(@Param("outTradeNo") String outTradeNo);

    /**
     * 支付失败闸门：按网关统一订单号条件更新，仅当当前状态为“处理中”时置为“失败”。
     * 用于真实网关场景：订单已取消/流转导致回调拒绝入账时，把处理中记录收敛为失败终态（供对账）。
     *
     * @param outTradeNo 网关统一订单号
     * @return 影响行数：1=置失败成功；0=已处于终态（重复回调幂等安全）
     */
    int markFail(@Param("outTradeNo") String outTradeNo);
}
