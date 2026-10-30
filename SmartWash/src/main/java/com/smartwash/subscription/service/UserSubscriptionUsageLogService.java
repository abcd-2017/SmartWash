package com.smartwash.subscription.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.subscription.entity.UserSubscriptionUsageLog;
import com.smartwash.subscription.from.AddUsageFrom;
import com.smartwash.subscription.vo.SubscriptionGroupCountVo;
import com.smartwash.subscription.vo.SubscriptionUsageVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 订阅使用打卡流水服务。
 * 每订阅每天一条，可撤销后重打；打卡归属与订阅状态校验先行，终态订阅不可打卡。
 */
public interface UserSubscriptionUsageLogService extends IService<UserSubscriptionUsageLog> {

    /** 使用打卡（不传日期默认今天，不允许未来日期；同日重复打卡拒绝） */
    SubscriptionUsageVo checkIn(Long subscriptionId, Long userId, AddUsageFrom from);

    /** 撤销打卡（物理删除，须归属当前用户且属于指定订阅） */
    void deleteUsage(Long subscriptionId, Long usageId, Long userId);

    /** 指定订阅的打卡记录分页（按使用日期倒序） */
    IPage<SubscriptionUsageVo> pageBySubscription(Long subscriptionId, Long userId, long page, long size);

    /** 按订阅分组统计起始日（含）以来的使用次数 */
    List<SubscriptionGroupCountVo> countGroupBySubscription(Long userId, LocalDate sinceDate);
}
