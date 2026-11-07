package com.smartwash.divination.llm;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * LLM 调用结果（流式与非流式统一）。
 */
@Data
@AllArgsConstructor
public class LlmChatResult {

    /** 完整回复文本（流式调用为各增量累积，非流式为整段返回） */
    private String content;

    /** 输入 token 数（供应商未返回 usage 时为 0，不阻塞主流程） */
    private int tokensIn;

    /** 输出 token 数（供应商未返回 usage 时为 0，不阻塞主流程） */
    private int tokensOut;
}
