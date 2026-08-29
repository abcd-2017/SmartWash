package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.entity.Lockers;
import com.smartwash.vo.locker.LockerStatusSummaryVo;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 按学校聚合柜子状态汇总（JOIN schools + GROUP BY 单条 SQL，消除 N+1）
     *
     * @param freeStatus 空闲状态值（LockerStatusEnum.FREE）
     * @param useStatus  使用中状态值（LockerStatusEnum.USE）
     * @param faultStatus 故障状态值（LockerStatusEnum.FAULT）
     * @return 每个有柜子的学校一行汇总；学校不存在时 schoolName 为 null（调用方补"未知"）
     */
    List<LockerStatusSummaryVo> summarizeBySchool(@Param("freeStatus") String freeStatus,
                                                  @Param("useStatus") String useStatus,
                                                  @Param("faultStatus") String faultStatus);
}
