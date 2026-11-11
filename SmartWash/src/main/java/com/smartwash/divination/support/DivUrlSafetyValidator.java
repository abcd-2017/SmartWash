package com.smartwash.divination.support;

import com.smartwash.exception.CustomExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 观星台外呼 URL 安全校验（SSRF 边界，fail-closed）。
 *
 * <p>校验对象：用户 BYOK 自定义接入点与管理端模型目录的 base_url——两者都会被服务端
 * 真实外呼（连通性试呼 / LLM 转发），任一输入面都不允许指向内网或本机服务。</p>
 *
 * <p>校验链（任一不过即拒绝，异常信息即拒绝原因）：
 * <ol>
 *   <li>结构：仅 HTTPS（LLM 接入点明文 HTTP 会泄露 API Key）、拒绝 userinfo（{@code user:pass@host}）、
 *       host 非空、端口 1-65535；</li>
 *   <li>主机快拒：{@code localhost} 及其后缀形式、{@code *.local} / {@code *.internal}；</li>
 *   <li>DNS 全解析：主机解析出的<strong>每一个</strong> IP 都必须在外网段（多解析记录只要有一个
 *       内网即整体拒绝，杜绝 DNS 轮询把部分流量导向内网）；解析失败按拒绝处理；</li>
 *   <li>IP 判定：环回 / 任意本地 / 链路本地 / RFC1918 私网 / CGNAT(100.64/10) / 0.0.0.0、
 *       组播与保留段 / IPv6 ULA(fc00::/7) 与链路本地(fe80::/10)。</li>
 * </ol></p>
 *
 * <p>已知边界（记录于 GLOSSARY 6.4）：校验通过后到真实外呼之间存在 DNS 重绑定窗口
 * （TOCTOU），彻底根治需外呼时 pin IP + SNI；当前试呼为短时单次外呼，窗口风险可接受，
 * Reactor Netty 默认不跟随重定向（首跳即终跳）。</p>
 */
@Slf4j
@Component
public class DivUrlSafetyValidator {

    /** CGNAT 运营商级 NAT 段 100.64.0.0/10（部分云厂商内网元数据服务落此段语义） */
    private static final int CGNAT_MASK = 0xFFC00000; // /10
    private static final int CGNAT_BASE = 0x64400000; // 100.64.0.0
    /** RFC 2544 网络基准测试保留段 198.18.0.0/15（非公网可路由；Fake-IP 代理劫持常落此段） */
    private static final int BENCHMARK_MASK = 0xFFFE0000; // /15
    private static final int BENCHMARK_BASE = 0xC6120000; // 198.18.0.0
    /** IPv6 唯一本地地址 fc00::/7 的高 7 位 */
    private static final int ULA_V6_PREFIX_HIGH7 = 0xFC;

    /**
     * DNS 全解析层开关（div.ssrf.dns-check，默认开）。仅限本机 Fake-IP 代理调试环境关闭：
     * Fake-IP 模式把一切域名（含不存在域名与真实公网域名）劫持解析到 198.18/15、2001:2::/64
     * benchmark 段，DNS 层校验在该环境必然误报；关闭后仍保留结构校验 + 主机名快拒 + IP 字面量判定
     * （三者均为本地判定，不依赖 DNS）。生产/默认必须开启。
     */
    @Value("${div.ssrf.dns-check:true}")
    private boolean dnsCheckEnabled;

    /** 生产/默认构造：DNS 层校验开启（div.ssrf.dns-check 未配置时 @Value 走字段注入） */
    public DivUrlSafetyValidator() {
    }

    /** 测试与显式装配用：直接指定 DNS 层开关 */
    DivUrlSafetyValidator(boolean dnsCheckEnabled) {
        this.dnsCheckEnabled = dnsCheckEnabled;
    }

    /**
     * 校验外呼 URL，非法抛业务异常（message 即用户可见原因）。
     *
     * @param rawUrl 用户/管理端输入的接入点（应为 https://host[:port][/path]）
     */
    public void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new CustomExceptions("接入点不能为空");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomExceptions("接入点格式不正确");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new CustomExceptions("接入点必须为 HTTPS 地址");
        }
        // userinfo（user:pass@host）既无业务意义，也可能被用于绕过 host 直觉（https://evil@127.0.0.1/）
        if (uri.getRawUserInfo() != null) {
            throw new CustomExceptions("接入点不允许携带用户凭据");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new CustomExceptions("接入点缺少主机名");
        }
        int port = uri.getPort(); // -1 = 未指定（默认 443），合法
        if (port < -1 || port == 0 || port > 65535) {
            throw new CustomExceptions("接入点端口不合法");
        }
        rejectLocalHostnames(host);

        // IP 字面量直接判定（本地判定，不受 dns-check 影响）；域名按开关决定是否做 DNS 全解析
        if (isIpLiteral(host)) {
            for (InetAddress address : parseIpLiteral(host)) {
                if (isForbiddenAddress(address)) {
                    log.info("SSRF 校验拒绝非公网地址, host: {}, resolved: {}", host, address.getHostAddress());
                    throw new CustomExceptions("接入点不允许指向内网或本机地址");
                }
            }
            return;
        }
        if (!dnsCheckEnabled) {
            log.debug("SSRF DNS 层校验已关闭（div.ssrf.dns-check=false，仅限 Fake-IP 调试环境）, host: {}", host);
            return;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isForbiddenAddress(address)) {
                    log.info("SSRF 校验拒绝非公网地址, host: {}, resolved: {}", host, address.getHostAddress());
                    throw new CustomExceptions("接入点不允许指向内网或本机地址");
                }
            }
        } catch (UnknownHostException e) {
            // fail-closed：解析不出的主机不外呼
            log.info("SSRF 校验拒绝不可解析主机: {}", host);
            throw new CustomExceptions("接入点主机无法解析，请检查地址");
        }
    }

    /** 主机名快拒表（DNS 解析前的廉价拦截，含 localhost 变体与 mDNS/内网专用后缀） */
    private void rejectLocalHostnames(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost")
                || lower.endsWith(".local") || lower.endsWith(".internal")
                || lower.endsWith(".home.arpa")) {
            throw new CustomExceptions("接入点不允许指向内网或本机地址");
        }
    }

    /** 判断 host 是否为 IP 字面量（v4 点分或 v6 冒号形式；URI.getHost 对 v6 已去方括号） */
    private boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true; // v6 字面量（含缩写冒号）
        }
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                int v = Integer.parseInt(part);
                if (v < 0 || v > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /** 解析 IP 字面量（经 JDK 规范化，拒绝 1.2.3.04 之类的畸形写法由上面 split 保证） */
    private InetAddress[] parseIpLiteral(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new CustomExceptions("接入点 IP 地址不合法");
        }
    }

    /**
     * 判断单个地址是否为禁往外网段。
     * v4：环回/任意本地/链路本地/私网/组播/CGNAT/wildcard；
     * v6：环回/任意本地/链路本地/组播/ULA(fc00::/7)/IPv4 映射内网。
     */
    private boolean isForbiddenAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address v4) {
            byte[] octets = v4.getAddress();
            int ip = (octets[0] & 0xFF) << 24 | (octets[1] & 0xFF) << 16
                    | (octets[2] & 0xFF) << 8 | (octets[3] & 0xFF);
            // RFC1918 私网
            if (v4.isSiteLocalAddress()) {
                return true;
            }
            // CGNAT 100.64/10、benchmark 198.18/15 与 wildcard 0.0.0.0/8
            if ((ip & CGNAT_MASK) == CGNAT_BASE
                    || (ip & BENCHMARK_MASK) == BENCHMARK_BASE
                    || (ip >>> 24) == 0) {
                return true;
            }
            return false;
        }
        if (address instanceof Inet6Address v6) {
            byte[] bytes = v6.getAddress();
            // ULA fc00::/7（fd00::/8 实际分配段 + fc 保留段）
            if ((bytes[0] & 0xFE) == (ULA_V6_PREFIX_HIGH7 & 0xFE)) {
                return true;
            }
            // RFC 5180 v6 基准测试段 2001:2::/64（非公网可路由；Fake-IP 代理劫持常落此段）
            if (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x00 && bytes[3] == 0x02) {
                return true;
            }
            // IPv4 映射地址 ::ffff:a.b.c.d —— 按内嵌 v4 再判（isIPv4MappedAddress 为包私有，手写字节判定：
            // 前 10 字节为 0、第 11/12 字节为 0xFF）
            boolean v4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    v4Mapped = false;
                    break;
                }
            }
            if (v4Mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                byte[] v4 = new byte[4];
                System.arraycopy(bytes, 12, v4, 0, 4);
                try {
                    return isForbiddenAddress(InetAddress.getByAddress(v4));
                } catch (UnknownHostException e) {
                    // 4 字节数组构造地址不会失败（fail-closed 兜底）
                    return true;
                }
            }
            return false;
        }
        // 未知地址族一律拒绝（fail-closed）
        return true;
    }
}
