package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Lockers;
import com.smartwash.from.locker.AddLockerFrom;
import com.smartwash.from.locker.BatchAddLockerFrom;
import com.smartwash.from.locker.SearchLockersFrom;
import com.smartwash.from.locker.UpdateLockerFrom;
import com.smartwash.vo.locker.BatchAddLockerVo;
import com.smartwash.vo.locker.LockerStatusSummaryVo;
import com.smartwash.vo.locker.LockersVo;

import java.util.List;

public interface ILockersService extends IService<Lockers> {

    Page<LockersVo> getAllLockers(SearchLockersFrom lockersFrom);

    Boolean updateLockers(UpdateLockerFrom lockerFrom);

    Boolean deleteLockers(String ids);

    Boolean addLockers(AddLockerFrom addLockerFrom);

    BatchAddLockerVo batchAddLockers(BatchAddLockerFrom from);

    Boolean deleteLockersBySchoolId(Long schoolId);

    Lockers getLockerById(Long schoolId, Integer lockerNumber);

    List<LockerStatusSummaryVo> getLockerStatusSummary();
}
