package com.smartwash.subscription.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.subscription.constant.SubscriptionStatus;
import com.smartwash.subscription.entity.SubscriptionReminderLog;
import com.smartwash.subscription.entity.UserSubscription;
import com.smartwash.subscription.entity.UserSubscriptionUsageLog;
import com.smartwash.subscription.from.AddSubscriptionFrom;
import com.smartwash.subscription.from.SearchSubscriptionFrom;
import com.smartwash.subscription.from.UpdateSubscriptionFrom;
import com.smartwash.subscription.mapper.SubscriptionReminderLogMapper;
import com.smartwash.subscription.mapper.UserSubscriptionMapper;
import com.smartwash.subscription.mapper.UserSubscriptionUsageLogMapper;
import com.smartwash.subscription.service.UserSubscriptionService;
import com.smartwash.subscription.vo.SubscriptionGroupCountVo;
import com.smartwash.subscription.vo.SubscriptionStatsVo;
import com.smartwash.subscription.vo.UserSubscriptionVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户订阅主档服务实现。
 * 归属校验统一走 getOwnedRecord（归属不符按「订阅不存在」处理，不暴露存在性）；
 * 状态流转全部经 casTransition 条件更新闸门判影响行数（对齐 OrdersMapper.casStatus 模式），终态只读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSubscriptionServiceImpl extends ServiceImpl<UserSubscriptionMapper, UserSubscription>
        implements UserSubscriptionService {

    private final UserSubscriptionUsageLogMapper userSubscriptionUsageLogMapper;
    private final SubscriptionReminderLogMapper subscriptionReminderLogMapper;

    // ==================== CRUD ====================

    @Override
    public UserSubscriptionVo createSubscription(AddSubscriptionFrom from, Long userId) {
        validateCycleType(from.getCycleType());
        validateCancelUrl(from.getCancelUrl());
        UserSubscription entity = new UserSubscription()
                .setUserId(userId)
                .setName(from.getName().trim())
                .setAmount(from.getAmount())
                .setCycleType(from.getCycleType())
                .setCycleInterval(from.getCycleInterval() == null ? 1 : from.getCycleInterval())
                .setNextRenewDate(from.getNextRenewDate())
                .setFirstAnchorDate(from.getFirstAnchorDate())
                .setTrialEndDate(from.getTrialEndDate())
                .setAutoRenew(from.getAutoRenew() == null ? 1 : from.getAutoRenew())
                .setStatus(SubscriptionStatus.ACTIVE.getStatus())
                .setCancelUrl(StrUtil.blankToDefault(from.getCancelUrl(), null))
                .setCancelSteps(StrUtil.blankToDefault(from.getCancelSteps(), null))
                .setRemindDaysBefore(from.getRemindDaysBefore() == null ? 3 : from.getRemindDaysBefore())
                .setNote(StrUtil.blankToDefault(from.getNote(), null));
        this.save(entity);
        log.info("订阅创建成功, subscriptionId: {}, userId: {}, name: {}", entity.getSubscriptionId(), userId, entity.getName());
        return buildVo(entity, 0);
    }

    @Override
    public UserSubscriptionVo updateSubscription(Long id, UpdateSubscriptionFrom from, Long userId) {
        UserSubscription record = getOwnedRecord(id, userId);
        if (SubscriptionStatus.isTerminal(record.getStatus())) {
            throw new CustomExceptions("订阅已" + describeStatus(record.getStatus()) + "，不可编辑");
        }
        if (from.getCycleType() != null) {
            validateCycleType(from.getCycleType());
        }
        if (StrUtil.isNotBlank(from.getCancelUrl())) {
            validateCancelUrl(from.getCancelUrl());
        }
        // 显式 wrapper.set：绕开 update-strategy=not_empty 对可选字段的空值过滤，null 字段不进 SET
        LambdaUpdateWrapper<UserSubscription> wrapper = new LambdaUpdateWrapper<UserSubscription>()
                .eq(UserSubscription::getSubscriptionId, id)
                .set(UserSubscription::getUpdatedAt, LocalDateTime.now());
        if (from.getName() != null) {
            wrapper.set(UserSubscription::getName, from.getName().trim());
        }
        if (from.getAmount() != null) {
            wrapper.set(UserSubscription::getAmount, from.getAmount());
        }
        if (from.getCycleType() != null) {
            wrapper.set(UserSubscription::getCycleType, from.getCycleType());
        }
        if (from.getCycleInterval() != null) {
            wrapper.set(UserSubscription::getCycleInterval, from.getCycleInterval());
        }
        if (from.getNextRenewDate() != null) {
            wrapper.set(UserSubscription::getNextRenewDate, from.getNextRenewDate());
        }
        if (from.getFirstAnchorDate() != null) {
            wrapper.set(UserSubscription::getFirstAnchorDate, from.getFirstAnchorDate());
        }
        if (from.getTrialEndDate() != null) {
            wrapper.set(UserSubscription::getTrialEndDate, from.getTrialEndDate());
        }
        if (from.getAutoRenew() != null) {
            wrapper.set(UserSubscription::getAutoRenew, from.getAutoRenew());
        }
        if (from.getRemindDaysBefore() != null) {
            wrapper.set(UserSubscription::getRemindDaysBefore, from.getRemindDaysBefore());
        }
        if (from.getCancelUrl() != null) {
            wrapper.set(UserSubscription::getCancelUrl, StrUtil.blankToDefault(from.getCancelUrl(), null));
        }
        if (from.getCancelSteps() != null) {
            wrapper.set(UserSubscription::getCancelSteps, StrUtil.blankToDefault(from.getCancelSteps(), null));
        }
        if (from.getNote() != null) {
            wrapper.set(UserSubscription::getNote, StrUtil.blankToDefault(from.getNote(), null));
        }
        this.update(wrapper);
        log.info("订阅已更新, subscriptionId: {}, userId: {}", id, userId);
        return getDetail(id, userId);
    }

    @Override
    public void deleteSubscription(Long id, Long userId) {
        UserSubscription record = getOwnedRecord(id, userId);
        // 物理删除（本项目无逻辑删除），级联清理打卡流水与提醒，避免孤儿行
        this.removeById(id);
        userSubscriptionUsageLogMapper.delete(new LambdaQueryWrapper<UserSubscriptionUsageLog>()
                .eq(UserSubscriptionUsageLog::getSubscriptionId, id));
        subscriptionReminderLogMapper.delete(new LambdaQueryWrapper<SubscriptionReminderLog>()
                .eq(SubscriptionReminderLog::getSubscriptionId, id));
        log.info("订阅已删除, subscriptionId: {}, userId: {}, name: {}", id, userId, record.getName());
    }

    @Override
    public IPage<UserSubscriptionVo> pageMine(SearchSubscriptionFrom from, Long userId) {
        if (from.getStatus() != null && !SubscriptionStatus.isValid(from.getStatus())) {
            throw new CustomExceptions("状态筛选参数非法");
        }
        LambdaQueryWrapper<UserSubscription> wrapper = new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId);
        if (from.getStatus() != null) {
            wrapper.eq(UserSubscription::getStatus, from.getStatus());
        }
        if (StrUtil.isNotBlank(from.getKeyword())) {
            wrapper.like(UserSubscription::getName, from.getKeyword().trim());
        }
        wrapper.orderByAsc(UserSubscription::getNextRenewDate)
                .orderByDesc(UserSubscription::getSubscriptionId);
        Page<UserSubscription> result = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        Map<Long, Integer> unreadMap = countUnreadBySubscription(userId);
        Page<UserSubscriptionVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(record -> buildVo(record, unreadMap.getOrDefault(record.getSubscriptionId(), 0)))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public UserSubscriptionVo getDetail(Long id, Long userId) {
        UserSubscription record = getOwnedRecord(id, userId);
        Integer unread = subscriptionReminderLogMapper.countUnreadGroupBySubscription(userId).stream()
                .filter(c -> Objects.equals(c.getSubscriptionId(), id))
                .map(SubscriptionGroupCountVo::getCnt)
                .findFirst().map(Long::intValue).orElse(0);
        return buildVo(record, unread);
    }

    // ==================== 状态机（CAS 条件流转） ====================

    @Override
    public void pause(Long id, Long userId) {
        transition(id, userId, Set.of(SubscriptionStatus.ACTIVE.getStatus()), SubscriptionStatus.PAUSED.getStatus(), "暂停");
    }

    @Override
    public void resume(Long id, Long userId) {
        transition(id, userId, Set.of(SubscriptionStatus.PAUSED.getStatus()), SubscriptionStatus.ACTIVE.getStatus(), "恢复");
    }

    @Override
    public void cancel(Long id, Long userId) {
        transition(id, userId, Set.of(SubscriptionStatus.ACTIVE.getStatus(), SubscriptionStatus.PAUSED.getStatus()),
                SubscriptionStatus.CANCELED.getStatus(), "取消");
    }

    @Override
    public void finish(Long id, Long userId) {
        transition(id, userId, Set.of(SubscriptionStatus.ACTIVE.getStatus(), SubscriptionStatus.PAUSED.getStatus()),
                SubscriptionStatus.FINISHED.getStatus(), "结束");
    }

    private void transition(Long id, Long userId, Set<String> expectStatuses, String targetStatus, String action) {
        UserSubscription record = getOwnedRecord(id, userId);
        // CAS 条件更新闸门：0 行说明状态已被并发修改或当前状态不允许流转
        int rows = this.getBaseMapper().casTransition(id, userId, List.copyOf(expectStatuses), targetStatus);
        if (rows == 0) {
            log.info("订阅状态流转被拒绝, subscriptionId: {}, 当前状态: {}, 目标状态: {}", id, record.getStatus(), targetStatus);
            throw new CustomExceptions("当前状态不允许" + action);
        }
        log.info("订阅已{}, subscriptionId: {}, {} -> {}", action, id, record.getStatus(), targetStatus);
    }

    // ==================== 统计（月度应收 + 近 90 天使用） ====================

    @Override
    public SubscriptionStatsVo getMyStats(Long userId) {
        LocalDate today = LocalDate.now();
        List<UserSubscription> actives = this.list(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId)
                .eq(UserSubscription::getStatus, SubscriptionStatus.ACTIVE.getStatus()));
        Map<Long, Long> usage90Map = userSubscriptionUsageLogMapper
                .countGroupBySubscription(userId, today.minusDays(89))
                .stream()
                .collect(Collectors.toMap(SubscriptionGroupCountVo::getSubscriptionId, SubscriptionGroupCountVo::getCnt));

        BigDecimal monthlyPayable = BigDecimal.ZERO;
        List<SubscriptionStatsVo.Item> items = new ArrayList<>(actives.size());
        for (UserSubscription sub : actives) {
            BigDecimal monthly = monthlyNormalized(sub.getAmount(), sub.getCycleType(), sub.getCycleInterval());
            monthlyPayable = monthlyPayable.add(monthly);
            long count90 = usage90Map.getOrDefault(sub.getSubscriptionId(), 0L);
            items.add(new SubscriptionStatsVo.Item()
                    .setSubscriptionId(sub.getSubscriptionId())
                    .setName(sub.getName())
                    .setCycleType(sub.getCycleType())
                    .setCycleInterval(sub.getCycleInterval())
                    .setAmount(sub.getAmount())
                    .setMonthlyNormalized(monthly)
                    .setUsageCount90d((int) count90)
                    // 单次折算成本 = 月折算金额 × 3 ÷ 近90天使用次数（等价 月折算 ÷ 月均使用次数），无使用记录为 null
                    .setPerUseCost(count90 == 0 ? null
                            : monthly.multiply(BigDecimal.valueOf(3))
                            .divide(BigDecimal.valueOf(count90), 2, RoundingMode.HALF_UP)));
        }
        SubscriptionStatsVo vo = new SubscriptionStatsVo()
                .setMonthlyPayable(monthlyPayable.setScale(2, RoundingMode.HALF_UP))
                .setActiveCount(actives.size())
                .setItems(items);
        log.debug("订阅统计完成, userId: {}, activeCount: {}, monthlyPayable: {}", userId, actives.size(), vo.getMonthlyPayable());
        return vo;
    }

    /** 周期折算月度金额：month -> amount/interval；year -> amount/(interval*12)，HALF_UP 保留 2 位 */
    private BigDecimal monthlyNormalized(BigDecimal amount, String cycleType, Integer cycleInterval) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        int interval = (cycleInterval == null || cycleInterval < 1) ? 1 : cycleInterval;
        BigDecimal divisor = "year".equals(cycleType)
                ? BigDecimal.valueOf(12L * interval)
                : BigDecimal.valueOf(interval);
        return safeAmount.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    // ==================== 内部方法 ====================

    private UserSubscription getOwnedRecord(Long id, Long userId) {
        UserSubscription record = this.getById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new CustomExceptions("订阅不存在");
        }
        return record;
    }

    private void validateCycleType(String cycleType) {
        if (!"month".equals(cycleType) && !"year".equals(cycleType)) {
            throw new CustomExceptions("计费周期仅支持 month/year");
        }
    }

    /** 安全底线：cancel_url 须可解析为 http/https 地址（对齐 toolbox validateTarget），App 端一键打开 */
    private void validateCancelUrl(String cancelUrl) {
        if (StrUtil.isBlank(cancelUrl)) {
            return;
        }
        String trimmed = cancelUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new CustomExceptions("取消网址必须为 http/https 地址");
        }
        try {
            if (java.net.URI.create(trimmed).getHost() == null) {
                throw new CustomExceptions("取消网址必须为合法的 http/https 地址");
            }
        } catch (IllegalArgumentException e) {
            throw new CustomExceptions("取消网址必须为合法的 http/https 地址");
        }
    }

    private Map<Long, Integer> countUnreadBySubscription(Long userId) {
        return subscriptionReminderLogMapper.countUnreadGroupBySubscription(userId).stream()
                .collect(Collectors.toMap(SubscriptionGroupCountVo::getSubscriptionId,
                        c -> c.getCnt() == null ? 0 : c.getCnt().intValue()));
    }

    private String describeStatus(String status) {
        for (SubscriptionStatus s : SubscriptionStatus.values()) {
            if (s.getStatus().equals(status)) {
                return s.getDescription();
            }
        }
        return status;
    }

    private UserSubscriptionVo buildVo(UserSubscription record, int unreadCount) {
        LocalDate today = LocalDate.now();
        return new UserSubscriptionVo()
                .setSubscriptionId(record.getSubscriptionId())
                .setUserId(record.getUserId())
                .setName(record.getName())
                .setAmount(record.getAmount())
                .setCycleType(record.getCycleType())
                .setCycleInterval(record.getCycleInterval())
                .setNextRenewDate(record.getNextRenewDate())
                .setFirstAnchorDate(record.getFirstAnchorDate())
                .setTrialEndDate(record.getTrialEndDate())
                .setAutoRenew(record.getAutoRenew())
                .setStatus(record.getStatus())
                .setCancelUrl(record.getCancelUrl())
                .setCancelSteps(record.getCancelSteps())
                .setRemindDaysBefore(record.getRemindDaysBefore())
                .setNote(record.getNote())
                .setDaysUntilRenew(record.getNextRenewDate() == null ? null
                        : ChronoUnit.DAYS.between(today, record.getNextRenewDate()))
                .setTrialDaysLeft(record.getTrialEndDate() == null ? null
                        : ChronoUnit.DAYS.between(today, record.getTrialEndDate()))
                .setUnreadReminderCount(unreadCount)
                .setCreatedAt(record.getCreatedAt())
                .setUpdatedAt(record.getUpdatedAt());
    }
}
