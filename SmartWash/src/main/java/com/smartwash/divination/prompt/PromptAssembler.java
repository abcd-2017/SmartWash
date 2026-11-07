package com.smartwash.divination.prompt;

import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivPromptVersion;
import com.smartwash.divination.service.DivPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt packet 组装器。
 * 组装解读请求的完整 prompt：盘面事实（不可改写）+ 规则事实 + 路由引导 + 领域方法 + 用户原问题。
 * 四术各一份版本化 system prompt（骨架同：事实锁定→先主后辅→结论先行→区分事实与推断→免责；术语异）。
 *
 * <p>packet 键约定：
 * <ul>
 *   <li>{@code system} — system prompt（激活版本内容；无激活版本时为兜底默认）</li>
 *   <li>{@code user} — user prompt（盘面 + 领域方法 + 问题；追问时含历史解读上下文；RAG top-k 拼接位预留在盘面段之后）</li>
 *   <li>{@code method} — 术数方法</li>
 *   <li>{@code promptVersionId} — 激活版本 id（仅存在激活版本时写入，供 div_interpretation 落库）</li>
 * </ul></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptAssembler {

    /** 追问上下文保留的最近追问轮数（更早轮次以占位符略去） */
    private static final int FOLLOWUP_KEEP_ROUNDS = 5;

    /** 追问上下文总长上限（字符数）：首次解读全文优先保留，超出时自旧向新舍弃追问轮次（最近一轮始终保留） */
    private static final int FOLLOWUP_CONTEXT_MAX_CHARS = 8000;

    /** 更早追问轮次的截断占位文案 */
    private static final String OLDER_FOLLOWUP_PLACEHOLDER = "（更早追问略）";

    private final DivPromptService promptService;

    /**
     * 组装首次解读 prompt packet。
     *
     * @param method      术数方法
     * @param serverChart 服务端权威盘面 JSON
     * @param question    用户原问题（可为空，空时以空串占位；首次解读入参为空时由调用方回退卦例自身问题）
     * @return prompt packet（system/user/method/promptVersionId）
     */
    public Map<String, String> assemble(String method, String serverChart, String question) {
        DivPromptVersion promptVersion = promptService.getActivePrompt(method);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("【确定性盘面】\n").append(serverChart).append("\n\n");
        userPrompt.append("【领域分析方法】\n").append(methodTextOf(promptVersion, method)).append("\n\n");
        userPrompt.append("【用户问题】\n").append(nullSafe(question)).append("\n\n");
        userPrompt.append("请基于盘面事实进行解读，每条断语必须回指盘面字段，文末附免责声明。");

        return buildPacket(method, systemPromptOf(promptVersion, method), userPrompt.toString(), promptVersion);
    }

    /**
     * 组装追问 prompt packet：在首次解读的 packet 骨架上，把历史解读轮次拼进 user prompt。
     *
     * <p>上下文控制：首次解读全文保留 + 最近 5 轮追问全文，更早轮次以「（更早追问略）」占位；
     * 「首次解读 + 已选轮次」总长超过 8000 字符时自旧向新舍弃追问轮次（最近一轮始终保留），
     * 保证追问语义成立的同时防上下文超限。</p>
     *
     * @param method           术数方法
     * @param serverChart      服务端权威盘面 JSON（追问沿用起卦时刻盘面）
     * @param followupQuestion 本次追问的子问题
     * @param history          该卦例此前解读记录（首轮 + 历次追问，按时间升序）
     * @return prompt packet（system/user/method/promptVersionId）
     */
    public Map<String, String> assembleFollowup(String method, String serverChart,
                                                String followupQuestion, List<DivInterpretation> history) {
        DivPromptVersion promptVersion = promptService.getActivePrompt(method);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("【确定性盘面】\n").append(serverChart).append("\n\n");
        userPrompt.append("【领域分析方法】\n").append(methodTextOf(promptVersion, method)).append("\n\n");
        userPrompt.append("【历史解读过程】\n").append(buildFollowupHistory(history)).append("\n\n");
        userPrompt.append("【本次追问】\n").append(nullSafe(followupQuestion)).append("\n\n");
        userPrompt.append("请基于盘面事实与历史解读进行回应，每条断语必须回指盘面字段，文末附免责声明。");

        return buildPacket(method, systemPromptOf(promptVersion, method), userPrompt.toString(), promptVersion);
    }

    /**
     * 构建追问历史上下文文本：首次解读全文 + 最近 N 轮追问全文，更早轮次占位略去，并控总长。
     *
     * @param history 此前解读记录（按时间升序；正常应恰含一条 kind=first 的首轮解读）
     * @return 历史上下文文本（history 为空时返回占位说明）
     */
    private String buildFollowupHistory(List<DivInterpretation> history) {
        if (history == null || history.isEmpty()) {
            return "（暂无历史解读）";
        }

        // 升序分离：最早的一条 first 视为首轮解读，其余均为追问轮次
        DivInterpretation first = null;
        List<DivInterpretation> followups = new ArrayList<>();
        for (DivInterpretation row : history) {
            if (first == null && "first".equals(row.getKind())) {
                first = row;
            } else {
                followups.add(row);
            }
        }

        // 首次解读全文优先保留（不参与截断）
        String firstText = first == null ? "" : renderFirstRound(first);

        // 按时间正序渲染各追问轮次（轮次编号用绝对序号，截断窗口不改变编号语义）
        List<String> roundTexts = new ArrayList<>(followups.size());
        for (int i = 0; i < followups.size(); i++) {
            roundTexts.add(renderFollowupRound(followups.get(i), i + 1));
        }

        // 自最近一轮向前扩大全文窗口：轮数不超过上限，且「首轮 + 已选轮次」总长不超预算；
        // 最近一轮始终保留（保证追问至少能带上最新上下文）
        int start = roundTexts.size();
        int used = firstText.length();
        while (start > 0 && (roundTexts.size() - start) < FOLLOWUP_KEEP_ROUNDS) {
            int len = roundTexts.get(start - 1).length();
            if (start < roundTexts.size() && used + len > FOLLOWUP_CONTEXT_MAX_CHARS) {
                break;
            }
            used += len;
            start--;
        }

        StringBuilder sb = new StringBuilder();
        if (!firstText.isEmpty()) {
            sb.append(firstText).append("\n\n");
        }
        if (start > 0) {
            sb.append(OLDER_FOLLOWUP_PLACEHOLDER).append("\n\n");
        }
        for (int i = start; i < roundTexts.size(); i++) {
            sb.append(roundTexts.get(i)).append("\n\n");
        }
        return sb.toString().strip();
    }

    /** 渲染首轮解读轮次（全文保留） */
    private String renderFirstRound(DivInterpretation first) {
        return "【首轮解读】\n" + nullSafe(first.getContentMd());
    }

    /** 渲染单条追问轮次（含该轮子问题与解读全文） */
    private String renderFollowupRound(DivInterpretation row, int roundNo) {
        return "【第" + roundNo + "轮追问】问题：" + nullSafe(row.getQuestion())
                + "\n解读：" + nullSafe(row.getContentMd());
    }

    /** 按 method 取激活版本，无激活版本时回退兜底默认 system prompt */
    private String systemPromptOf(DivPromptVersion promptVersion, String method) {
        return promptVersion != null ? promptVersion.getSystemPrompt() : buildDefaultSystemPrompt(method);
    }

    /** 按 method 取激活版本领域方法文本，无激活版本时回退兜底默认 */
    private String methodTextOf(DivPromptVersion promptVersion, String method) {
        return promptVersion != null ? promptVersion.getMethodText() : buildDefaultMethodText(method);
    }

    /** 统一产出 packet：无激活版本时不写 promptVersionId 键，由调用方落 0 */
    private Map<String, String> buildPacket(String method, String systemPrompt, String userPrompt,
                                            DivPromptVersion promptVersion) {
        Map<String, String> packet = new HashMap<>();
        packet.put("system", systemPrompt);
        packet.put("user", userPrompt);
        packet.put("method", method);
        if (promptVersion != null) {
            packet.put("promptVersionId", String.valueOf(promptVersion.getId()));
        }
        return packet;
    }

    private String nullSafe(String text) {
        return text == null ? "" : text;
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
