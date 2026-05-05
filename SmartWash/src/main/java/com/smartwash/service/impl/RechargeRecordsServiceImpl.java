package com.smartwash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.RechargeRecords;
import com.smartwash.entity.Users;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.from.recharge_records.SearchRechargeRecordsFrom;
import com.smartwash.from.recharge_records.UserRechargeFrom;
import com.smartwash.mapper.RechargeRecordsMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.vo.recharge_records.RechargeRecordsVo;
import com.smartwash.vo.users.UserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeRecordsServiceImpl extends ServiceImpl<RechargeRecordsMapper, RechargeRecords> implements IRechargeRecordsService {
    private final UsersMapper usersMapper;

    //获取所有充值记录
    @Override
    public Page<RechargeRecordsVo> getAllRecords(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        Page<RechargeRecords> page = new Page<>(rechargeRecordsFrom.getPage(), rechargeRecordsFrom.getSize());
        LambdaQueryWrapper<RechargeRecords> queryWrapper = getRechargeRecordsLambdaQueryWrapper(rechargeRecordsFrom);

        List<RechargeRecords> rechargeRecords = this.list(page, queryWrapper);
        Page<RechargeRecordsVo> rechargeRecordsVoPage = new Page<>();
        BeanUtils.copyProperties(page, rechargeRecordsVoPage);

        // 批量查询用户数据，避免N+1问题
        Set<Long> userIds = rechargeRecords.stream().map(RechargeRecords::getUserId).collect(Collectors.toSet());
        Map<Long, Users> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : usersMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(Users::getUserId, Function.identity()));

        rechargeRecordsVoPage.setRecords(rechargeRecords.stream().map(it -> {
            RechargeRecordsVo recordsVo = new RechargeRecordsVo();
            BeanUtils.copyProperties(it, recordsVo);
            Users users = userMap.get(it.getUserId());
            UserVo userVo = new UserVo();
            if (users != null) {
                userVo.setUserId(users.getUserId());
                userVo.setPhoneNumber(users.getPhoneNumber());
            }
            recordsVo.setUsers(userVo);
            return recordsVo;
        }).toList());

        return rechargeRecordsVoPage;
    }

    //获取当前用户充值记录
    @Override
    public Page<RechargeRecordsVo> getUserRechargeRecords(Long userId, BaseSearchFrom searchFrom) {
        Page<RechargeRecords> page = new Page<>(searchFrom.getPage(), searchFrom.getSize());
        LambdaQueryWrapper<RechargeRecords> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RechargeRecords::getUserId, userId);
        queryWrapper.orderByDesc(RechargeRecords::getRechargeTime);

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

    @Transactional
    @Override
    public boolean userRecharge(UserRechargeFrom vo, Long userId) {
        Users user = usersMapper.selectById(userId);
        BigDecimal balance = BigDecimal.valueOf(vo.getAmount());
        //1.充值表插入数据
        RechargeRecords records = new RechargeRecords();
        records.setUserId(userId);
        records.setRechargeType(vo.getRechargeType());
        records.setAmount(balance);
        save(records);

        //2.用户表中添加对应余额
        usersMapper.addUserBalance(userId, balance);

        log.info("用户充值成功, userId: {}, amount: {}", userId, balance);
        return true;
    }

    private LambdaQueryWrapper<RechargeRecords> getRechargeRecordsLambdaQueryWrapper(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        LambdaQueryWrapper<RechargeRecords> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(rechargeRecordsFrom.getPhoneNumber())) {
            Users user = usersMapper.getUserByPhoneNumber(rechargeRecordsFrom.getPhoneNumber());
            Long userId = user == null ? -1 : user.getUserId();
            queryWrapper.eq(RechargeRecords::getUserId, userId);
        }

        queryWrapper.and(rechargeRecordsFrom.getRecordId() != null, b -> b.eq(RechargeRecords::getRecordId, rechargeRecordsFrom.getRecordId()));
        queryWrapper.and(rechargeRecordsFrom.getStartTime() != null, b -> b.ge(RechargeRecords::getRechargeTime, rechargeRecordsFrom.getStartTime()));
        queryWrapper.and(rechargeRecordsFrom.getEndTime() != null, b -> b.le(RechargeRecords::getRechargeTime, rechargeRecordsFrom.getEndTime()));
        queryWrapper.and(rechargeRecordsFrom.getRechargeType() != null, b -> b.eq(RechargeRecords::getRechargeType, rechargeRecordsFrom.getRechargeType()));

        return queryWrapper;
    }
}
