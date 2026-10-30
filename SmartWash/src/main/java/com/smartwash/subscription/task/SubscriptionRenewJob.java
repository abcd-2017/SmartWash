package com.smartwash.subscription.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.subscription.constant.SubscriptionRemindType;
import com.smartwash.subscription.constant.SubscriptionStatus;
import com.smartwash.subscription.entity.SubscriptionReminderLog;
import com.smartwash.subscription.entity.UserSubscription;
import com.smartwash.subscription.mapper.SubscriptionReminderLogMapper;
import com.smartwash.subscription.mapper.UserSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 订阅续费推进与提醒定时任务（每日 07:30）。
 * 1. 推进：扫 auto_renew=1 AND status='active' AND next_renew_date < 当日（对齐 Wallos 扫描条件），
 *    循环加周期越过今天后按 CAS 条件更新（WHERE next_renew_date=旧值 AND auto_renew=1 AND status='active'）落库，
 *    影响行数 0 说明用户并发编辑/暂停/取消，放弃本次推进（对齐 OrderTimeoutManager 的 CAS 闸门模式）；
 * 2. 提醒：active 且 next_renew_date 落入 remind_days_before 窗口插 renew 提醒、
 *    trial_end_date 恰为当日插 trial_end 提醒（仅当日，对齐 Wallos 取消日语义）；
 *    remind_date 记目标日期（非扫描日），配合 uk_sub_type_date 唯一索引幂等，重复插入吞 DuplicateKey。
 * 说明：提醒以「天」为粒度，DB 即唯一事实源，重启天然恢复，无需内存调度（规避 OrderTimeoutManager 多实例缺陷）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRenewJob {

    /** 推进循环防御上限：1200 期（百年量级），拦截脏数据导致的死循环 */
    static final int MAX_ITERATIONS = 1200;

    private final UserSubscriptionMapper userSubscriptionMapper;
    private final SubscriptionReminderLogMapper subscriptionReminderLogMapper;

    @Scheduled(cron = "0 30 7 * * ?")
    public void run() {
        LocalDate today = LocalDate.now();
        advanceOverdue(today);
        createDueReminders(today);
    }

    /**
     * 推进停摆的自动续费订阅（可传指定日期，便于回归验证）。
     */
    public void advanceOverdue(LocalDate today) {
        List<UserSubscription> overdue = userSubscriptionMapper.selectList(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getAutoRenew, 1)
                        .eq(UserSubscription::getStatus, SubscriptionStatus.ACTIVE.getStatus())
                        .lt(UserSubscription::getNextRenewDate, today));
        if (overdue.isEmpty()) {
            log.debug("订阅续费推进：无停摆订阅");
            return;
        }
        int advanced = 0;
        int skipped = 0;
        for (UserSubscription sub : overdue) {
            LocalDate nextRenewDate = advanceDate(sub.getNextRenewDate(), today, sub.getCycleType(), sub.getCycleInterval());
            if (nextRenewDate == null) {
                // 非法周期类型/间隔或循环超限：跳过并告警，人工修复数据
                log.warn("订阅续费推进跳过：周期参数非法, subscriptionId: {}, cycleType: {}, cycleInterval: {}",
                        sub.getSubscriptionId(), sub.getCycleType(), sub.getCycleInterval());
                skipped++;
                continue;
            }
            // CAS 条件更新闸门：next_renew_date 仍为旧值且仍处于 auto_renew=1 + active 才落库
            int rows = userSubscriptionMapper.casAdvanceRenewDate(sub.getSubscriptionId(), sub.getNextRenewDate(), nextRenewDate);
            if (rows == 0) {
                // 用户并发编辑/暂停/取消已发生，放弃本次推进（下次扫描按新数据裁决）
                log.info("订阅续费推进跳过：数据已被并发修改, subscriptionId: {}", sub.getSubscriptionId());
                skipped++;
                continue;
            }
            advanced++;
            log.info("订阅续费日已推进, subscriptionId: {}, {} -> {}",
                    sub.getSubscriptionId(), sub.getNextRenewDate(), nextRenewDate);
        }
        log.info("订阅续费推进完成, 共扫描 {} 条, 推进 {} 条, 跳过 {} 条", overdue.size(), advanced, skipped);
    }

    /**
     * 生成当日应触发的提醒（可传指定日期，便于回归验证）。
     */
    public void createDueReminders(LocalDate today) {
        int created = 0;
        // 1) 续费提醒：active 且 next_renew_date 落入 remind_days_before 提前窗口（含已过期未推进的补提醒）
        List<UserSubscription> renewDue = userSubscriptionMapper.selectRenewDue(today);
        for (UserSubscription sub : renewDue) {
            if (insertReminder(buildReminder(sub, SubscriptionRemindType.RENEW.getType(), sub.getNextRenewDate()))) {
                created++;
            }
        }
        // 2) 试用截止提醒：仅 trial_end_date 恰为当日（对齐 Wallos 取消日语义）
        List<UserSubscription> trialEnding = userSubscriptionMapper.selectTrialEnding(today);
        for (UserSubscription sub : trialEnding) {
            if (insertReminder(buildReminder(sub, SubscriptionRemindType.TRIAL_END.getType(), sub.getTrialEndDate()))) {
                created++;
            }
        }
        log.info("订阅提醒生成完成, renew 扫描 {} 条, trial_end 扫描 {} 条, 新增 {} 条", renewDue.size(), trialEnding.size(), created);
    }

    /**
     * 周期推进纯函数：从 next_renew_date 循环加周期直至不早于 today（对齐 Wallos「循环加 interval 越过今天」）。
     * 月末边界：LocalDate.plusMonths/plusYears 固定 clamp（1/31+1M→2/28），与 PHP DateTime 行为一致，v1 不引入多策略枚举。
     *
     * @return 推进后的续费日；周期类型/间隔非法或循环超限时返回 null
     */
    static LocalDate advanceDate(LocalDate nextRenewDate, LocalDate today, String cycleType, Integer cycleInterval) {
        if (nextRenewDate == null || today == null || cycleInterval == null || cycleInterval < 1) {
            return null;
        }
        boolean yearly;
        if ("month".equals(cycleType)) {
            yearly = false;
        } else if ("year".equals(cycleType)) {
            yearly = true;
        } else {
            return null;
        }
        LocalDate d = nextRenewDate;
        int iterations = 0;
        while (d.isBefore(today)) {
            d = yearly ? d.plusYears(cycleInterval) : d.plusMonths(cycleInterval);
            if (++iterations > MAX_ITERATIONS) {
                return null;
            }
        }
        return d;
    }

    private SubscriptionReminderLog buildReminder(UserSubscription sub, String remindType, LocalDate remindDate) {
        return new SubscriptionReminderLog()
                .setSubscriptionId(sub.getSubscriptionId())
                .setUserId(sub.getUserId())
                .setRemindType(remindType)
                .setRemindDate(remindDate)
                .setIsRead(0);
    }

    /**
     * 幂等插入：uk_sub_type_date(subscription_id, remind_type, remind_date) 唯一索引兜底，
     * 同一订阅同类型同日重复扫描只落一条，冲突吞 DuplicateKey 视为已存在。
     */
    private boolean insertReminder(SubscriptionReminderLog reminder) {
        try {
            subscriptionReminderLogMapper.insert(reminder);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("订阅提醒已存在（幂等跳过）, subscriptionId: {}, remindType: {}, remindDate: {}",
                    reminder.getSubscriptionId(), reminder.getRemindType(), reminder.getRemindDate());
            return false;
        }
    }
}
