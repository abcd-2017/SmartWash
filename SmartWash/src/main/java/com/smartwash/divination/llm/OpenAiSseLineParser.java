package com.smartwash.divination.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容 SSE 行解析器（纯函数，可单测）。
 *
 * 输入：一行 SSE data 载荷（兼容裸载荷与带 "data:" 前缀两种形态）；
 * 输出：解析事件（增量文本 / usage / 结束 / 错误 / 忽略）。
 *
 * 解析规则：
 *   - "data: [DONE]"                     → DONE
 *   - data 载荷含 choices[0].delta.content → DELTA（同帧携带 usage 时一并带出）
 *   - data 载荷含 usage（include_usage 末尾统计帧，choices 为空）→ USAGE
 *   - data 载荷含 error（部分兼容供应商以 200 + error 帧报错）→ ERROR
 *   - 注释行/空行/脏数据/其余帧           → IGNORE（不中断流）
 */
@Slf4j
@Component
public class OpenAiSseLineParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析单行 SSE 数据。
     */
    public Event parse(String line) {
        if (line == null) {
            return Event.ignore();
        }
        String data = line.trim();
        if (data.isEmpty()) {
            return Event.ignore();
        }
        // SSE 注释/心跳行（如 ": keep-alive"）
        if (data.startsWith(":")) {
            return Event.ignore();
        }
        // 兼容裸载荷与 "data:" 前缀两种形态
        if (data.regionMatches(true, 0, "data:", 0, 5)) {
            data = data.substring(5).trim();
        } else if (data.startsWith("event:") || data.startsWith("id:") || data.startsWith("retry:")) {
            return Event.ignore();
        }
        if (data.isEmpty()) {
            return Event.ignore();
        }
        if ("[DONE]".equalsIgnoreCase(data)) {
            return Event.done();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(data);
        } catch (Exception e) {
            log.debug("SSE 行解析失败（脏数据忽略）: {}", abbreviate(data));
            return Event.ignore();
        }
        if (!root.isObject()) {
            return Event.ignore();
        }
        // 错误帧
        JsonNode error = root.get("error");
        if (error != null && error.isObject()) {
            Integer code = error.hasNonNull("code") && error.get("code").isNumber()
                    ? error.get("code").asInt() : null;
            String message = error.hasNonNull("message") ? error.get("message").asText() : "供应商返回错误";
            return Event.error(code, message);
        }
        // 增量文本：choices[0].delta.content
        String deltaText = null;
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode delta = choices.get(0).get("delta");
            if (delta != null && delta.hasNonNull("content")) {
                deltaText = delta.get("content").asText();
            }
        }
        // usage 统计帧（stream_options.include_usage=true 时末尾追加，choices 为空数组）
        Integer promptTokens = null;
        Integer completionTokens = null;
        JsonNode usage = root.get("usage");
        if (usage != null && usage.isObject()) {
            if (usage.hasNonNull("prompt_tokens")) {
                promptTokens = usage.get("prompt_tokens").asInt();
            }
            if (usage.hasNonNull("completion_tokens")) {
                completionTokens = usage.get("completion_tokens").asInt();
            }
        }
        if (deltaText != null) {
            return Event.delta(deltaText, promptTokens, completionTokens);
        }
        if (promptTokens != null || completionTokens != null) {
            return Event.usage(promptTokens, completionTokens);
        }
        return Event.ignore();
    }

    /** 日志截断，避免脏数据刷屏 */
    private static String abbreviate(String s) {
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }

    /**
     * 解析事件。
     */
    @Getter
    public static class Event {

        public enum Type { DELTA, USAGE, DONE, ERROR, IGNORE }

        private final Type type;
        /** DELTA 时的增量文本 */
        private final String text;
        /** usage：输入 token 数（帧内携带时非空） */
        private final Integer promptTokens;
        /** usage：输出 token 数（帧内携带时非空） */
        private final Integer completionTokens;
        /** ERROR 时的错误码（部分供应商为字符串型 code，此时为 null） */
        private final Integer errorCode;
        /** ERROR 时的错误信息 */
        private final String errorMessage;

        private Event(Type type, String text, Integer promptTokens, Integer completionTokens,
                      Integer errorCode, String errorMessage) {
            this.type = type;
            this.text = text;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        static Event delta(String text, Integer promptTokens, Integer completionTokens) {
            return new Event(Type.DELTA, text, promptTokens, completionTokens, null, null);
        }

        static Event usage(Integer promptTokens, Integer completionTokens) {
            return new Event(Type.USAGE, null, promptTokens, completionTokens, null, null);
        }

        static Event done() {
            return new Event(Type.DONE, null, null, null, null, null);
        }

        static Event error(Integer code, String message) {
            return new Event(Type.ERROR, null, null, null, code, message);
        }

        static Event ignore() {
            return new Event(Type.IGNORE, null, null, null, null, null);
        }

        /** 本帧是否携带 usage 统计 */
        public boolean hasUsage() {
            return promptTokens != null || completionTokens != null;
        }
    }
}
