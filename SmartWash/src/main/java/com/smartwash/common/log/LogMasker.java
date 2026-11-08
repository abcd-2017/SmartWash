package com.smartwash.common.log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志脱敏工具：按字段名对日志文本中的敏感值做掩码（替换为 {@link #MASK}）。
 *
 * <p><b>只改日志，不改报文</b>——本类仅被日志输出方（如 {@link AccessLogFilter}）调用，
 * 对请求/响应报文零感知、零改动。
 *
 * <p>支持两种形态：
 * <ul>
 *   <li>JSON：{@code {"password":"abc","age":18}} —— 正则定位 "字段":值 结构，敏感字段值掩码；</li>
 *   <li>query/form：{@code password=abc&page=1} —— 按 & 分段，敏感参数值掩码。</li>
 * </ul>
 *
 * <p><b>字段匹配策略</b>：字段名归一化（小写、去掉 {@code -}/{@code _}）后做<b>包含</b>匹配，
 * 即 {@code account_balance} 命中清单项 {@code balance}，{@code passwordHash} 命中 {@code password}。
 * 取「宁多勿漏」方向：漏掩码是安全事故，多掩码只损失少量排障信息。
 *
 * <p>默认清单内置常用敏感字段（password/secret/token/captcha/code 等），可通过
 * {@code smartwash.request-log.mask-fields} 追加（见 {@link RequestLogProperties}）。
 * 本类为纯字符串处理、无状态、无第三方依赖，可安全静态单测。
 */
public final class LogMasker {

    /** 敏感值统一掩码占位符 */
    public static final String MASK = "***";

    /** 默认敏感字段清单（全小写小写归一化形式）。code 为验证码字段（UserRegisterFrom/ResetPasswordFrom 的 code） */
    private static final Set<String> DEFAULT_FIELDS = Set.of(
            // 口令类
            "password", "passwd", "pwd", "passwordhash", "confirmpassword", "newpassword", "oldpassword",
            // 密钥/凭据类
            "secret", "secretkey", "clientsecret", "apikey", "accesskey", "accesskeysecret",
            "token", "accesstoken", "refreshtoken", "authorization", "credential",
            // 验证码/取件码类（SmartWash 验证码请求字段名为 code；取件码同属敏感凭证）
            "captcha", "verificationcode", "smscode", "code", "pickupcode",
            // 其他
            "sessionkey", "privatekey");

    /** JSON 形态："字段名":值（值支持带引号字符串/数字/字面量，容忍转义与空白） */
    private static final Pattern JSON_PAIR = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[^,{}\\[\\]]+)");

    /** 掩码后的字段集合（已归一化），contains 语义匹配 */
    private final Set<String> sensitiveFields;

    /** 默认清单构造 */
    public LogMasker() {
        this(null);
    }

    /**
     * 默认清单 + 自定义追加字段构造。
     *
     * @param extraFields 追加的敏感字段名（原始大小写/分隔符均可，内部归一化）；null/空则只用默认清单
     */
    public LogMasker(Collection<String> extraFields) {
        Set<String> merged = new HashSet<>(DEFAULT_FIELDS);
        if (extraFields != null) {
            extraFields.stream()
                    .filter(f -> f != null && !f.isBlank())
                    .map(LogMasker::normalize)
                    .forEach(merged::add);
        }
        this.sensitiveFields = Set.copyOf(merged);
    }

    /**
     * 判断字段名是否敏感（归一化后包含任一清单项）。
     *
     * @param field 原始字段名
     * @return true 表示需要掩码
     */
    public boolean isSensitive(String field) {
        if (field == null || field.isBlank()) {
            return false;
        }
        String normalized = normalize(field);
        return sensitiveFields.stream().anyMatch(normalized::contains);
    }

    /**
     * 脱敏 JSON 形态文本中的敏感字段值。非 JSON 或无匹配时原样返回，畸形输入不抛异常。
     *
     * @param text 日志文本（可为 null）
     * @return 脱敏后文本
     */
    public String maskJson(String text) {
        if (text == null || text.isEmpty() || !text.contains("\"")) {
            return text;
        }
        Matcher matcher = JSON_PAIR.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            String replacement;
            if (isSensitive(key)) {
                // 带引号字符串值替换为 "***"，数字/字面量值替换为 ***，保持 JSON 结构可读
                replacement = "\"" + key + "\":"
                        + (value.startsWith("\"") ? "\"" + MASK + "\"" : MASK);
            } else {
                // 非敏感字段保留原始匹配段（含值内转义序列），不做任何改写
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 脱敏 query/form（k=v&k2=v2）形态文本中的敏感参数值。无键值对结构时原样返回。
     *
     * @param query 查询串/表单体（可为 null）
     * @return 脱敏后文本
     */
    public String maskQuery(String query) {
        if (query == null || query.isEmpty() || !query.contains("=")) {
            return query;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : query.split("&", -1)) {
            int eq = segment.indexOf('=');
            if (eq > 0 && isSensitive(segment.substring(0, eq))) {
                // 键保留原始大小写，值统一掩码
                segments.add(segment.substring(0, eq) + "=" + MASK);
            } else {
                segments.add(segment);
            }
        }
        return String.join("&", segments);
    }

    /** 归一化：小写并去掉 -/_，使 passwordHash/password-hash/password_hash 同一形式 */
    private static String normalize(String field) {
        return field.toLowerCase().replace("-", "").replace("_", "");
    }
}
