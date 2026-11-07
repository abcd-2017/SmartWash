package com.smartwash.divination.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Flux&lt;String&gt; → SseEmitter 桥接器。
 * 将 WebClient 流式响应的每个 chunk 桥接到 SseEmitter 推给客户端。
 *
 * SSE 事件契约：
 *   event: delta   data: {"text":"…流式片段…"}
 *   event: done    data: {"interpretationId":123,"cached":false}
 *   event: error   data: {"code":500,"message":"供应商暂时不可用，已降级"}
 *
 * 断连契约：客户端断开后 emitter 已完成/已提交，再 send 会抛 IllegalStateException。
 *   - sendDelta：转抛 {@link ClientGoneException}，让供应商流中断并静默收尾
 *     （不 completeWithError——emitter 已处于终态，且断连不是供应商失败）；
 *   - sendDone/sendError：静默返回（收尾阶段再抛异常只会逃逸到线程池兜底）。
 */
@Slf4j
@Component
public class SseBridge {

    /**
     * 推送 delta 文本帧。
     */
    public void sendDelta(SseEmitter emitter, String text) {
        try {
            emitter.send(SseEmitter.event()
                    .name("delta")
                    .data("{\"text\":\"" + escapeJson(text) + "\"}", MediaType.TEXT_EVENT_STREAM));
        } catch (IOException e) {
            log.warn("SSE 推送 delta 失败（客户端可能已断开）", e);
            completeQuietlyWithError(emitter, e);
        } catch (IllegalStateException e) {
            // emitter 已完成/已提交（客户端断开后的下一个 delta）：转抛专用异常，
            // 上层中断供应商流并静默收尾，避免被包装成 LlmCallException 污染熔断窗口
            throw new ClientGoneException("SseEmitter 已完成，客户端已断开", e);
        }
    }

    /**
     * 推送完成帧。
     */
    public void sendDone(SseEmitter emitter, Long interpretationId, boolean cached) {
        try {
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data("{\"interpretationId\":" + interpretationId + ",\"cached\":" + cached + "}",
                            MediaType.TEXT_EVENT_STREAM));
            emitter.complete();
        } catch (IOException e) {
            log.warn("SSE 推送 done 失败", e);
            completeQuietlyWithError(emitter, e);
        } catch (IllegalStateException e) {
            // 客户端已断开或流已结束：done 帧发不出去就静默收尾，不向上抛（收尾阶段无兜底方）
            log.debug("SSE 推送 done 失败（客户端已断开或流已完成），静默收尾");
        }
    }

    /**
     * 推送错误帧。
     */
    public void sendError(SseEmitter emitter, int code, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"code\":" + code + ",\"message\":\"" + escapeJson(message) + "\"}",
                            MediaType.TEXT_EVENT_STREAM));
            emitter.complete();
        } catch (IOException e) {
            log.warn("SSE 推送 error 失败", e);
            completeQuietlyWithError(emitter, e);
        } catch (IllegalStateException e) {
            // 对已完成/已断开的 emitter 发错误帧会二次抛 ISE 逃逸：静默返回（等同发送前判定 emitter 状态）
            log.debug("SSE 推送 error 失败（客户端已断开或流已完成），跳过错误帧");
        }
    }

    /** 断连收尾：emitter 已完成时 completeWithError 自身也会抛 ISE，一并兜住 */
    private void completeQuietlyWithError(SseEmitter emitter, IOException cause) {
        try {
            emitter.completeWithError(cause);
        } catch (IllegalStateException e) {
            log.debug("SSE emitter 已完成，completeWithError 跳过");
        }
    }

    /** JSON 转义 */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
