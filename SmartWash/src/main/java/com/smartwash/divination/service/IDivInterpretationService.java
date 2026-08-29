package com.smartwash.divination.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 解读服务接口：分流→缓存→组装→SSE→落库。
 */
public interface IDivInterpretationService {

    /**
     * SSE 流式解读。
     *
     * @param recordId  卦例 ID
     * @param question  用户原问题（首次解读时与卦例问题相同，追问时为子问题）
     * @param userId    当前用户 ID
     * @param isFollowup 是否追问
     * @return SseEmitter（流式推送 delta/done/error 帧）
     */
    SseEmitter interpret(Long recordId, String question, Long userId, boolean isFollowup);
}
