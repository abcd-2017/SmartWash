package com.smartwash.divination.prompt;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.smartwash.divination.entity.DivPromptVersion;
import com.smartwash.divination.service.DivPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Prompt packet 组装器。
 * 组装解读请求的完整 prompt：盘面事实（不可改写）+ 规则事实 + 路由引导 + 领域方法 + 用户原问题。
 * 四术各一份版本化 system prompt（骨架同：事实锁定→先主后辅→结论先行→区分事实与推断→免责；术语异）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptAssembler {

    private final DivPromptService promptService;

    /**
     * 组装解读 prompt packet。
     *
     * @param method      术数方法
     * @param serverChart 服务端权威盘面 JSON
     * @param question    用户原问题
     * @return 完整 prompt（system + user）
     */
    public Map<String, String> assemble(String method, String serverChart, String question) {
        // 1. 获取激活的 prompt 版本
        DivPromptVersion promptVersion = promptService.getActivePrompt(method);

        String systemPrompt;
        String methodText;
        if (promptVersion != null) {
            systemPrompt = promptVersion.getSystemPrompt();
            methodText = promptVersion.getMethodText();
        } else {
            // 兜底默认 prompt
            systemPrompt = buildDefaultSystemPrompt(method);
            methodText = buildDefaultMethodText(method);
        }

        // 2. 组装 user prompt
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("【确定性盘面】\n").append(serverChart).append("\n\n");
        userPrompt.append("【领域分析方法】\n").append(methodText).append("\n\n");
        userPrompt.append("【用户问题】\n").append(question).append("\n\n");
        userPrompt.append("请基于盘面事实进行解读，每条断语必须回指盘面字段，文末附免责声明。");

        Map<String, String> packet = new HashMap<>();
        packet.put("system", systemPrompt);
        packet.put("user", userPrompt.toString());
        packet.put("method", method);
        if (promptVersion != null) {
            packet.put("promptVersionId", String.valueOf(promptVersion.getId()));
        }
        return packet;
    }

    private String buildDefaultSystemPrompt(String method) {
        return String.format(
                "你是一位资深的%s解读师。请严格遵循以下规则：\n" +
                        "1. 盘面事实不可改写，所有断语必须基于盘面字段\n" +
                        "2. 先结论后论证，区分事实与推断\n" +
                        "3. 每条断语回指盘面具体字段\n" +
                        "4. 文末必须附免责声明：本解读仅供娱乐参考，不构成任何建议\n" +
                        "5. 不回答医疗/法律/投资等高风险领域问题",
                getMethodName(method)
        );
    }

    private String buildDefaultMethodText(String method) {
        return switch (method) {
            case "liuyao" -> "六爻纳甲：取用神→旺衰（月令+日辰）→动变生克→原神忌神→应期";
            case "meihua" -> "梅花易数：体用（动爻为用静为体）→五行生克→互变卦→卦气旺衰";
            case "qimen" -> "奇门遁甲：用神宫（日干=求测人、时干=事）→星门神→十干克应→格局";
            case "liuren" -> "大六壬：四课三传→十二天将→课体→毕法赋";
            default -> "基于盘面事实进行解读";
        };
    }

    private String getMethodName(String method) {
        return switch (method) {
            case "liuyao" -> "六爻";
            case "meihua" -> "梅花易数";
            case "qimen" -> "奇门遁甲";
            case "liuren" -> "大六壬";
            default -> "术数";
        };
    }
}
