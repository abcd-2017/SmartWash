package com.smartwash.common.client;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 客户端平台上下文（不可变对象）。
 *
 * <p>由 {@link ClientContextFilter} 从统一请求头解析产出，供登录日志、访问日志与业务代码消费：
 * <ul>
 *   <li>{@code X-Platform}：android / ios / harmonyos / web；</li>
 *   <li>{@code X-App-Channel}：full / lite（同一 Android 工程的两个 product flavor，见 ADR-0011）；</li>
 *   <li>{@code X-App-Version}：App 版本号；</li>
 *   <li>{@code X-Network-Type}：wifi / cellular / none；</li>
 *   <li>{@code User-Agent}：标准 UA。</li>
 * </ul>
 *
 * <p><b>缺省与归一化</b>：请求头缺失或非法时退化为默认值（platform / appChannel / networkType = {@code unknown}），
 * 绝不抛异常阻断请求；platform / appChannel / networkType 大小写不敏感，统一转小写。
 * 项目渠道与平台是两个正交维度：变体标识（full/lite）不塞进 {@code X-Platform}，
 * 避免破坏既有封闭合法集（ADR-0011）。
 * UA 与版本号做长度截断（UA 最长 500、版本号最长 64），UA 中的 CR/LF/Tab 替换为空格，
 * 防止客户端伪造换行污染日志/展示。
 *
 * <p>不可变：全字段 final、无 setter，线程安全。
 */
public final class ClientContext {

    public static final String HEADER_PLATFORM = "X-Platform";
    /** 项目渠道请求头（full=全量版 / lite=吃啥精简版） */
    public static final String HEADER_APP_CHANNEL = "X-App-Channel";
    public static final String HEADER_APP_VERSION = "X-App-Version";
    public static final String HEADER_NETWORK_TYPE = "X-Network-Type";
    public static final String HEADER_USER_AGENT = "User-Agent";

    /** 平台缺省值：未携带或非法 */
    public static final String PLATFORM_UNKNOWN = "unknown";
    /** 项目渠道缺省值：未携带或非法 */
    public static final String CHANNEL_UNKNOWN = "unknown";
    /** 网络类型缺省值：未携带或非法 */
    public static final String NETWORK_TYPE_UNKNOWN = "unknown";

    /** 合法平台标识（ADR-0011） */
    private static final Set<String> PLATFORMS = Set.of("android", "ios", "harmonyos", "web");
    /** 合法项目渠道：同一 Android 工程的两个 product flavor */
    private static final Set<String> APP_CHANNELS = Set.of("full", "lite");
    /** 合法网络类型（ADR-0011） */
    private static final Set<String> NETWORK_TYPES = Set.of("wifi", "cellular", "none");

    /** UA 列宽 500，超出截断 */
    private static final int MAX_UA_LENGTH = 500;
    /** 版本号列宽 64，超出截断 */
    private static final int MAX_APP_VERSION_LENGTH = 64;

    private final String platform;
    private final String appChannel;
    private final String appVersion;
    private final String networkType;
    private final String userAgent;

    private ClientContext(String platform, String appChannel, String appVersion, String networkType, String userAgent) {
        this.platform = platform;
        this.appChannel = appChannel;
        this.appVersion = appVersion;
        this.networkType = networkType;
        this.userAgent = userAgent;
    }

    /**
     * 从请求头值构造上下文（纯函数，可静态单测）。
     *
     * @param platform     X-Platform 原始值（可空）
     * @param appChannel   X-App-Channel 原始值（可空）
     * @param appVersion   X-App-Version 原始值（可空）
     * @param networkType  X-Network-Type 原始值（可空）
     * @param userAgent    User-Agent 原始值（可空）
     * @return 归一化后的不可变上下文
     */
    public static ClientContext fromHeaders(String platform, String appChannel, String appVersion, String networkType, String userAgent) {
        return new ClientContext(
                normalizePlatform(platform),
                normalizeAppChannel(appChannel),
                truncate(appVersion, MAX_APP_VERSION_LENGTH),
                normalizeNetworkType(networkType),
                sanitizeUserAgent(userAgent));
    }

    /** 归一化平台：trim + 小写；空或非法回退 unknown */
    private static String normalizePlatform(String platform) {
        String value = lowerTrim(platform);
        return PLATFORMS.contains(value) ? value : PLATFORM_UNKNOWN;
    }

    /** 归一化项目渠道：trim + 小写；空或非法回退 unknown（旁路信息，不阻断请求） */
    private static String normalizeAppChannel(String appChannel) {
        String value = lowerTrim(appChannel);
        return APP_CHANNELS.contains(value) ? value : CHANNEL_UNKNOWN;
    }

    /** 归一化网络类型：trim + 小写；空或非法回退 unknown */
    private static String normalizeNetworkType(String networkType) {
        String value = lowerTrim(networkType);
        return NETWORK_TYPES.contains(value) ? value : NETWORK_TYPE_UNKNOWN;
    }

    /** trim + 小写；空返回空串（便于集合判定） */
    private static String lowerTrim(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** trim；空返回 null（DB 列可空），超长截断 */
    private static String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /** UA 清洗：CR/LF/Tab 替换为空格（防日志/页面注入），再截断；空返回 null */
    private static String sanitizeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        String sanitized = userAgent.trim().replaceAll("[\\r\\n\\t]", " ");
        return sanitized.length() <= MAX_UA_LENGTH ? sanitized : sanitized.substring(0, MAX_UA_LENGTH);
    }

    public String getPlatform() {
        return platform;
    }

    public String getAppChannel() {
        return appChannel;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getNetworkType() {
        return networkType;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
