package com.smartwash.subscription.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.subscription.entity.SubscriptionReminderLog;
import com.smartwash.subscription.vo.SubscriptionGroupCountVo;
import com.smartwash.subscription.vo.SubscriptionReminderVo;

import java.util.List;
import java.util.Map;

/**
 * 订阅提醒记录服务。
 * 提醒由定时任务按唯一索引幂等写入，本服务只负责用户侧读取与已读。
 */
public interface SubscriptionReminderLogService extends IService<SubscriptionReminderLog> {

    /** 我的提醒列表（unread=true 只看未读；按应提醒日倒序，上限200条） */
    List<SubscriptionReminderVo> listMine(Long userId, Boolean unread);

    /** 标记已读（幂等：已读重复标记静默成功） */
    void markRead(Long reminderId, Long userId);

    /** 按订阅分组统计未读提醒数（列表/详情红点） */
    Map<Long, Integer> countUnreadGroupBySubscription(Long userId);
}
