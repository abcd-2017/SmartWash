package com.smartwash.subscription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.subscription.entity.SubscriptionReminderLog;
import com.smartwash.subscription.entity.UserSubscription;
import com.smartwash.subscription.mapper.SubscriptionReminderLogMapper;
import com.smartwash.subscription.mapper.UserSubscriptionMapper;
import com.smartwash.subscription.service.SubscriptionReminderLogService;
import com.smartwash.subscription.vo.SubscriptionGroupCountVo;
import com.smartwash.subscription.vo.SubscriptionReminderVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订阅提醒记录服务实现。
 * 写入由定时任务完成（唯一索引幂等），本服务只负责用户侧读取与已读标记。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionReminderLogServiceImpl extends ServiceImpl<SubscriptionReminderLogMapper, SubscriptionReminderLog>
        implements SubscriptionReminderLogService {

    /** 提醒列表上限：提醒按天累积，防止无限增长的全量拉取 */
    private static final int LIST_LIMIT = 200;

    private final UserSubscriptionMapper userSubscriptionMapper;

    @Override
    public List<SubscriptionReminderVo> listMine(Long userId, Boolean unread) {
        LambdaQueryWrapper<SubscriptionReminderLog> wrapper = new LambdaQueryWrapper<SubscriptionReminderLog>()
                .eq(SubscriptionReminderLog::getUserId, userId);
        if (Boolean.TRUE.equals(unread)) {
            wrapper.eq(SubscriptionReminderLog::getIsRead, 0);
        }
        wrapper.orderByDesc(SubscriptionReminderLog::getRemindDate)
                .orderByDesc(SubscriptionReminderLog::getReminderId)
                .last("LIMIT " + LIST_LIMIT);
        List<SubscriptionReminderLog> records = this.list(wrapper);
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> nameMap = userSubscriptionMapper.selectBatchIds(
                        records.stream().map(SubscriptionReminderLog::getSubscriptionId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(UserSubscription::getSubscriptionId, UserSubscription::getName, (a, b) -> a));
        return records.stream().map(record -> buildVo(record, nameMap.get(record.getSubscriptionId())))
                .collect(Collectors.toList());
    }

    @Override
    public void markRead(Long reminderId, Long userId) {
        SubscriptionReminderLog record = this.getById(reminderId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new CustomExceptions("提醒不存在");
        }
        if (Integer.valueOf(1).equals(record.getIsRead())) {
            // 已读重复标记幂等成功
            return;
        }
        this.getBaseMapper().casMarkRead(reminderId, userId);
        log.debug("提醒已读, reminderId: {}, userId: {}", reminderId, userId);
    }

    @Override
    public Map<Long, Integer> countUnreadGroupBySubscription(Long userId) {
        return this.getBaseMapper().countUnreadGroupBySubscription(userId).stream()
                .collect(Collectors.toMap(SubscriptionGroupCountVo::getSubscriptionId,
                        c -> c.getCnt() == null ? 0 : c.getCnt().intValue()));
    }

    private SubscriptionReminderVo buildVo(SubscriptionReminderLog record, String subscriptionName) {
        return new SubscriptionReminderVo()
                .setReminderId(record.getReminderId())
                .setSubscriptionId(record.getSubscriptionId())
                .setSubscriptionName(subscriptionName)
                .setRemindType(record.getRemindType())
                .setRemindDate(record.getRemindDate())
                .setIsRead(record.getIsRead())
                .setCreatedAt(record.getCreatedAt());
    }
}
