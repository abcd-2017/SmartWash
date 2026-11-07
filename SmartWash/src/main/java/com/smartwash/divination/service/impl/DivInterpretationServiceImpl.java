package com.smartwash.divination.service.impl;

import com.smartwash.divination.core.DivinationCore;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivPlatformSetting;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.llm.LlmCallException;
import com.smartwash.divination.llm.LlmChatResult;
import com.smartwash.divination.llm.LlmErrorMapping;
import com.smartwash.divination.llm.LlmGateway;
import com.smartwash.divination.llm.OpenAiCompatClient;
import com.smartwash.divination.llm.OpenAiCompatProvider;
import com.smartwash.divination.llm.SseBridge;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivRecordMapper;
import com.smartwash.divination.service.DivModelConfigService;
import com.smartwash.divination.service.DivPlatformSettingService;
import com.smartwash.divination.service.DivSafetyService;
import com.smartwash.divination.service.DivUsageService;
import com.smartwash.divination.service.DivUserApiConfigService;
import com.smartwash.divination.service.IDivInterpretationService;
import com.smartwash.config.MdcTaskDecorator;
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
 *   6. OpenAiCompatClient 流式调用 → SseBridge 逐 chunk 推流并累积（失败一次非流式回退）
 *   7. 落库 div_interpretation（含 usage 计量）
 *   8. 异步事实审计（B3 实现）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivInterpretationServiceImpl implements IDivInterpretationService {

    /** 解读系统提示词（本票简化版；完整 prompt 工程由 PromptAssembler 票实现） */
    private static final String INTERPRET_SYSTEM_PROMPT = "你是「观象台」术数解读助手。基于给定的术数方法、问题与盘面，"
            + "用简体中文 Markdown 输出结构化解读，结论谨慎、就事论事；"
            + "禁止医疗/法律/投资建议；结尾附免责声明：本解读仅供娱乐参考，不构成任何建议。";

    private final DivRecordMapper recordMapper;
    private final DivInterpretationMapper interpretationMapper;
    private final DivinationCore divinationCore;
    private final LlmGateway llmGateway;
    private final SseBridge sseBridge;
    private final DivSafetyService safetyService;
    private final DivUsageService usageService;
    private final OpenAiCompatClient llmClient;
    private final DivUserApiConfigService userApiConfigService;
    private final DivPlatformSettingService platformSettingService;
    private final DivModelConfigService modelConfigService;

    /** MDC 装饰器：自建线程池任务提交时包装，向异步线程传播 traceId */
    private final MdcTaskDecorator mdcTaskDecorator;

    /** 异步推流线程池 */
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /** 用户每日解读次数兜底限额：仅当 div_platform_setting 对应限额缺失时使用（主路径以 DB 为准） */
    @Value("${div.llm.limit.interpret-per-user-day:20}")
    private int interpretPerUserDay;

    @Override
    public SseEmitter interpret(Long recordId, String question, Long userId, boolean isFollowup) {
        SseEmitter emitter = new SseEmitter(120_000L); // 120s 超时

        // 经 MdcTaskDecorator 包装，把当前请求的 traceId 传播到推流线程，日志可跨线程串联
        executor.execute(mdcTaskDecorator.decorate(() -> {
            try {
                doInterpret(emitter, recordId, question, userId, isFollowup);
            } catch (Exception e) {
                log.error("解读异常, recordId: {}", recordId, e);
                sseBridge.sendError(emitter, 500, "解读服务异常，请稍后再试");
            }
        }));

        // 设置完成/超时回调
        emitter.onCompletion(() -> log.debug("SSE 推送完成, recordId: {}", recordId));
        emitter.onTimeout(() -> log.warn("SSE 推送超时, recordId: {}", recordId));
        emitter.onError(ex -> log.warn("SSE 推送异常, recordId: {}", recordId, ex));

        return emitter;
    }

    /** 包级可见便于单测直调（异步线程池外同步执行）；对外入口为 {@link #interpret} */
    void doInterpret(SseEmitter emitter, Long recordId, String question, Long userId, boolean isFollowup) throws IOException {
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

        // 3. 解析供应商（先解析再限额：BYOK 与平台 key 的日限额数值不同，限额需按解析出的 keySource 区分）
        DivPlatformSetting setting = platformSettingService.getSetting();
        OpenAiCompatProvider userProvider = null;
        if (setting.getByokEnabled() != null && setting.getByokEnabled() == 1) {
            // 平台开启 BYOK 才查用户配置（enabled=1 且 verified=1），关闭时不查不传
            userProvider = userApiConfigService.resolveUserProvider(userId);
        }
        LlmGateway.ResolvedProvider resolved;
        try {
            // 平台默认模型以 Supplier 懒加载：仅 BYOK 未配置/未启用/熔断时才查库解密
            resolved = llmGateway.resolveProvider(userProvider,
                    () -> modelConfigService.resolvePlatformDefaultProvider());
        } catch (IllegalStateException e) {
            // 供应商解析失败 = 服务降级（SSE 返回 503），warn 留痕便于排查密钥/平台配置问题
            log.warn("LLM 供应商解析失败，SSE 降级返回 503, recordId: {}, userId: {}, 原因: {}",
                    recordId, userId, e.getMessage());
            sseBridge.sendError(emitter, 503, "解读服务暂不可用，请稍后再试");
            return;
        }

        // 4. 限额：按 keySource 取对应日限额（BYOK→byok_daily_limit，平台 key→platform_daily_limit）
        int dailyLimit = resolveDailyLimit(setting, resolved.getKeySource());
        if (!usageService.tryConsumeUserLimit(userId, dailyLimit)) {
            sseBridge.sendError(emitter, 429, "今日解读次数已达上限，请明日再来");
            return;
        }

        // 5. 组装 prompt packet（简化版：server_chart + 问题）
        String prompt = buildPrompt(record, question);

        // 6. 流式调用 LLM 并推流（真实调用，失败且未推出增量时由客户端做一次非流式回退）
        OpenAiCompatProvider provider = resolved.getProvider();
        long startTime = System.currentTimeMillis();
        LlmChatResult llmResult = null;
        LlmCallException callError = null;
        boolean clientGone = false;
        try {
            llmResult = llmClient.chatWithFallback(provider, INTERPRET_SYSTEM_PROMPT, prompt,
                    chunk -> sseBridge.sendDelta(emitter, chunk));
        } catch (LlmCallException e) {
            callError = e;
        } catch (IllegalStateException e) {
            // SSE 推流中客户端已断开（emitter 已完成）——供应商链路此前正常推流，不计供应商失败
            clientGone = true;
            log.debug("客户端已断开，解读推流中止, recordId: {}, userId: {}", recordId, userId);
        }

        // 熔断统计：无论成败都记录（客户端主动断开时供应商本身无失败，记成功）
        llmGateway.recordResult(provider.getName(), callError == null || clientGone);

        if (callError != null) {
            // 7. 错误映射：4xx 透传脱敏 message；5xx/网络异常 → 503 固定文案
            int sseCode = LlmErrorMapping.mapToSseCode(callError);
            String sseMsg = LlmErrorMapping.mapToSseMessage(callError);
            log.warn("LLM 调用失败, recordId: {}, provider: {}, httpStatus: {}, sseCode: {}",
                    recordId, provider.getName(), callError.getHttpStatus(), sseCode);
            sseBridge.sendError(emitter, sseCode, sseMsg);
            return;
        }
        if (clientGone) {
            return;
        }

        // 8. 落库（usage 计量：供应商未返回 usage 时计 0，不阻塞主流程）
        long latency = System.currentTimeMillis() - startTime;
        DivInterpretation interpretation = new DivInterpretation();
        interpretation.setRecordId(recordId);
        interpretation.setUserId(userId);
        interpretation.setPromptVersionId(0L); // TODO: 从缓存取激活版本
        interpretation.setKind(isFollowup ? "followup" : "first");
        interpretation.setQuestion(isFollowup ? question : null);
        interpretation.setProvider(provider.getName());
        interpretation.setModel(provider.getModel());
        interpretation.setKeySource(resolved.getKeySource());
        interpretation.setConfigId(resolved.getConfigId());
        interpretation.setContentMd(llmResult.getContent());
        interpretation.setTokensIn(llmResult.getTokensIn());
        interpretation.setTokensOut(llmResult.getTokensOut());
        interpretation.setLatencyMs((int) latency);
        interpretation.setCacheHit(0);
        interpretation.setAuditStatus(0);
        interpretationMapper.insert(interpretation);

        log.info("解读完成, recordId: {}, interpretationId: {}, provider: {}, tokensIn: {}, tokensOut: {}, latency: {}ms",
                recordId, interpretation.getId(), provider.getName(),
                llmResult.getTokensIn(), llmResult.getTokensOut(), latency);

        // 9. 推送完成帧
        sseBridge.sendDone(emitter, interpretation.getId(), false);
    }

    /**
     * 按密钥来源解析用户每日解读限额。
     *
     * <p>BYOK 请求取 {@code div_platform_setting.byok_daily_limit}，
     * 平台 key 请求取 {@code platform_daily_limit}；
     * DB 单例尚未初始化对应字段时兜底 yml 配置（div.llm.limit.interpret-per-user-day）。</p>
     *
     * @param setting   平台设置（单例）
     * @param keySource 密钥来源（user=BYOK，platform=平台 key/bootstrap）
     * @return 每日限额
     */
    private int resolveDailyLimit(DivPlatformSetting setting, String keySource) {
        boolean byokRequest = "user".equals(keySource);
        Integer limit = byokRequest ? setting.getByokDailyLimit() : setting.getPlatformDailyLimit();
        if (limit == null || limit <= 0) {
            log.debug("平台设置未配置{}日限额，兜底 yml 默认值", byokRequest ? "BYOK" : "平台");
            return interpretPerUserDay;
        }
        return limit;
    }

    /**
     * 组装 prompt packet（简化版，本票保留；完整实现由 prompt 票完成）。
     * 完整实现应包含：server_chart + 规则事实 + prompt 版本 + RAG top-k 引用。
     */
    private String buildPrompt(DivRecord record, String question) {
        return String.format(
                "术数方法：%s\n问题：%s\n盘面：%s",
                record.getMethod(), question, record.getServerChart()
        );
    }
}
