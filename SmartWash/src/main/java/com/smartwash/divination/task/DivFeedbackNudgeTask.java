package com.smartwash.divination.task;

import com.smartwash.divination.service.DivNudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 应验回访提醒每日扫描任务（占卜闭环）。
 * 扫描「解读完成 ≥3 天、该解读无 div_feedback、且无既有 div_feedback_nudge」的记录，
 * 插入 status=0 的待回访提醒（nudge_date = 解读完成日 + 3 天）。
 * 风格参照 DivUsageAggregator；单机内存调度（与 OrderTimeoutManager 同一已知约束，多实例部署需外置调度）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DivFeedbackNudgeTask {

    private final DivNudgeService nudgeService;

    /**
     * 每日凌晨 00:10 扫描（晚于 DivUsageAggregator 的 00:05，错峰降低 DB 争用）。
     */
    @Scheduled(cron = "0 10 0 * * ?")
    public void scanDaily() {
        log.info("开始扫描应验回访提醒");
        int created = scanNudges(LocalDateTime.now());
        log.info("应验回访提醒扫描完成, 新增: {}", created);
    }

    /**
     * 执行一次扫描（公开供测试/手动触发），返回新增提醒条数。
     */
    public int scanNudges(LocalDateTime now) {
        return nudgeService.createNudgesForReadyInterpretations(now);
    }
}
