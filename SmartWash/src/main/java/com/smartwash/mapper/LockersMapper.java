package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.entity.Lockers;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface LockersMapper extends BaseMapper<Lockers> {

    List<Lockers> getLockersBySchoolId(Long schoolId);

    //查询并锁定空闲寄存柜（FOR UPDATE，防止竞态条件）
    Lockers getFreeLockerBySchoolIdForUpdate(Long schoolId);

    //解除寄存柜占用
    void unLocker(Long lockerId, String status);
}
