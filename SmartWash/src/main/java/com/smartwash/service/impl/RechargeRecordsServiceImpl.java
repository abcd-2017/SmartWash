package com.smartwash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.RechargeRecords;
import com.smartwash.from.recharge_records_from.SearchRechargeRecordsFrom;
import com.smartwash.mapper.RechargeRecordsMapper;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.vo.recharge_records_vo.RechargeRecordsVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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
public class RechargeRecordsServiceImpl extends ServiceImpl<RechargeRecordsMapper, RechargeRecords> implements IRechargeRecordsService {

    //获取所有充值记录
    @Override
    public Page<RechargeRecordsVo> getAllRecords(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        Page<RechargeRecords> page = new Page<>(rechargeRecordsFrom.getPage(), rechargeRecordsFrom.getSize());
        LambdaQueryWrapper<RechargeRecords> queryWrapper = getRechargeRecordsLambdaQueryWrapper(rechargeRecordsFrom);

        List<RechargeRecords> rechargeRecords = this.list(page, queryWrapper);
        Page<RechargeRecordsVo> rechargeRecordsVoPage = new Page<>();
        BeanUtils.copyProperties(page, rechargeRecordsVoPage);
        rechargeRecordsVoPage.setRecords(rechargeRecords.stream().map(it -> {
            RechargeRecordsVo recordsVo = new RechargeRecordsVo();
            BeanUtils.copyProperties(it, recordsVo);
            return recordsVo;
        }).toList());

        return rechargeRecordsVoPage;
    }

    private static LambdaQueryWrapper<RechargeRecords> getRechargeRecordsLambdaQueryWrapper(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        LambdaQueryWrapper<RechargeRecords> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.and(rechargeRecordsFrom.getRecordId() != null, b -> b.eq(RechargeRecords::getRecordId, rechargeRecordsFrom.getRecordId()));
        queryWrapper.and(rechargeRecordsFrom.getUserId() != null, b -> b.eq(RechargeRecords::getUserId, rechargeRecordsFrom.getUserId()));
        queryWrapper.and(rechargeRecordsFrom.getStartTime() != null, b -> b.ge(RechargeRecords::getRechargeTime, rechargeRecordsFrom.getStartTime()));
        queryWrapper.and(rechargeRecordsFrom.getEndTime() != null, b -> b.le(RechargeRecords::getRechargeTime, rechargeRecordsFrom.getEndTime()));
        queryWrapper.and(rechargeRecordsFrom.getRechargeType() != null, b -> b.eq(RechargeRecords::getRechargeType, rechargeRecordsFrom.getRechargeType()));

        return queryWrapper;
    }
}
