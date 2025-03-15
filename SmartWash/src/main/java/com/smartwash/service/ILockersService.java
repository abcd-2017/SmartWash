package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Lockers;
import com.smartwash.from.locker.AddLockerFrom;
import com.smartwash.from.locker.SearchLockersFrom;
import com.smartwash.from.locker.UpdateLockerFrom;
import com.smartwash.vo.locker.LockersVo;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface ILockersService extends IService<Lockers> {

    Page<LockersVo> getAllLockers(SearchLockersFrom lockersFrom);

    Boolean updateLockers(UpdateLockerFrom lockerFrom);

    Boolean deleteLockers(String ids);

    Boolean addLockers(AddLockerFrom addLockerFrom);

    Boolean deleteLockersBySchoolId(Long schoolId);

    Lockers getLockerById(Long schoolId, Integer lockerNumber);
}
