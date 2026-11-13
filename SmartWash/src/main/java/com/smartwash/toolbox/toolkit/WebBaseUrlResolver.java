package com.smartwash.toolbox.toolkit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Web 前端对外地址解析器（私密文本分享链接等 Web SPA 公开页的域名来源）。
 *
 * <p>与 {@link PublicBaseUrlResolver} 不同：Web SPA 与后端 API 不同源，无法从请求推导，
 * 只认显式配置 {@code toolbox.web-base-url}；未配置返回空串，由调用方降级
 * （如私密文本 secretUrl 置空、端上仅展示短码）。生产必须 HTTPS。</p>
 */
@Component
public class WebBaseUrlResolver {

    /** 显式配置的 Web 对外地址前缀；空白表示未配置 */
    @Value("${toolbox.web-base-url:}")
    private String configuredWebBaseUrl;

    /**
     * 解析 Web 对外地址前缀（不含尾斜杠）。
     * 未配置时返回空串，由调用方决定降级策略。
     */
    public String resolve() {
        if (!StringUtils.hasText(configuredWebBaseUrl)) {
            return "";
        }
        String trimmed = configuredWebBaseUrl.trim();
        return (trimmed.endsWith("/") && trimmed.length() > 1)
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }
}
