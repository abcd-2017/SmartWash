package com.smartwash.divination.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.entity.DivUsageDaily;
import com.smartwash.divination.mapper.DivBlockedQuestionMapper;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivRecordMapper;
import com.smartwash.divination.mapper.DivUsageDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日用量聚合任务。
 * 定时将当日 div_record/div_interpretation/div_blocked_question 统计落库 div_usage_daily，
 * 供管理端用量看板消费。
 *
 * 聚合维度：stat_date + method。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DivUsageAggregator {

    private final DivRecordMapper recordMapper;
    private final DivInterpretationMapper interpretationMapper;
    private final DivBlockedQuestionMapper blockedQuestionMapper;
    private final DivUsageDailyMapper usageDailyMapper;

    /** 支持的术数方法 */
    private static final String[] METHODS = {"liuyao", "meihua", "qimen", "liuren"};

    /**
     * 每日凌晨 00:05 聚合前一天用量。
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void aggregateDaily() {
        log.info("开始聚合昨日观象台用量");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        aggregateDate(yesterday);
        log.info("昨日观象台用量聚合完成");
    }

    /**
     * 聚合指定日期的用量（幂等：先删后插）。
     */
    public void aggregateDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        for (String method : METHODS) {
            // 卦例数
            LambdaQueryWrapper<DivRecord> recordWrapper = new LambdaQueryWrapper<>();
            recordWrapper.eq(DivRecord::getMethod, method)
                    .ge(DivRecord::getCreatedAt, start)
                    .lt(DivRecord::getCreatedAt, end);
            int recordCount = Math.toIntExact(recordMapper.selectCount(recordWrapper));

            // 解读数 + token 统计 + 缓存命中
            LambdaQueryWrapper<DivInterpretation> interpretWrapper = new LambdaQueryWrapper<>();
            // 按 record 的 method 关联（简化：直接按解读时间统计）
            interpretWrapper.ge(DivInterpretation::getCreatedAt, start)
                    .lt(DivInterpretation::getCreatedAt, end);
            List<DivInterpretation> interpretations = interpretationMapper.selectList(interpretWrapper);
            int interpretCount = interpretations.size();
            long tokensIn = interpretations.stream().mapToLong(i -> i.getTokensIn() != null ? i.getTokensIn() : 0).sum();
            long tokensOut = interpretations.stream().mapToLong(i -> i.getTokensOut() != null ? i.getTokensOut() : 0).sum();
            int cacheHitCount = (int) interpretations.stream().filter(i -> i.getCacheHit() != null && i.getCacheHit() == 1).count();

            // 拦截数
            LambdaQueryWrapper<com.smartwash.divination.entity.DivBlockedQuestion> blockedWrapper = new LambdaQueryWrapper<>();
            blockedWrapper.eq(com.smartwash.divination.entity.DivBlockedQuestion::getMethod, method)
                    .ge(com.smartwash.divination.entity.DivBlockedQuestion::getCreatedAt, start)
                    .lt(com.smartwash.divination.entity.DivBlockedQuestion::getCreatedAt, end);
            int blockedCount = Math.toIntExact(blockedQuestionMapper.selectCount(blockedWrapper));

            // 活跃用户数（去重）
            int activeUsers = (int) interpretations.stream()
                    .map(DivInterpretation::getUserId)
                    .distinct()
                    .count();

            // 幂等：先删后插
            LambdaQueryWrapper<DivUsageDaily> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(DivUsageDaily::getStatDate, date).eq(DivUsageDaily::getMethod, method);
            usageDailyMapper.delete(deleteWrapper);

            DivUsageDaily daily = new DivUsageDaily();
            daily.setStatDate(date);
            daily.setMethod(method);
            daily.setRecordCount(recordCount);
            daily.setInterpretCount(interpretCount);
            daily.setCacheHitCount(cacheHitCount);
            daily.setBlockedCount(blockedCount);
            daily.setTokensIn(tokensIn);
            daily.setTokensOut(tokensOut);
            daily.setActiveUsers(activeUsers);
            usageDailyMapper.insert(daily);
        }
    }
}
