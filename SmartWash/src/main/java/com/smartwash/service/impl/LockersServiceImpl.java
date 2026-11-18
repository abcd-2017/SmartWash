package com.smartwash.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.LockerStatusEnum;
import com.smartwash.entity.Lockers;
import com.smartwash.from.locker.AddLockerFrom;
import com.smartwash.from.locker.BatchAddLockerFrom;
import com.smartwash.from.locker.SearchLockersFrom;
import com.smartwash.from.locker.UpdateLockerFrom;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mapper.LockersMapper;
import com.smartwash.service.ILockersService;
import com.smartwash.vo.locker.BatchAddLockerVo;
import com.smartwash.vo.locker.LockerStatusSummaryVo;
import com.smartwash.vo.locker.LockersVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockersServiceImpl extends ServiceImpl<LockersMapper, Lockers> implements ILockersService {

    @Override
    public Page<LockersVo> getAllLockers(SearchLockersFrom lockersFrom) {
        Page<Lockers> page = new Page<>(lockersFrom.getPage(), lockersFrom.getSize());
        LambdaQueryWrapper<Lockers> queryWrapper = getLockersLambdaQueryWrapper(lockersFrom);

        List<Lockers> Lockers = this.list(page, queryWrapper);
        Page<LockersVo> LockersVoPage = new Page<>();
        BeanUtils.copyProperties(page, LockersVoPage);
        LockersVoPage.setRecords(Lockers.stream().map(it -> {
            LockersVo LockersVo = new LockersVo();
            BeanUtils.copyProperties(it, LockersVo);
            return LockersVo;
        }).toList());

        return LockersVoPage;
    }

    private static LambdaQueryWrapper<Lockers> getLockersLambdaQueryWrapper(SearchLockersFrom lockersFrom) {
        LambdaQueryWrapper<Lockers> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.and(lockersFrom.getLockerId() != null, b -> b.eq(Lockers::getLockerId, lockersFrom.getLockerId()));
        queryWrapper.and(lockersFrom.getSchoolId() != null, b -> b.eq(Lockers::getSchoolId, lockersFrom.getSchoolId()));
        // 表单携带 lockerNumber 筛选但此前查询未接，Web 端按编号筛选不生效（e2e-2026-11-18 Web 报告 P1）
        queryWrapper.and(lockersFrom.getLockerNumber() != null, b -> b.eq(Lockers::getLockerNumber, lockersFrom.getLockerNumber()));
        queryWrapper.and(lockersFrom.getStatus() != null, b -> b.eq(Lockers::getStatus, lockersFrom.getStatus()));
        queryWrapper.and(lockersFrom.getStartTime() != null, b -> b.ge(Lockers::getLastUsedAt, lockersFrom.getStartTime()));
        queryWrapper.and(lockersFrom.getEndTime() != null, b -> b.le(Lockers::getLastUsedAt, lockersFrom.getEndTime()));
        return queryWrapper;
    }

    @Override
    public Boolean addLockers(AddLockerFrom lockerFrom) {
        Lockers Lockers = new Lockers();
        BeanUtils.copyProperties(lockerFrom, Lockers);
        boolean result = save(Lockers);
        log.info("添加寄存柜, schoolId: {}, lockerNumber: {}", lockerFrom.getSchoolId(), lockerFrom.getLockerNumber());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchAddLockerVo batchAddLockers(BatchAddLockerFrom from) {
        // 查该学校已占用的柜号，批量新增时自动跳过，避免同校编号重复
        List<Lockers> existing = list(new LambdaQueryWrapper<Lockers>()
                .eq(Lockers::getSchoolId, from.getSchoolId()));
        Set<Integer> occupied = existing.stream()
                .map(Lockers::getLockerNumber)
                .collect(Collectors.toSet());

        List<Integer> numbers = resolveAvailableNumbers(occupied, from.getStartNumber(), from.getCount());
        if (numbers.isEmpty()) {
            throw new CustomExceptions("没有可用的柜号");
        }

        List<Lockers> lockerList = new ArrayList<>();
        for (Integer number : numbers) {
            Lockers locker = new Lockers();
            locker.setSchoolId(from.getSchoolId());
            locker.setLockerNumber(number);
            locker.setStatus(LockerStatusEnum.FREE.getValue());
            lockerList.add(locker);
        }
        saveBatch(lockerList);

        BatchAddLockerVo vo = new BatchAddLockerVo();
        vo.setAddedCount(numbers.size());
        vo.setFirstNumber(numbers.get(0));
        vo.setLastNumber(numbers.get(numbers.size() - 1));
        log.info("批量新增寄存柜成功, schoolId: {}, range: {}-{}, addedCount: {}",
                from.getSchoolId(), vo.getFirstNumber(), vo.getLastNumber(), vo.getAddedCount());
        return vo;
    }

    //包可见以便单测：从起始号开始跳过已占用号，取满 count 个可用编号
    List<Integer> resolveAvailableNumbers(Set<Integer> occupied, int startNumber, int count) {
        List<Integer> result = new ArrayList<>(count);
        int number = startNumber;
        // 安全上限：起始号 + 需求数 + 已占用数，覆盖最坏情况（占用号连续排在前面）
        int upperBound = startNumber + count + occupied.size();
        while (result.size() < count && number <= upperBound) {
            if (!occupied.contains(number)) {
                result.add(number);
            }
            number++;
        }
        return result;
    }

    @Override
    public Boolean deleteLockersBySchoolId(Long schoolId) {
        log.info("根据学校删除寄存柜, schoolId: {}", schoolId);
        LambdaQueryWrapper<Lockers> queryWrapper = new LambdaQueryWrapper<Lockers>().eq(Lockers::getSchoolId, schoolId);
        return remove(queryWrapper);
    }

    @Override
    public Lockers getLockerById(Long schoolId, Integer lockerNumber) {
        LambdaQueryWrapper<Lockers> queryWrapper = new LambdaQueryWrapper<Lockers>().
                eq(Lockers::getSchoolId, schoolId).and(q -> q.eq(Lockers::getLockerNumber, lockerNumber));
        return getOne(queryWrapper);
    }

    @Override
    public Boolean updateLockers(UpdateLockerFrom lockersFrom) {
        log.info("修改寄存柜, lockerId: {}", lockersFrom.getLockerId());
        Lockers locker = getById(lockersFrom.getLockerId());
        if (locker == null) {
            throw new CustomExceptions("寄存柜不存在");
        }
        BeanUtils.copyProperties(lockersFrom, locker);
        return updateById(locker);
    }

    @Override
    public Boolean deleteLockers(String ids) {
        log.info("删除寄存柜, ids: {}", ids);
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return removeByIds(idList);
    }

    @Override
    public List<LockerStatusSummaryVo> getLockerStatusSummary() {
        // 单条聚合 SQL（JOIN schools + GROUP BY）完成汇总与校名装配，替代原先
        // "全表 list + 循环逐校 getById" 的 N+1 与全量加载（评审报告后端 #27）
        List<LockerStatusSummaryVo> summary = baseMapper.summarizeBySchool(
                LockerStatusEnum.FREE.getValue(), LockerStatusEnum.USE.getValue(), LockerStatusEnum.FAULT.getValue());
        // 学校不存在（已被删除）时校名为 NULL，保持原语义返回"未知"
        summary.forEach(vo -> {
            if (vo.getSchoolName() == null) {
                vo.setSchoolName("未知");
            }
        });
        return summary;
    }
}
