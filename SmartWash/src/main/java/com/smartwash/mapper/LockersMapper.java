package com.smartwash.mapper;

import com.smartwash.entity.Lockers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 
 * @since 2025-03-06
 */
public interface LockersMapper extends BaseMapper<Lockers> {

    List<Lockers> getLockersBySchoolId(Long schoolId);
}
