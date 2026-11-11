package com.smartwash.divination.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartwash.divination.core.DivinationCore;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivPlatformSetting;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.llm.ClientGoneException;
import com.smartwash.divination.llm.LlmCallException;
import com.smartwash.divination.llm.LlmChatResult;
import com.smartwash.divination.llm.LlmErrorMapping;
import com.smartwash.divination.llm.LlmGateway;
import com.smartwash.divination.llm.NoProviderConfiguredException;
import com.smartwash.divination.llm.OpenAiCompatClient;
import com.smartwash.divination.llm.OpenAiCompatProvider;
import com.smartwash.divination.llm.SseBridge;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivRecordMapper;
import com.smartwash.divination.prompt.PromptAssembler;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 解读服务实现：分流→缓存→组装→SSE→落库。
 *
 * 全链路时序：
 *   1. 安全分流（敏感问题拦截）
 *   2. Redis 限流 INCR → 超限 429
 *   3. 查解读缓存（Redis）→ 命中回放（预留，未实现；当前每次均真实调用 LLM）
 *   4. 组装 prompt packet（激活 prompt 版本 + server_chart + 领域方法；追问时拼接历史解读上下文）
 *   5. 三层密钥解析 → LlmGateway 路由
 *   6. OpenAiCompatClient 流式调用 → SseBridge 逐 chunk 推流并累积（失败一次非流式回退）
 *   7. 落库 div_interpretation（含 usage 计量与 prompt_version_id）
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
    private final OpenAiCompatClient llmClient;
    private final DivUserApiConfigService userApiConfigService;
    private final DivPlatformSettingService platformSettingService;
    private final DivModelConfigService modelConfigService;
    private final PromptAssembler promptAssembler;

    /** MDC 装饰器：自建线程池任务提交时包装，向异步线程传播 request_id */
    private final MdcTaskDecorator mdcTaskDecorator;

    /** 异步推流线程池 */
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /** 用户每日解读次数兜底限额：仅当 div_platform_setting 对应限额缺失时使用（主路径以 DB 为准） */
    @Value("${div.llm.limit.interpret-per-user-day:20}")
    private int interpretPerUserDay;

    @Override
    public SseEmitter interpret(Long recordId, String question, Long userId, boolean isFollowup) {
        SseEmitter emitter = new SseEmitter(120_000L); // 120s 超时

        // 经 MdcTaskDecorator 包装，把当前请求的 request_id 传播到推流线程，日志可跨线程串联
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

        // 2. 安全分流：question 为空（首次解读空 body）时回退卦例自身问题，与 buildPacket 的
        //    effectiveQuestion 同口径——防止空 body 请求绕过关键词拦截
        String effectiveQuestion = question != null ? question : record.getQuestion();
        String refusal = safetyService.check(effectiveQuestion);
        if (refusal != null) {
            safetyService.recordBlocked(userId, effectiveQuestion, record.getMethod(), "keyword");
            sseBridge.sendError(emitter, 403, refusal);
            return;
        }

        // 3. 解析供应商（平台零兜底：只消费用户 BYOK 配置）
        DivPlatformSetting setting = platformSettingService.getSetting();
        if (setting.getDivUseEnabled() == null || setting.getDivUseEnabled() != 1) {
            // 功能总开关关闭：403，客户端提示平台未开放
            log.info("观星台 AI 解读总开关关闭，SSE 403, recordId: {}, userId: {}", recordId, userId);
            sseBridge.sendError(emitter, 403, "观星台 AI 解读暂未开放，请联系管理员");
            return;
        }
        OpenAiCompatProvider userProvider = userApiConfigService.resolveUserProvider(userId);
        if (userProvider == null) {
            // 平台零兜底：用户未配置模型 → SSE 428（Precondition Required），
            // 客户端按该码路由拦截跳设置页（区别于 503 服务故障）
            log.info("用户未配置解读模型，SSE 428 引导设置, recordId: {}, userId: {}", recordId, userId);
            sseBridge.sendError(emitter, 428, "尚未配置解读模型，请先在设置中选择模型并填写 API Key");
            return;
        }
        LlmGateway.ResolvedProvider resolved;
        try {
            resolved = llmGateway.resolveProvider(userProvider);
        } catch (NoProviderConfiguredException e) {
            // 防御：userProvider 非空不应触发；触发则同 428 语义
            log.warn("供应商解析为未配置（防御分支）, recordId: {}, userId: {}", recordId, userId);
            sseBridge.sendError(emitter, 428, "尚未配置解读模型，请先在设置中选择模型并填写 API Key");
            return;
        } catch (IllegalStateException e) {
            // 配置存在但供应商熔断中 = 服务降级（SSE 返回 503）
            log.warn("LLM 供应商熔断中，SSE 降级返回 503, recordId: {}, userId: {}, 原因: {}",
                    recordId, userId, e.getMessage());
            sseBridge.sendError(emitter, 503, "解读服务暂不可用，请稍后再试");
            return;
        }

        // 4. 限额：每用户每日解读次数（平台零兜底后仅 BYOK 一种来源）
        int dailyLimit = resolveDailyLimit(setting);
        if (!usageService.tryConsumeUserLimit(userId, dailyLimit)) {
            sseBridge.sendError(emitter, 429, "今日解读次数已达上限，请明日再来");
            return;
        }

        // 5. 组装 prompt packet（激活 prompt 版本 + 完整 serverChart + 领域方法；追问时拼接历史解读上下文）
        Map<String, String> packet = buildPacket(record, question, isFollowup);
        String systemPrompt = packet.get("system");
        String userPrompt = packet.get("user");
        Long promptVersionId = parseVersionId(packet.get("promptVersionId"));
        // 日志只打长度摘要，不打完整 prompt（含盘面与解读内容，避免刷日志）
        log.debug("prompt 组装完成, recordId: {}, kind: {}, promptVersionId: {}, userPromptChars: {}",
                recordId, isFollowup ? "followup" : "first", promptVersionId, userPrompt.length());

        // 6. 流式调用 LLM 并推流（真实调用，失败且未推出增量时由客户端做一次非流式回退）
        OpenAiCompatProvider provider = resolved.getProvider();
        long startTime = System.currentTimeMillis();
        LlmChatResult llmResult = null;
        LlmCallException callError = null;
        boolean clientGone = false;
        try {
            llmResult = llmClient.chatWithFallback(provider, systemPrompt, userPrompt,
                    chunk -> sseBridge.sendDelta(emitter, chunk));
        } catch (LlmCallException e) {
            callError = e;
        } catch (ClientGoneException e) {
            // 客户端断连（emitter 已完成/已提交后仍推送）：非供应商失败——
            // 不记熔断窗口、不 sendError（sendError 对已完成 emitter 会二次抛 ISE），静默收尾
            clientGone = true;
            log.debug("客户端已断开，解读推流中止, recordId: {}, userId: {}", recordId, userId);
        }

        // 熔断统计：仅记录有确定结果的调用；客户端断连时供应商结果未知，不进窗口（避免污染错误率）
        if (!clientGone) {
            llmGateway.recordResult(provider, callError == null);
        }

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
        interpretation.setPromptVersionId(promptVersionId); // 无激活版本走兜底时落 0
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
     * 解析用户每日解读次数限额（平台零兜底后仅 BYOK 一种来源）。
     *
     * <p>DB 单例尚未初始化对应字段时兜底 yml 配置（div.llm.limit.interpret-per-user-day）。</p>
     *
     * @param setting 平台设置（单例）
     * @return 每日限额
     */
    private int resolveDailyLimit(DivPlatformSetting setting) {
        Integer limit = setting.getByokDailyLimit();
        if (limit == null || limit <= 0) {
            log.debug("平台设置未配置每日限额，兜底 yml 默认值: {}", interpretPerUserDay);
            return interpretPerUserDay;
        }
        return limit;
    }

    /**
     * 组装 prompt packet：首次解读与追问分别委托 {@link PromptAssembler}。
     *
     * <p>追问（isFollowup=true）时按 recordId 查 div_interpretation 历史解读
     * （首轮 + 历次追问，按时间升序）拼进 user prompt；首次解读 question 入参可为 null
     * （对应落库语义 interpretation.question=null），此时 prompt 使用卦例自身问题。</p>
     *
     * @param record     卦例（已校验归属）
     * @param question   本次问题（首次解读时与卦例问题相同或为 null，追问时为子问题）
     * @param isFollowup 是否追问
     * @return prompt packet（system/user/method/promptVersionId，见 PromptAssembler 键约定）
     */
    private Map<String, String> buildPacket(DivRecord record, String question, boolean isFollowup) {
        if (!isFollowup) {
            String effectiveQuestion = question != null ? question : record.getQuestion();
            return promptAssembler.assemble(record.getMethod(), record.getServerChart(), effectiveQuestion);
        }
        // 追问：查该卦例此前全部解读记录（含首轮解读与历次追问），按时间升序供拼上下文
        QueryWrapper<DivInterpretation> wrapper = new QueryWrapper<>();
        wrapper.eq("record_id", record.getId())
                .orderByAsc("created_at")
                .orderByAsc("id");
        List<DivInterpretation> history = interpretationMapper.selectList(wrapper);
        return promptAssembler.assembleFollowup(record.getMethod(), record.getServerChart(), question, history);
    }

    /**
     * 从 packet 取激活 prompt 版本 id；无激活版本走兜底时 packet 不含该键，落 0（保持现状语义）。
     */
    private Long parseVersionId(String versionIdStr) {
        return versionIdStr != null && !versionIdStr.isEmpty() ? Long.valueOf(versionIdStr) : 0L;
    }
}
