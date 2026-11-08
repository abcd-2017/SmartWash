package com.smartwash.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.points.entity.PointSignIn;
import org.apache.ibatis.annotations.Param;

/**
 * 每日签到 Mapper。
 * uk_sign_user_date(user_id, sign_date) 唯一索引是并发幂等的 DB 闸门。
 */
public interface PointSignInMapper extends BaseMapper<PointSignIn> {

    /**
     * 按用户 + 日期查询签到记录（连签判定 / 幂等快路径）
     */
    PointSignIn selectByUserIdAndDate(@Param("userId") Long userId, @Param("signDate") java.time.LocalDate signDate);
}
