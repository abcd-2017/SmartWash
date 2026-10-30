package com.smartwash.subscription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.subscription.entity.SubscriptionReminderLog;
import com.smartwash.subscription.vo.SubscriptionGroupCountVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订阅提醒记录 Mapper。
 */
public interface SubscriptionReminderLogMapper extends BaseMapper<SubscriptionReminderLog> {

    /**
     * 按订阅分组统计未读提醒数（列表/详情红点）。
     */
    List<SubscriptionGroupCountVo> countUnreadGroupBySubscription(@Param("userId") Long userId);

    /**
     * 标记已读条件更新：仅未读且归属当前用户时生效（幂等，重复已读影响 0 行）。
     */
    int casMarkRead(@Param("reminderId") Long reminderId, @Param("userId") Long userId);
}
