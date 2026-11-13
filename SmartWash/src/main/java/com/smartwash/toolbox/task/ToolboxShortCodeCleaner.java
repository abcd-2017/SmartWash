package com.smartwash.toolbox.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.mapper.ToolboxShortVisitMapper;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import com.smartwash.toolbox.toolkit.ShortCodeContentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 过期短码定时清理任务。
 * 失效判断全部在读取路径完成（对齐 shlink isEnabled），本任务只做物理清理不参与判断：
 * 每日先将过期未焚毁的私密文本内容置空（防「尸体」留库），再物理删除 expire_at 已过 30 天的短码，
 * 随后全局清扫无主访问明细（含历史悬挂）。
 * 单实例无需分布式锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolboxShortCodeCleaner {

    /** 过期宽限期：expire_at 已过 30 天才清理（留出统计与排查窗口） */
    private static final int RETENTION_DAYS = 30;

    private final ToolboxShortCodeService toolboxShortCodeService;
    private final ToolboxShortVisitService toolboxShortVisitService;
    private final ToolboxShortVisitMapper toolboxShortVisitMapper;

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanExpired() {
        // 私密文本（contentType=7）过期未焚毁：置空内容（密文一并清除，未被领取也不留库）
        boolean secretSwept = toolboxShortCodeService.update(new LambdaUpdateWrapper<ToolboxShortCode>()
                .eq(ToolboxShortCode::getContentType, ShortCodeContentType.SECRET_TEXT)
                .isNull(ToolboxShortCode::getBurnedAt)
                .isNotNull(ToolboxShortCode::getExpireAt)
                .lt(ToolboxShortCode::getExpireAt, LocalDateTime.now())
                .set(ToolboxShortCode::getContent, ""));

        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);
        LambdaQueryWrapper<ToolboxShortCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(ToolboxShortCode::getExpireAt)
                .lt(ToolboxShortCode::getExpireAt, threshold);
        List<ToolboxShortCode> expired = toolboxShortCodeService.list(wrapper);
        // 先删过期主记录；无需 DEL 缓存：带 expire_at 的记录不满足「干净」条件从不进跳转缓存，负缓存 60s 自愈且语义仍正确（码已删=404）
        for (ToolboxShortCode record : expired) {
            toolboxShortCodeService.removeById(record.getId());
        }
        // 再全局清扫无主明细（含刚删的过期短码与历史悬挂；以现存短码表为基准）
        int swept = toolboxShortVisitMapper.sweepOrphanVisits();
        log.info("过期短码清理完成, 私密文本过期置空: {}, 共清理短码 {} 条（expire_at 早于 {}）, 清扫无主明细 {} 条",
                secretSwept, expired.size(), threshold, swept);
    }
}
