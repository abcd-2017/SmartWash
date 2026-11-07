package com.smartwash.common.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求链路日志配置（{@code smartwash.request-log.*}）。
 *
 * <p>默认值满足开箱即用；{@code application.yaml} 可覆盖慢请求阈值、排除路径，
 * 并向脱敏清单追加字段（默认清单内置在 {@link LogMasker}，此处配置为「追加」而非覆盖）。
 * 注意：本配置只影响 {@link AccessLogFilter} 的出入日志，{@link TraceIdFilter} 的
 * request_id 注入与回写不受开关影响，始终生效。
 */
@ConfigurationProperties(prefix = "smartwash.request-log")
public class RequestLogProperties {

    /** 是否启用出入日志；关闭后仅保留 request_id 链路注入 */
    private boolean accessLogEnabled = true;

    /** 慢请求阈值（毫秒）：达到即在 INFO 日志行追加 (慢请求) 标记（级别恒为 INFO，既有任务约定） */
    private long slowThresholdMs = 2000;

    /** 不做出入日志的路径前缀（纯前缀匹配，配 /actuator 即覆盖其全部子路径） */
    private List<String> excludePaths = List.of("/actuator", "/swagger-ui", "/v3/api-docs", "/favicon.ico");

    /** 追加的敏感掩码字段名（与 LogMasker 内置默认清单合并，而非覆盖） */
    private List<String> maskFields = new ArrayList<>();

    public boolean isAccessLogEnabled() {
        return accessLogEnabled;
    }

    public void setAccessLogEnabled(boolean accessLogEnabled) {
        this.accessLogEnabled = accessLogEnabled;
    }

    public long getSlowThresholdMs() {
        return slowThresholdMs;
    }

    public void setSlowThresholdMs(long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public List<String> getMaskFields() {
        return maskFields;
    }

    public void setMaskFields(List<String> maskFields) {
        this.maskFields = maskFields;
    }
}
