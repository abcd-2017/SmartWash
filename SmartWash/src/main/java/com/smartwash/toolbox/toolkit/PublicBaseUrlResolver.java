package com.smartwash.toolbox.toolkit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 短链对外域名解析器。
 *
 * <p>取值优先级：显式配置 {@code toolbox.public-base-url} &gt; 按当前请求推导。
 * 未配置时推导结果为 {@code scheme} + {@code Host}，使短链默认落在后端自身对外的地址上——
 * 直连部署与反向代理部署都无需逐环境配置，也只有「短链域名与 API 域名不一致」
 * （如 API 在 {@code api.x.com}、短链在 {@code s.x.com}）时才需要显式配置。</p>
 *
 * <p>为什么不能写死后端自身地址：后端无法感知自己的对外身份。经反向代理时，
 * 容器看到的 Host 是内网上游名、协议是内网 HTTP，而短链要被公网浏览器/扫码设备打开，
 * 必须用客户端实际使用的域名。</p>
 *
 * <p>安全：反代场景仅当 TCP 直连地址为回环/内网（可信反代形态）时才采信
 * {@code X-Forwarded-Host} / {@code X-Forwarded-Proto}，与 {@link ClientIpUtil} 同一哲学。
 * 这两个头部公网客户端可随意伪造，无条件采信会把短链指向任意域名（钓鱼面）。</p>
 */
@Component
public class PublicBaseUrlResolver {

    /** 显式配置的对外域名前缀；空白表示按请求推导 */
    @Value("${toolbox.public-base-url:}")
    private String configuredBaseUrl;

    /**
     * 解析对外域名前缀（不含尾斜杠）。
     * 无法确定时返回空串，由调用方决定降级策略。
     */
    public String resolve(HttpServletRequest request) {
        if (StringUtils.hasText(configuredBaseUrl)) {
            return stripTrailingSlash(configuredBaseUrl);
        }
        return deriveFromRequest(request);
    }

    private String deriveFromRequest(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String host = resolveHost(request);
        if (!StringUtils.hasText(host)) {
            return "";
        }
        return resolveScheme(request) + "://" + host;
    }

    /** 对外 Host：可信反代时取 X-Forwarded-Host 首段，否则取 Host 头 */
    private String resolveHost(HttpServletRequest request) {
        if (ClientIpUtil.isTrustedProxy(request.getRemoteAddr())) {
            String forwarded = request.getHeader("X-Forwarded-Host");
            if (StringUtils.hasText(forwarded)) {
                return firstSegment(forwarded);
            }
        }
        return request.getHeader("Host");
    }

    /** 对外协议：可信反代时取 X-Forwarded-Proto 首段，否则取实际协议 */
    private String resolveScheme(HttpServletRequest request) {
        if (ClientIpUtil.isTrustedProxy(request.getRemoteAddr())) {
            String proto = request.getHeader("X-Forwarded-Proto");
            if (StringUtils.hasText(proto)) {
                return firstSegment(proto);
            }
        }
        return request.getScheme();
    }

    /** 多级代理时头部为逗号分隔列表，最左段为原始客户端请求的值 */
    private static String firstSegment(String value) {
        String trimmed = value.trim();
        int commaIdx = trimmed.indexOf(',');
        return (commaIdx > 0 ? trimmed.substring(0, commaIdx) : trimmed).trim();
    }

    private static String stripTrailingSlash(String base) {
        String trimmed = base.trim();
        return (trimmed.endsWith("/") && trimmed.length() > 1)
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }
}
