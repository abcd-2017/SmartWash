package com.smartwash.toolbox.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.entity.ToolboxShortVisit;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 过期短码定时清理任务。
 * 失效判断全部在读取路径完成（对齐 shlink isEnabled），本任务只做物理清理不参与判断：
 * 每日物理删除 expire_at 已过 30 天的短码与关联访问明细，单实例无需分布式锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolboxShortCodeCleaner {

    /** 过期宽限期：expire_at 已过 30 天才清理（留出统计与排查窗口） */
    private static final int RETENTION_DAYS = 30;

    private final ToolboxShortCodeService toolboxShortCodeService;
    private final ToolboxShortVisitService toolboxShortVisitService;

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);
        LambdaQueryWrapper<ToolboxShortCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(ToolboxShortCode::getExpireAt)
                .lt(ToolboxShortCode::getExpireAt, threshold);
        List<ToolboxShortCode> expired = toolboxShortCodeService.list(wrapper);
        if (expired.isEmpty()) {
            log.debug("无过期短码需要清理");
            return;
        }
        for (ToolboxShortCode record : expired) {
            // 明细以 code 冗余关联（无外键），先清明细再删主记录；
            // 无需 DEL 缓存：带 expire_at 的记录不满足「干净」条件从不进跳转缓存，负缓存 60s 自愈且语义仍正确（码已删=404）
            LambdaQueryWrapper<ToolboxShortVisit> visitWrapper = new LambdaQueryWrapper<>();
            visitWrapper.eq(ToolboxShortVisit::getCode, record.getCode());
            toolboxShortVisitService.remove(visitWrapper);
            toolboxShortCodeService.removeById(record.getId());
        }
        log.info("过期短码清理完成, 共清理 {} 条（expire_at 早于 {}）", expired.size(), threshold);
    }
}
