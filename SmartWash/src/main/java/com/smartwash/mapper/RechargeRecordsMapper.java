package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.entity.RechargeRecords;
import com.smartwash.vo.recharge_records.RechargeSummaryVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface RechargeRecordsMapper extends BaseMapper<RechargeRecords> {

    /**
     * 充值成功幂等闸门：按网关统一订单号条件更新，仅当当前状态为“处理中”时置为“已到账”。
     * 调用方必须以影响行数 == 1 判定首次回调成功（之后才允许加余额），== 0 表示已处理过（重复回调/重放，直接幂等返回）。
     *
     * @param outTradeNo 网关统一订单号
     * @return 影响行数：1=抢闸成功；0=已处理过或状态不符
     */
    int markSuccess(@Param("outTradeNo") String outTradeNo);

    /**
     * 充值记录汇总统计：一次条件聚合返回总笔数/成功笔数/总金额/成功金额。
     * XML 中手动追加 is_delete = 0（@TableLogic 不作用于手写 SQL）。
     *
     * @param dateFrom 起始时间（含 recharge_time >=），可空 = 不限
     * @param dateTo   结束时间（含 recharge_time <=），可空 = 不限
     * @return 汇总结果
     */
    RechargeSummaryVo selectRechargeSummary(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);
}
