package com.smartwash.toolbox.listener;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.entity.ToolboxShortVisit;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 短码点击统计监听器（异步落库，不阻塞跳转）。
 * 统计卫生（对齐 shlink）：HEAD 请求与爬虫 UA 不计数；IP 只存 MD5 前 8 位摘要；
 * 主表 click_count 用 setSql 原子自增，防并发丢失更新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortCodeVisitedListener {

    private static final Pattern BOT_UA_PATTERN = Pattern.compile(".*(bot|spider|crawler).*");

    private final ToolboxShortVisitService toolboxShortVisitService;
    private final ToolboxShortCodeService toolboxShortCodeService;

    @Async
    @EventListener
    public void onVisited(ShortCodeVisitedEvent event) {
        try {
            doRecord(event);
        } catch (Exception e) {
            // 统计失败只记日志，不影响已返回的跳转
            log.error("短码点击统计落库失败, code: {}", event.getCode(), e);
        }
    }

    private void doRecord(ShortCodeVisitedEvent event) {
        // HEAD 不计数
        if ("HEAD".equalsIgnoreCase(event.getMethod())) {
            return;
        }
        // 爬虫与空 UA 不计数
        String ua = event.getUserAgent();
        if (ua == null || BOT_UA_PATTERN.matcher(ua.toLowerCase()).matches()) {
            return;
        }
        ToolboxShortVisit visit = new ToolboxShortVisit()
                .setCode(event.getCode())
                .setIpHash(DigestUtil.md5Hex(event.getClientIp()).substring(0, 8))
                .setUserAgent(StrUtil.sub(ua, 0, 512))
                .setReferer(StrUtil.sub(event.getReferer(), 0, 512));
        toolboxShortVisitService.save(visit);
        // 主表原子自增，防并发丢失更新
        toolboxShortCodeService.update(new UpdateWrapper<ToolboxShortCode>()
                .eq("code", event.getCode())
                .setSql("click_count = click_count + 1"));
        log.debug("短码点击统计完成, code: {}, ipHash: {}", event.getCode(), visit.getIpHash());
    }
}
