package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.RechargeRecords;
import com.smartwash.from.recharge_records.SearchRechargeRecordsFrom;
import com.smartwash.from.recharge_records.UserRechargeFrom;
import com.smartwash.vo.recharge_records.RechargeRecordsVo;

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

    //用户充值
    boolean userRecharge(UserRechargeFrom vo, Long userId);
}
