package com.smartwash.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.Lockers;
import com.smartwash.from.locker.AddLockerFrom;
import com.smartwash.from.locker.SearchLockersFrom;
import com.smartwash.from.locker.UpdateLockerFrom;
import com.smartwash.mapper.LockersMapper;
import com.smartwash.service.ILockersService;
import com.smartwash.vo.locker.LockersVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Slf4j
@Service
public class LockersServiceImpl extends ServiceImpl<LockersMapper, Lockers> implements ILockersService {
    //获取所有存储柜
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
        queryWrapper.and(lockersFrom.getStatus() != null, b -> b.eq(Lockers::getStatus, lockersFrom.getStatus()));
        queryWrapper.and(lockersFrom.getStartTime() != null, b -> b.ge(Lockers::getLastUsedAt, lockersFrom.getStartTime()));
        queryWrapper.and(lockersFrom.getEndTime() != null, b -> b.le(Lockers::getLastUsedAt, lockersFrom.getEndTime()));
        return queryWrapper;
    }

    //添加存储柜
    @Override
    public Boolean addLockers(AddLockerFrom lockerFrom) {
        Lockers Lockers = new Lockers();
        BeanUtils.copyProperties(lockerFrom, Lockers);
        boolean result = save(Lockers);
        log.info("添加寄存柜, schoolId: {}, lockerNumber: {}", lockerFrom.getSchoolId(), lockerFrom.getLockerNumber());
        return result;
    }

    //根据学校id删除存储柜
    @Override
    public Boolean deleteLockersBySchoolId(Long schoolId) {
        log.info("根据学校删除寄存柜, schoolId: {}", schoolId);
        LambdaQueryWrapper<Lockers> queryWrapper = new LambdaQueryWrapper<Lockers>().eq(Lockers::getSchoolId, schoolId);
        return remove(queryWrapper);
    }

    //判断当前学校的这个柜号有没有被使用
    @Override
    public Lockers getLockerById(Long schoolId, Integer lockerNumber) {
        LambdaQueryWrapper<Lockers> queryWrapper = new LambdaQueryWrapper<Lockers>().
                eq(Lockers::getSchoolId, schoolId).and(q -> q.eq(Lockers::getLockerNumber, lockerNumber));
        return getOne(queryWrapper);
    }

    //修改存储柜
    @Override
    public Boolean updateLockers(UpdateLockerFrom lockersFrom) {
        log.info("修改寄存柜, lockerId: {}", lockersFrom.getLockerId());
        Lockers school = getById(lockersFrom.getLockerId());
        BeanUtils.copyProperties(lockersFrom, school);
        return updateById(school);
    }

    //删除存储柜
    @Override
    public Boolean deleteLockers(String ids) {
        log.info("删除寄存柜, ids: {}", ids);
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }
}
