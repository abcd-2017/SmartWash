package com.smartwash.divination.service.impl;

import com.smartwash.divination.core.DivinationCore;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.llm.LlmGateway;
import com.smartwash.divination.llm.SseBridge;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivRecordMapper;
import com.smartwash.divination.service.DivSafetyService;
import com.smartwash.divination.service.DivUsageService;
import com.smartwash.divination.service.IDivInterpretationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 解读服务实现：分流→缓存→组装→SSE→落库。
 *
 * 全链路时序：
 *   1. 安全分流（敏感问题拦截）
 *   2. Redis 限流 INCR → 超限 429
 *   3. 查解读缓存（Redis）→ 命中回放
 *   4. 组装 prompt packet（server_chart + 规则事实 + prompt 版本 + RAG）
 *   5. 三层密钥解析 → LlmGateway 路由
 *   6. WebClient 流式调用 → SseBridge 逐 chunk 推流并累积
 *   7. 落库 div_interpretation
 *   8. 异步事实审计（B3 实现）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivInterpretationServiceImpl implements IDivInterpretationService {

    private final DivRecordMapper recordMapper;
    private final DivInterpretationMapper interpretationMapper;
    private final DivinationCore divinationCore;
    private final LlmGateway llmGateway;
    private final SseBridge sseBridge;
    private final DivSafetyService safetyService;
    private final DivUsageService usageService;

    /** 异步推流线程池 */
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Value("${div.llm.limit.interpret-per-user-day:20}")
    private int interpretPerUserDay;

    @Override
    public SseEmitter interpret(Long recordId, String question, Long userId, boolean isFollowup) {
        SseEmitter emitter = new SseEmitter(120_000L); // 120s 超时

        executor.execute(() -> {
            try {
                doInterpret(emitter, recordId, question, userId, isFollowup);
            } catch (Exception e) {
                log.error("解读异常, recordId: {}", recordId, e);
                sseBridge.sendError(emitter, 500, "解读服务异常，请稍后再试");
            }
        });

        // 设置完成/超时回调
        emitter.onCompletion(() -> log.debug("SSE 推送完成, recordId: {}", recordId));
        emitter.onTimeout(() -> log.warn("SSE 推送超时, recordId: {}", recordId));
        emitter.onError(ex -> log.warn("SSE 推送异常, recordId: {}", recordId, ex));

        return emitter;
    }

    private void doInterpret(SseEmitter emitter, Long recordId, String question, Long userId, boolean isFollowup) throws IOException {
        // 1. 查卦例
        DivRecord record = recordMapper.selectById(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            sseBridge.sendError(emitter, 404, "卦例不存在");
            return;
        }

        // 2. 安全分流
        String refusal = safetyService.check(question);
        if (refusal != null) {
            safetyService.recordBlocked(userId, question, record.getMethod(), "keyword");
            sseBridge.sendError(emitter, 403, refusal);
            return;
        }

        // 3. 限流
        if (!usageService.tryConsumeUserLimit(userId, interpretPerUserDay)) {
            sseBridge.sendError(emitter, 429, "今日解读次数已达上限，请明日再来");
            return;
        }

        // 4. 组装 prompt packet（简化版：server_chart + 问题）
        String prompt = buildPrompt(record, question);

        // 5. 解析供应商（简化：使用 bootstrap 兜底）
        LlmGateway.ResolvedProvider resolved;
        try {
            resolved = llmGateway.resolveProvider(null);
        } catch (IllegalStateException e) {
            sseBridge.sendError(emitter, 503, "解读服务暂不可用，请稍后再试");
            return;
        }

        // 6. 流式调用 LLM 并推流（简化实现：模拟流式输出）
        StringBuilder contentBuilder = new StringBuilder();
        long startTime = System.currentTimeMillis();

        // TODO: 接入真实 WebClient 流式调用（B2 后续迭代完善）
        // 当前为占位实现：返回提示文本
        String placeholder = generatePlaceholderInterpretation(record, question);
        for (String chunk : placeholder.split("(?<=\\n)")) {
            sseBridge.sendDelta(emitter, chunk);
            contentBuilder.append(chunk);
            try {
                Thread.sleep(50); // 模拟流式延迟
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // 7. 落库
        long latency = (int) (System.currentTimeMillis() - startTime);
        DivInterpretation interpretation = new DivInterpretation();
        interpretation.setRecordId(recordId);
        interpretation.setUserId(userId);
        interpretation.setPromptVersionId(0L); // TODO: 从缓存取激活版本
        interpretation.setKind(isFollowup ? "followup" : "first");
        interpretation.setQuestion(isFollowup ? question : null);
        interpretation.setProvider(resolved.getProvider().getName());
        interpretation.setModel(resolved.getProvider().getModel());
        interpretation.setKeySource(resolved.getKeySource());
        interpretation.setConfigId(resolved.getConfigId());
        interpretation.setContentMd(contentBuilder.toString());
        interpretation.setTokensIn(0);
        interpretation.setTokensOut(0);
        interpretation.setLatencyMs((int) latency);
        interpretation.setCacheHit(0);
        interpretation.setAuditStatus(0);
        interpretationMapper.insert(interpretation);

        log.info("解读完成, recordId: {}, interpretationId: {}, latency: {}ms",
                recordId, interpretation.getId(), latency);

        // 8. 推送完成帧
        sseBridge.sendDone(emitter, interpretation.getId(), false);
    }

    /**
     * 组装 prompt packet（简化版）。
     * 完整实现应包含：server_chart + 规则事实 + prompt 版本 + RAG top-k 引用。
     */
    private String buildPrompt(DivRecord record, String question) {
        return String.format(
                "术数方法：%s\n问题：%s\n盘面：%s",
                record.getMethod(), question, record.getServerChart()
        );
    }

    /**
     * 生成占位解读文本（真实实现由 WebClient 流式调用 LLM 替代）。
     */
    private String generatePlaceholderInterpretation(DivRecord record, String question) {
        return String.format(
                "【观象台 · %s解读（占位）】\n\n" +
                        "您的问题：%s\n\n" +
                        "服务端已重算权威盘面并完成校验（chart_verified=%s）。\n" +
                        "LLM 解读功能正在接入中，敬请期待。\n\n" +
                        "免责声明：本解读仅供娱乐参考，不构成任何建议。",
                record.getMethod(), question,
                record.getChartVerified() != null && record.getChartVerified() == 1 ? "一致" : "待校验"
        );
    }
}
