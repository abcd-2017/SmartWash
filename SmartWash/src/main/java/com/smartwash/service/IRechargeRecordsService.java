package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.RechargeRecords;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.from.recharge_records.SearchRechargeRecordsFrom;
import com.smartwash.from.recharge_records.UserRechargeFrom;
import com.smartwash.vo.recharge_records.RechargeRecordsVo;
import com.smartwash.vo.recharge_records.RechargeSummaryVo;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface IRechargeRecordsService extends IService<RechargeRecords> {

    Page<RechargeRecordsVo> getAllRecords(SearchRechargeRecordsFrom rechargeRecordsFrom);

    /**
     * 充值记录汇总统计：总笔数/成功笔数/总金额/成功金额
     *
     * @param dateFrom 起始时间（含），可空 = 不限
     * @param dateTo   结束时间（含），可空 = 不限
     * @return 汇总结果，空值统一归零
     */
    RechargeSummaryVo getRechargeSummary(LocalDateTime dateFrom, LocalDateTime dateTo);

    //用户充值
    boolean userRecharge(UserRechargeFrom vo, Long userId);

    //获取当前用户充值记录
    Page<RechargeRecordsVo> getUserRechargeRecords(Long userId, BaseSearchFrom searchFrom);
}
