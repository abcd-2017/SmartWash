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
public class LockersServiceImpl extends ServiceImpl<LockersMapper, Lockers> implements ILockersService {
    //获取所有存储柜
    @Override
    public Page<LockersVo> getAllLockers(SearchLockersFrom lockersFrom) {
        Page<Lockers> page = new Page<>(lockersFrom.getPage(), lockersFrom.getSize());
        LambdaQueryWrapper<Lockers> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.and(lockersFrom.getLockerId() != null, b -> b.eq(Lockers::getLockerId, lockersFrom.getLockerId()));

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

    //添加存储柜
    @Override
    public Boolean addLockers(AddLockerFrom lockerFrom) {
        Lockers Lockers = new Lockers();
        BeanUtils.copyProperties(lockerFrom, Lockers);
        return save(Lockers);
    }

    //修改存储柜
    @Override
    public Boolean updateLockers(UpdateLockerFrom lockersFrom) {
        Lockers school = getById(lockersFrom.getLockerId());
        BeanUtils.copyProperties(lockersFrom, school);
        return updateById(school);
    }

    //删除存储柜
    @Override
    public Boolean deleteLockers(String ids) {
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }
}
