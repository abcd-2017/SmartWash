package com.smartwash.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

/**
 * 客户端真实 IP 解析器（限流键兜底维度）。
 *
 * <p>规则与 {@code LoginController#getClientIp} 同一套可信代理语义，共用同一配置键
 * {@code security.trusted-proxy-cidrs}：仅当 TCP 直连地址落在可信代理网段内才采信
 * X-Forwarded-For / X-Real-IP 头；直连部署下这些头完全由客户端控制，一律使用直连地址，
 * 防止伪造头部轮换绕过限流，或伪造受害者出口 IP 定向触发误限。
 *
 * <p>独立成类的原因：限流组件自包含（纯增量，不改动既有登录风控代码）；后续若做公共
 * 抽取，两处可合并到同一工具类。
 */
@Slf4j
public class ClientIpResolver {

    /** 可信反向代理网段（IPv4 CIDR，逗号分隔；默认空 = 无可信代理，头信息一律不采信） */
    private final String trustedProxyCidrs;

    public ClientIpResolver(@Value("${security.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs;
    }

    /**
     * 解析客户端真实 IP。请求对象为 null（非 Web 线程）时返回 null，由调用方退化为 unknown。
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return remoteAddr;
    }

    /** 判断直连地址是否落在可信代理网段（IPv4 CIDR 匹配；未配置网段或地址非法时一律视为不可信） */
    private boolean isTrustedProxy(String ip) {
        if (!StringUtils.hasText(trustedProxyCidrs) || !StringUtils.hasText(ip)) {
            return false;
        }
        Long ipLong = ipv4ToLong(ip);
        if (ipLong == null) {
            return false;
        }
        for (String cidr : trustedProxyCidrs.split(",")) {
            String[] parts = cidr.trim().split("/");
            if (parts.length != 2) {
                continue;
            }
            Long network = ipv4ToLong(parts[0]);
            if (network == null) {
                continue;
            }
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // 服务端自身配置的 CIDR 前缀格式非法，跳过并预警
                log.warn("限流可信代理网段配置解析失败，该网段被跳过, cidr: {}", cidr);
                continue;
            }
            if (prefix < 0 || prefix > 32) {
                continue;
            }
            long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            if ((ipLong & mask) == (network & mask)) {
                return true;
            }
        }
        return false;
    }

    /** IPv4 点分十进制转 long；格式非法返回 null（IPv6 不支持 CIDR 匹配，视为不可信代理）。 */
    private Long ipv4ToLong(String ip) {
        String[] segments = ip.split("\\.");
        if (segments.length != 4) {
            return null;
        }
        long result = 0;
        for (String segment : segments) {
            int value;
            try {
                value = Integer.parseInt(segment);
            } catch (NumberFormatException e) {
                // 来源头（X-Forwarded-For 等）可被客户端伪造注入非法格式，仅 debug 留痕防日志刷屏
                log.debug("限流 IP 地址段解析失败，视为非法 IP, ip: {}", ip);
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
            result = (result << 8) | value;
        }
        return result;
    }
}
