package com.smartwash.divination.service;

import com.smartwash.divination.entity.DivBlockedQuestion;
import com.smartwash.divination.mapper.DivBlockedQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 敏感问题分流服务。
 * 医疗生死、胎儿性别、失踪定位、投资建议等高风险问题：确定性分类器直接转向现实求助，不排盘不解读。
 * 词库命中 → refusal 事件 + 落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivSafetyService {

    private final DivBlockedQuestionMapper blockedQuestionMapper;

    /** 高风险关键词库（可按需扩展或移入管理端配置） */
    private static final List<String> HIGH_RISK_KEYWORDS = Arrays.asList(
            "自杀", "跳楼", "割腕", "不想活", "活不下去", "死",
            "胎儿性别", "怀的是男是女", "生男生女",
            "失踪", "找人", "定位", "跟踪",
            "股票推荐", "投资建议", "买哪只股", "理财建议", "赌博"
    );

    /**
     * 检查问题是否命中高风险分流。
     *
     * @param question 用户原问题
     * @return null=通过（可继续解读）；非 null=命中规则，返回拒绝原因
     */
    public String check(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String lower = question.toLowerCase();
        for (String keyword : HIGH_RISK_KEYWORDS) {
            if (lower.contains(keyword)) {
                log.warn("敏感问题命中分流关键词: {}", keyword);
                return "该问题涉及健康/安全/投资等高风险领域，请咨询专业机构，不提供占卜解读";
            }
        }
        return null;
    }

    /**
     * 记录拦截问题日志。
     */
    public void recordBlocked(Long userId, String question, String method, String reason) {
        DivBlockedQuestion blocked = new DivBlockedQuestion();
        blocked.setUserId(userId);
        blocked.setQuestion(question);
        blocked.setMethod(method);
        blocked.setReason(reason);
        blockedQuestionMapper.insert(blocked);
    }
}
