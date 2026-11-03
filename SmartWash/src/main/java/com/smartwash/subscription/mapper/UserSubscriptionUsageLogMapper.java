package com.smartwash.subscription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.subscription.entity.UserSubscriptionUsageLog;
import com.smartwash.subscription.vo.SubscriptionGroupCountVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 订阅使用打卡流水 Mapper。
 */
public interface UserSubscriptionUsageLogMapper extends BaseMapper<UserSubscriptionUsageLog> {

    /**
     * 按订阅分组统计起始日（含）以来的使用次数（近 90 天决策数据）。
     */
    List<SubscriptionGroupCountVo> countGroupBySubscription(@Param("userId") Long userId,
                                                            @Param("sinceDate") LocalDate sinceDate);
}
