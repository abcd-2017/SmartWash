package com.smartwash.toolbox.delivery.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.toolbox.delivery.service.ToolboxDeliveryService;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 过期投递会话定时清理任务。
 * 失效判断全部在读取路径完成（进入/上传/列表/取走/内容先判 expire_at），本任务只做物理清理：
 * 每 5 分钟扫 expire_at 已过 10 分钟的投递会话（content_type=6），按「先删 MinIO 前缀对象 →
 * 删条目 → 删会话」次序回收（防孤儿对象）；滞留窗口以分钟计，对齐投递「无痕」承诺。
 * 单实例部署无需分布式锁；多实例重复清理为幂等删除，无副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolboxDeliveryCleaner {

    /** 会话主表 content_type：6 设备投递 */
    private static final int CONTENT_TYPE_DELIVERY = 6;
    /** 过期宽限期：expire_at 已过 10 分钟才清理（留出正在进行的下载收尾窗口） */
    private static final int RETENTION_MINUTES = 10;

    private final ToolboxShortCodeService toolboxShortCodeService;
    private final ToolboxDeliveryService toolboxDeliveryService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void cleanExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(RETENTION_MINUTES);
        LambdaQueryWrapper<ToolboxShortCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolboxShortCode::getContentType, CONTENT_TYPE_DELIVERY)
                .isNotNull(ToolboxShortCode::getExpireAt)
                .lt(ToolboxShortCode::getExpireAt, threshold);
        List<ToolboxShortCode> expired = toolboxShortCodeService.list(wrapper);
        if (expired.isEmpty()) {
            log.debug("无过期投递会话需要清理");
            return;
        }
        toolboxDeliveryService.purgeExpiredSessions(expired);
        log.info("过期投递会话扫描完成, 共清理 {} 个（expire_at 早于 {}）", expired.size(), threshold);
    }
}
