package com.smartwash.divination.llm;

import java.util.regex.Pattern;

/**
 * LLM 调用错误 → SSE error 帧映射（纯函数，可单测）。
 *
 * 契约（与 SseBridge 帧格式约定一致）：
 *   - 供应商 HTTP 4xx → SSE code 原样或归并 4xx，message 透传脱敏后的供应商 message
 *   - 供应商 HTTP 5xx / 网络异常 → SSE code 503，固定文案（不暴露供应商细节）
 */
public final class LlmErrorMapping {

    /** 5xx/网络异常的统一对外文案 */
    public static final String SERVICE_UNAVAILABLE_MESSAGE = "解读服务暂不可用，请稍后再试";

    /** 4xx 无供应商 message 时的兜底文案 */
    private static final String CLIENT_ERROR_FALLBACK_MESSAGE = "模型调用被拒绝，请检查模型配置";

    /** 疑似 API Key 模式：sk-xxx */
    private static final Pattern API_KEY_PATTERN = Pattern.compile("sk-[A-Za-z0-9_\\-]{6,}");

    /**
     * GLM/智谱形态密钥：id.secret 点分两段（真实形态约 32 位.16 位字母数字）。
     * 两段阈值取 16+ 避免误伤普通英文短语（如 connection.terminated、authentication.failed）。
     */
    private static final Pattern DOT_SEPARATED_KEY_PATTERN =
            Pattern.compile("[A-Za-z0-9]{16,}\\.[A-Za-z0-9]{16,}");

    /** Bearer token 模式 */
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-]{6,}");

    /** 脱敏后最大长度（SSE data 为单行，避免超长破坏帧） */
    private static final int MAX_MESSAGE_LENGTH = 200;

    private LlmErrorMapping() {
    }

    /**
     * 映射 SSE error 帧的 code：
     *   401/403/429 原样透传（密钥无效/无权限/限流，客户端需要区分处理）；
     *   其余 4xx 归并为 400（参数/配置类错误）；
     *   5xx 与网络异常（httpStatus=0）统一 503。
     */
    public static int mapToSseCode(LlmCallException e) {
        int status = e.getHttpStatus();
        if (status == 401 || status == 403 || status == 429) {
            return status;
        }
        if (e.isClientError()) {
            return 400;
        }
        return 503;
    }

    /**
     * 映射 SSE error 帧的 message：
     *   4xx → 透传脱敏后的供应商 message（无 message 时兜底文案）；
     *   5xx / 网络异常 → 固定文案。
     */
    public static String mapToSseMessage(LlmCallException e) {
        if (e.isClientError()) {
            String msg = e.getProviderMessage();
            return (msg != null && !msg.isBlank()) ? msg : CLIENT_ERROR_FALLBACK_MESSAGE;
        }
        return SERVICE_UNAVAILABLE_MESSAGE;
    }

    /**
     * 脱敏供应商返回信息：掩去疑似密钥/Bearer token、压平换行（SSE 单行帧）、截断超长。
     */
    public static String sanitizeProviderMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = API_KEY_PATTERN.matcher(raw).replaceAll("sk-***");
        cleaned = DOT_SEPARATED_KEY_PATTERN.matcher(cleaned).replaceAll("key-***");
        cleaned = BEARER_PATTERN.matcher(cleaned).replaceAll("Bearer ***");
        cleaned = cleaned.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_MESSAGE_LENGTH
                ? cleaned.substring(0, MAX_MESSAGE_LENGTH) + "…" : cleaned;
    }
}
