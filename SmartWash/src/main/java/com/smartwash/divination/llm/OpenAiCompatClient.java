package com.smartwash.divination.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议 HTTP 客户端（WebClient/Reactor Netty）。
 *
 * 职责：
 *   1. 流式调用 POST {baseUrl}/chat/completions（stream=true + include_usage），
 *      逐事件经 {@link OpenAiSseLineParser} 解析，增量文本实时回调推流；
 *   2. 非流式回退：流式失败（连接失败/超时/5xx/非 SSE 响应）且尚未推出任何增量时，
 *      降级发一次 stream=false 请求取整段文本，回退仅一次、不递归；
 *   3. 连通性试呼：1-token 非流式请求（10s 超时），服务管理端与用户端连通性测试。
 *
 * 安全约定：
 *   - Authorization 仅在请求头携带，日志与异常信息绝不打印/透传 apiKey；
 *   - 供应商错误信息经 {@link LlmErrorMapping#sanitizeProviderMessage} 脱敏后再传播。
 */
@Slf4j
@Component
public class OpenAiCompatClient {

    /** JSON 处理（线程安全） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 连接超时 5s */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 流式读超时 60s（相邻网络读之间的空闲上限，长生成不误伤） */
    private static final Duration STREAM_READ_TIMEOUT = Duration.ofSeconds(60);
    /** 试呼超时 10s */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);
    /** 流式调用整体硬上限（与 SseEmitter 120s 窗口对齐） */
    private static final Duration STREAM_TOTAL_TIMEOUT = Duration.ofSeconds(120);
    /** 解读温度（适中，兼顾稳定与文采） */
    private static final double CHAT_TEMPERATURE = 0.7;

    private final OpenAiSseLineParser parser;
    /** 流式/非流式回退共用（60s 读超时） */
    private final WebClient chatWebClient;
    /** 试呼专用（10s 收紧超时） */
    private final WebClient probeWebClient;

    public OpenAiCompatClient(OpenAiSseLineParser parser) {
        this.parser = parser;
        HttpClient chatHttp = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .responseTimeout(STREAM_READ_TIMEOUT);
        this.chatWebClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(chatHttp))
                .build();
        HttpClient probeHttp = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .responseTimeout(PROBE_TIMEOUT);
        this.probeWebClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(probeHttp))
                .build();
    }

    // ==================== 对外入口 ====================

    /**
     * 流式对话 + 一次非流式回退。
     *
     * @param provider      已解析的供应商配置
     * @param systemPrompt  系统提示词
     * @param userPrompt    用户提示词（prompt packet）
     * @param deltaConsumer 增量文本消费者（回退时仅回调一次，入参为整段文本）
     * @return 调用结果（完整文本 + usage 计量）
     * @throws LlmCallException 流式与回退均失败时抛出（message 已脱敏）
     */
    public LlmChatResult chatWithFallback(OpenAiCompatProvider provider, String systemPrompt,
                                          String userPrompt, Consumer<String> deltaConsumer) {
        AtomicBoolean anyDelta = new AtomicBoolean(false);
        Consumer<String> wrapped = text -> {
            anyDelta.set(true);
            deltaConsumer.accept(text);
        };
        try {
            return doStreamCall(provider, systemPrompt, userPrompt, wrapped);
        } catch (LlmCallException e) {
            // 回退条件：尚未推出任何增量（避免内容重复），且失败为网络/超时/5xx/非 SSE（4xx 重试无意义）
            boolean fallbackEligible = !anyDelta.get() && !e.isClientError();
            if (!fallbackEligible) {
                throw e;
            }
            log.warn("LLM 流式调用失败，降级非流式重试一次, provider: {}, httpStatus: {}, 原因: {}",
                    provider.getName(), e.getHttpStatus(), e.getProviderMessage());
            return doBlockingCall(provider, systemPrompt, userPrompt, deltaConsumer);
        }
    }

    /**
     * 连通性试呼：1-token 非流式请求（10s 超时）。
     *
     * @return true=试呼成功（2xx 且响应为标准 chat.completion 结构）
     */
    public boolean probeConnectivity(String baseUrl, String apiKey, String model) {
        String url = chatCompletionsUrl(baseUrl);
        Map<String, Object> body = buildChatRequestBody(model,
                List.of(new ChatMessage("user", "ping")), false, 0.0, 1);
        try {
            String respBody = probeWebClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(response -> readBodyOrError(response))
                    .block(PROBE_TIMEOUT);
            boolean ok = isSuccessfulCompletionResponse(respBody);
            log.debug("LLM 连通性试呼完成, baseUrl: {}, model: {}, 结果: {}", baseUrl, model, ok);
            return ok;
        } catch (Exception e) {
            // 绝不打印 apiKey：仅记录脱敏原因
            String reason = e instanceof LlmCallException le
                    ? le.getProviderMessage() : e.getClass().getSimpleName();
            log.warn("LLM 连通性试呼失败, baseUrl: {}, model: {}, 原因: {}", baseUrl, model, reason);
            return false;
        }
    }

    // ==================== 请求体与响应判定（纯函数，可单测） ====================

    /**
     * 构造 chat/completions 请求体。
     *
     * @param model       供应商模型标识
     * @param messages    对话消息（system/user）
     * @param stream      是否流式
     * @param temperature 温度
     * @param maxTokens   最大输出 token（可空）
     */
    public static Map<String, Object> buildChatRequestBody(String model, List<ChatMessage> messages,
                                                           boolean stream, Double temperature, Integer maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        if (stream) {
            // OpenAI 兼容协议：末尾追加 usage 统计帧
            body.put("stream_options", Map.of("include_usage", true));
        }
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }
        return body;
    }

    /**
     * 拼接 chat/completions 完整 URL（去除 baseUrl 尾部斜杠）。
     */
    public static String chatCompletionsUrl(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    /**
     * 试呼响应判定：2xx 且含非空 choices[0].message 即视为成功。
     */
    public static boolean isSuccessfulCompletionResponse(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode root = JSON.readTree(json);
            JsonNode choices = root.get("choices");
            return choices != null && choices.isArray() && !choices.isEmpty()
                    && choices.get(0).has("message");
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 流式调用：逐事件解析并实时回调 deltaConsumer，累积完整文本与 usage。
     */
    private LlmChatResult doStreamCall(OpenAiCompatProvider provider, String systemPrompt,
                                       String userPrompt, Consumer<String> deltaConsumer) {
        String url = chatCompletionsUrl(provider.getBaseUrl());
        Map<String, Object> body = buildChatRequestBody(provider.getModel(),
                List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", userPrompt)),
                true, CHAT_TEMPERATURE, null);

        StringBuilder content = new StringBuilder();
        UsageHolder usage = new UsageHolder();
        try {
            chatWebClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(response -> consumeStreamResponse(response, deltaConsumer, content, usage))
                    .block(STREAM_TOTAL_TIMEOUT);
        } catch (Exception e) {
            throw translate(e);
        }
        // 兜底：部分供应商对 stream:true 返回 event-stream 头但整体一个 JSON（零增量零 usage）
        if (content.length() == 0 && usage.promptTokens == 0 && usage.completionTokens == 0) {
            throw LlmCallException.network("流式响应未产生任何内容（疑似非 SSE 响应）");
        }
        return new LlmChatResult(content.toString(), usage.promptTokens, usage.completionTokens);
    }

    /**
     * 消费 SSE 流式响应：状态/Content-Type 校验 → 逐事件解析 → 实时回调推流。
     * [DONE] 帧后主动断开上游（部分供应商不发 FIN）。
     */
    private Mono<Void> consumeStreamResponse(ClientResponse response, Consumer<String> deltaConsumer,
                                             StringBuilder content, UsageHolder usage) {
        if (response.statusCode().isError()) {
            return response.bodyToMono(String.class).defaultIfEmpty("")
                    .flatMap(b -> Mono.error(toLlmException(response.statusCode().value(), b)));
        }
        MediaType contentType = response.headers().contentType().orElse(null);
        if (contentType == null || !contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)) {
            log.debug("供应商未返回 SSE 流式响应, contentType: {}", contentType);
            return response.bodyToMono(String.class).defaultIfEmpty("")
                    .flatMap(b -> Mono.error(LlmCallException.network("供应商未返回 SSE 流式响应")));
        }
        return response.bodyToFlux(String.class)
                .map(parser::parse)
                .takeUntil(event -> event.getType() == OpenAiSseLineParser.Event.Type.DONE)
                .doOnNext(event -> handleStreamEvent(event, deltaConsumer, content, usage))
                .then();
    }

    /**
     * 处理单个解析事件：增量推流 / usage 累积 / 错误中断。
     */
    private void handleStreamEvent(OpenAiSseLineParser.Event event, Consumer<String> deltaConsumer,
                                   StringBuilder content, UsageHolder usage) {
        switch (event.getType()) {
            case DELTA -> {
                content.append(event.getText());
                deltaConsumer.accept(event.getText());
                if (event.hasUsage()) {
                    usage.fill(event);
                }
            }
            case USAGE -> usage.fill(event);
            case ERROR -> {
                Integer code = event.getErrorCode();
                int httpStatus = (code != null && code >= 400) ? code : 500;
                throw new LlmCallException(httpStatus,
                        LlmErrorMapping.sanitizeProviderMessage(event.getErrorMessage()));
            }
            default -> {
                // DONE / IGNORE：[DONE] 由 takeUntil 处理，其余帧忽略不中断流
            }
        }
    }

    /**
     * 非流式回退调用：一次 stream=false 请求取整段文本，经 deltaConsumer 一次性推流。
     * 仅被 {@link #chatWithFallback} 调用一次，不再递归。
     */
    private LlmChatResult doBlockingCall(OpenAiCompatProvider provider, String systemPrompt,
                                         String userPrompt, Consumer<String> deltaConsumer) {
        String url = chatCompletionsUrl(provider.getBaseUrl());
        Map<String, Object> body = buildChatRequestBody(provider.getModel(),
                List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", userPrompt)),
                false, CHAT_TEMPERATURE, null);
        String respBody;
        try {
            respBody = chatWebClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(this::readBodyOrError)
                    .block(STREAM_TOTAL_TIMEOUT);
        } catch (Exception e) {
            throw translate(e);
        }
        JsonNode root;
        try {
            root = JSON.readTree(respBody);
        } catch (Exception e) {
            throw LlmCallException.network("非流式响应解析失败（响应非 JSON）");
        }
        JsonNode error = root.get("error");
        if (error != null && error.isObject()) {
            throw new LlmCallException(500,
                    LlmErrorMapping.sanitizeProviderMessage(
                            error.hasNonNull("message") ? error.get("message").asText() : null));
        }
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        int tokensIn = root.path("usage").path("prompt_tokens").asInt(0);
        int tokensOut = root.path("usage").path("completion_tokens").asInt(0);
        // 整段文本一次性推流
        deltaConsumer.accept(text);
        return new LlmChatResult(text, tokensIn, tokensOut);
    }

    /**
     * 读取响应体：错误状态抛 LlmCallException（提取 error.message 并脱敏），成功返回原始体。
     */
    private Mono<String> readBodyOrError(ClientResponse response) {
        if (response.statusCode().isError()) {
            return response.bodyToMono(String.class).defaultIfEmpty("")
                    .flatMap(b -> Mono.error(toLlmException(response.statusCode().value(), b)));
        }
        return response.bodyToMono(String.class).defaultIfEmpty("");
    }

    /**
     * 供应商错误响应体 → LlmCallException：提取 error.message，脱敏（绝不透出 apiKey）。
     */
    private LlmCallException toLlmException(int httpStatus, String body) {
        String providerMessage = null;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = JSON.readTree(body);
                JsonNode error = root.get("error");
                if (error != null && error.isObject() && error.hasNonNull("message")) {
                    providerMessage = error.get("message").asText();
                } else {
                    providerMessage = body;
                }
            } catch (Exception e) {
                providerMessage = body;
            }
        }
        return new LlmCallException(httpStatus, LlmErrorMapping.sanitizeProviderMessage(providerMessage));
    }

    /**
     * Reactor/网络异常 → LlmCallException（网络类，httpStatus=0），message 脱敏截断。
     */
    private LlmCallException translate(Exception e) {
        if (e instanceof LlmCallException le) {
            return le;
        }
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        log.debug("LLM 请求网络异常, 类型: {}, 原因: {}", e.getClass().getSimpleName(), reason);
        return LlmCallException.network(LlmErrorMapping.sanitizeProviderMessage("LLM 请求异常: " + reason));
    }

    /**
     * usage 累积容器（供应商无 usage 帧时保持 0，不阻塞主流程）。
     */
    private static class UsageHolder {
        int promptTokens;
        int completionTokens;

        void fill(OpenAiSseLineParser.Event event) {
            if (event.getPromptTokens() != null) {
                promptTokens = event.getPromptTokens();
            }
            if (event.getCompletionTokens() != null) {
                completionTokens = event.getCompletionTokens();
            }
        }
    }

    /**
     * 对话消息（OpenAI 兼容协议）。
     */
    public record ChatMessage(String role, String content) {
    }
}
