package com.smartwash.subscription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.subscription.entity.UserSubscription;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户订阅主表 Mapper。
 * CAS 条件更新是状态流转与续费推进的唯一并发闸门（对齐 OrdersMapper.casStatus 模式）。
 */
public interface UserSubscriptionMapper extends BaseMapper<UserSubscription> {

    /**
     * 状态机条件流转（并发闸门）：仅当当前状态命中 expectStatuses 时流转到 targetStatus，
     * 影响行数为 0 说明状态已被并发修改或当前状态不允许流转。
     */
    int casTransition(@Param("subscriptionId") Long subscriptionId,
                      @Param("userId") Long userId,
                      @Param("expectStatuses") List<String> expectStatuses,
                      @Param("targetStatus") String targetStatus);

    /**
     * 续费日推进 CAS（并发闸门）：仅当 next_renew_date 仍为旧值且订阅仍处于 auto_renew=1 + active 时落库，
     * 防止与用户手动编辑/暂停/取消并发时覆盖用户意图。
     */
    int casAdvanceRenewDate(@Param("subscriptionId") Long subscriptionId,
                            @Param("expectDate") LocalDate expectDate,
                            @Param("newDate") LocalDate newDate);

    /**
     * 续费提醒扫描：active 且 next_renew_date 落入 remind_days_before 提前窗口（列间比较，含已过期的补提醒）。
     */
    List<UserSubscription> selectRenewDue(@Param("today") LocalDate today);

    /**
     * 试用截止提醒扫描：active 且 trial_end_date 恰为当日（仅当日，对齐 Wallos 取消日语义）。
     */
    List<UserSubscription> selectTrialEnding(@Param("today") LocalDate today);
}
