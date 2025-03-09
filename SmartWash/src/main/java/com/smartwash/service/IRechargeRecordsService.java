package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.entity.RechargeRecords;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.from.recharge_records_from.SearchRechargeRecordsFrom;
import com.smartwash.vo.recharge_records_vo.RechargeRecordsVo;
import com.smartwash.vo.schools_vo.SchoolsVo;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 
 * @since 2025-03-06
 */
public interface IRechargeRecordsService extends IService<RechargeRecords> {

    Page<RechargeRecordsVo> getAllRecords(SearchRechargeRecordsFrom rechargeRecordsFrom);
}
