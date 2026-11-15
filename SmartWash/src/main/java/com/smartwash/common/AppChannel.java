package com.smartwash.common;

import lombok.Getter;

import java.util.Arrays;

/**
 * OTA 分发渠道（同一 Android 工程的两个 product flavor）。
 *
 * <p>full = 全量版（applicationId {@code com.smartwash}）；
 * lite = 精简版「吃啥 / Today Eat」（applicationId {@code com.todayeat.app}）。
 * 两个渠道是独立 App，版本清单按 channel 分组下发，避免互推错包。</p>
 */
@Getter
public enum AppChannel {

    /** 全量版 */
    FULL("full", "全量版"),
    /** 精简版（吃啥 / Today Eat） */
    LITE("lite", "吃啥精简版");

    /** 渠道标识（version.json 的 channels 键、接口 channel 参数取值） */
    private final String code;
    /** 渠道描述（仅日志/错误提示用） */
    private final String description;

    AppChannel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按 code 解析渠道，大小写不敏感、自动 trim。
     *
     * @param code 渠道标识（可空）
     * @return 匹配的渠道；空或无法识别返回 null（由调用方决定缺省/报错）
     */
    public static AppChannel fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        return Arrays.stream(values())
                .filter(channel -> channel.code.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析下发渠道：缺省（空）回落 {@link #FULL}；非法取值返回 null 由调用方报错，
     * 避免端上拼错渠道时静默拿到 full 包。
     *
     * @param code 渠道标识（可空）
     * @return 缺省或合法渠道；非法取值返回 null
     */
    public static AppChannel resolveOrDefault(String code) {
        AppChannel channel = fromCode(code);
        if (channel != null) {
            return channel;
        }
        return (code == null || code.isBlank()) ? FULL : null;
    }
}
