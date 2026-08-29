package com.smartwash.mapper;

import com.smartwash.entity.RechargeRecords;
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
public interface RechargeRecordsMapper extends BaseMapper<RechargeRecords> {

    /**
     * 充值成功幂等闸门：按网关统一订单号条件更新，仅当当前状态为“处理中”时置为“已到账”。
     * 调用方必须以影响行数 == 1 判定首次回调成功（之后才允许加余额），== 0 表示已处理过（重复回调/重放，直接幂等返回）。
     *
     * @param outTradeNo 网关统一订单号
     * @return 影响行数：1=抢闸成功；0=已处理过或状态不符
     */
    int markSuccess(@Param("outTradeNo") String outTradeNo);
}
