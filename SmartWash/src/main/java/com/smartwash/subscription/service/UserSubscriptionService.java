package com.smartwash.subscription.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.subscription.entity.UserSubscription;
import com.smartwash.subscription.from.AddSubscriptionFrom;
import com.smartwash.subscription.from.SearchSubscriptionFrom;
import com.smartwash.subscription.from.UpdateSubscriptionFrom;
import com.smartwash.subscription.vo.SubscriptionStatsVo;
import com.smartwash.subscription.vo.UserSubscriptionVo;

/**
 * 用户订阅主档服务。
 * 全部操作以 userId 为主体隔离（归属不符按「订阅不存在」处理，不暴露存在性）；
 * 状态流转统一走 CAS 条件更新闸门，终态（canceled/finished）只读。
 */
public interface UserSubscriptionService extends IService<UserSubscription> {

    /** 创建订阅（初始状态 active） */
    UserSubscriptionVo createSubscription(AddSubscriptionFrom from, Long userId);

    /** 编辑订阅（全字段可选，null=不改；终态拒绝编辑） */
    UserSubscriptionVo updateSubscription(Long id, UpdateSubscriptionFrom from, Long userId);

    /** 删除订阅（物理删除，级联清理打卡流水与提醒记录） */
    void deleteSubscription(Long id, Long userId);

    /** 我的订阅分页（含未读提醒数与距续费天数） */
    IPage<UserSubscriptionVo> pageMine(SearchSubscriptionFrom from, Long userId);

    /** 订阅详情（含未读提醒数） */
    UserSubscriptionVo getDetail(Long id, Long userId);

    /** 暂停：active → paused */
    void pause(Long id, Long userId);

    /** 恢复：paused → active */
    void resume(Long id, Long userId);

    /** 取消：active/paused → canceled（终态保留数据） */
    void cancel(Long id, Long userId);

    /** 结束：active/paused → finished（终态保留数据，语义为试用结束不再续用） */
    void finish(Long id, Long userId);

    /** 统计：月度应收总额 + 各活跃订阅近 90 天使用次数与单次折算成本 */
    SubscriptionStatsVo getMyStats(Long userId);
}
