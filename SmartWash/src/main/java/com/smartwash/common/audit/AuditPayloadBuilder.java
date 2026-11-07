package com.smartwash.common.audit;

import com.smartwash.utils.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 审计摘要构建器：把方法入参/返回值序列化为 JSON 摘要。
 *
 * <ul>
 *   <li><b>敏感字段掩码</b>：键名（不区分大小写）含 password/secret/token/passwd/captcha 的字段值
 *       替换为 {@code ***}，防止密码等敏感值经审计落库（红线：无敏感值入库）；</li>
 *   <li><b>超长截断</b>：超过 {@link #MAX_PAYLOAD_LENGTH}（2KB）直接截断——截断后的串可能不是合法
 *       JSON，定位为「人工排查用摘要」而非可反序列化快照；</li>
 *   <li><b>绝不抛异常</b>：序列化失败降级为 {@code 类名(toString)} 摘要，保证审计链路不影响业务。</li>
 * </ul>
 */
@Component
public class AuditPayloadBuilder {

    /** 单段摘要最大长度（字符），约 2KB（中文按 UTF-8 会略超字节，取保守字符数） */
    public static final int MAX_PAYLOAD_LENGTH = 2048;

    private static final String MASK = "***";

    /** 敏感键名关键词（小写包含匹配即掩码） */
    private static final Set<String> SENSITIVE_KEY_WORDS = Set.of("password", "passwd", "secret", "token", "captcha");

    /**
     * 构建入参摘要：入参序列化为 JSON 数组（脱敏 + 截断）。
     * 空入参返回 {@code []}；序列化失败降级为字符串摘要。
     */
    public String buildArgsJson(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        try {
            return truncate(maskSensitive(JsonUtils.toJson(Arrays.asList(args))));
        } catch (Exception e) {
            // 审计绝不影响业务：序列化失败退化为纯文本摘要
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Object arg = args[i];
                sb.append(arg == null ? "null" : arg.getClass().getSimpleName() + "(" + arg + ")");
            }
            return truncate(sb.append("]").toString());
        }
    }

    /**
     * 构建返回值摘要（脱敏 + 截断）；返回值为 null 返回 null。
     */
    public String buildResultJson(Object result) {
        if (result == null) {
            return null;
        }
        try {
            return truncate(maskSensitive(JsonUtils.toJson(result)));
        } catch (Exception e) {
            return truncate(String.valueOf(result));
        }
    }

    /**
     * 敏感字段掩码：解析 JSON 树后按键名脱敏；解析失败返回原文（后续仍有截断兜底）。
     */
    private String maskSensitive(String json) {
        if (json == null || !json.contains("{") && !json.contains("[")) {
            return json;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = JsonUtils.readTree(json);
            maskNode(root);
            return JsonUtils.toJson(root);
        } catch (Exception e) {
            // 非 JSON 结构（理论上不会出现）直接原文返回
            return json;
        }
    }

    /** 递归掩码：对象键命中敏感词则整值替换，数组逐元素递归 */
    private void maskNode(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode obj = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            // 先收集后替换，避免遍历时修改结构
            java.util.List<String> toMask = new java.util.ArrayList<>();
            obj.fieldNames().forEachRemaining(name -> {
                if (isSensitiveKey(name)) {
                    toMask.add(name);
                } else {
                    maskNode(obj.get(name));
                }
            });
            for (String name : toMask) {
                obj.put(name, MASK);
            }
        } else if (node.isArray()) {
            node.forEach(this::maskNode);
        }
    }

    /** 键名（小写）包含任一敏感关键词即命中 */
    static boolean isSensitiveKey(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_WORDS.stream().anyMatch(lower::contains);
    }

    /** 截断到 MAX_PAYLOAD_LENGTH（截断后可能不再是合法 JSON，定位为摘要） */
    static String truncate(String s) {
        if (s == null || s.length() <= MAX_PAYLOAD_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_PAYLOAD_LENGTH);
    }
}
