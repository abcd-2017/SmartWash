package com.smartwash.divination.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Flux&lt;String&gt; → SseEmitter 桥接器。
 * 将 WebClient 流式响应的每个 chunk 桥接到 SseEmitter 推给客户端。
 *
 * SSE 事件契约：
 *   event: delta   data: {"text":"…流式片段…"}
 *   event: done    data: {"interpretationId":123,"cached":false}
 *   event: error   data: {"code":500,"message":"供应商暂时不可用，已降级"}
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
            emitter.completeWithError(e);
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
            emitter.completeWithError(e);
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
            emitter.completeWithError(e);
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
