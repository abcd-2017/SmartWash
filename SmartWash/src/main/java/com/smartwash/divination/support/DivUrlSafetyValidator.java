package com.smartwash.divination.support;

import com.smartwash.exception.CustomExceptions;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;

/**
 * 外呼 URL 安全校验器（SSRF fail-closed）。
 *
 * 校验链（GLOSSARY §6.4 口径）：
 *   1. 仅 HTTPS（明文 http 直接拒绝）；
 *   2. 拒绝携带 userinfo 的 URL（https://user@host 形式）；
 *   3. 内网域名快拒（localhost / *.localhost / *.local）；
 *   4. 主机做 DNS 解析后，每一个解析 IP 都必须在外网段——
 *      拒绝环回、私网、链路本地、唯一本地（fc00::/7）、CGNAT（100.64/10）、0.0.0.0/8、组播；
 *   5. IP 字面量经 JDK 规范化后同规则校验。
 *
 * 已知边界（ADR-0006）：只覆盖首跳——真实外呼必须 {@code followRedirect(false)}，
 * 否则 https 302 跳内网可绕过全部校验；DNS 重绑定窗口未根治。
 * 校验失败一律抛 {@link CustomExceptions}（fail-closed，不返回宽松结果）。
 */
public final class DivUrlSafetyValidator {

    private DivUrlSafetyValidator() {
    }

    /**
     * 校验外呼 URL 是否安全，不通过直接抛业务异常。
     *
     * @param url 待校验的绝对地址
     */
    public static void check(String url) {
        if (url == null || url.isBlank()) {
            throw new CustomExceptions("链接不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomExceptions("链接格式非法");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            // 仅 https：明文 http 有窃听/篡改风险，其他 scheme（file/ftp/gopher 等）一律拒绝
            throw new CustomExceptions("仅支持 https 链接");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new CustomExceptions("链接缺少主机地址");
        }
        if (uri.getUserInfo() != null) {
            // 拒绝 userinfo：https://user@evil.com 形式可伪装可信域名
            throw new CustomExceptions("链接不允许携带用户信息");
        }

        // 内网域名快拒：不消耗 DNS 解析
        String lower = host.toLowerCase();
        if ("localhost".equals(lower)
                || lower.endsWith(".localhost")
                || lower.endsWith(".local")
                || lower.endsWith(".internal")) {
            throw new CustomExceptions("不允许访问内网地址");
        }

        // 解析全部地址（IP 字面量不触发 DNS 查询），每一个都必须在外网段
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            // 解析失败按不可访问处理（fail-closed），不区分域名不存在与网络故障
            throw new CustomExceptions("链接地址无法解析");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new CustomExceptions("不允许访问内网地址");
            }
        }
    }

    /**
     * 判断单个地址是否为公网地址（非环回/私网/链路本地/唯一本地/CGNAT/0网段/组播）。
     * 包级可见便于单测。
     */
    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()          // 0.0.0.0 / ::
                || address.isLoopbackAddress()   // 127.0.0.0/8 / ::1
                || address.isLinkLocalAddress()  // 169.254.0.0/16 / fe80::/10
                || address.isSiteLocalAddress()  // 10/8、172.16/12、192.168/16 / fec0::/10
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            byte[] octets = address.getAddress();
            int b0 = octets[0] & 0xFF;
            int b1 = octets[1] & 0xFF;
            if (b0 == 0) {
                return false; // 0.0.0.0/8（any-local 仅覆盖单地址，整段一并拒绝）
            }
            // CGNAT 100.64.0.0/10（运营商级 NAT，JDK 未内置判断）
            return !(b0 == 100 && b1 >= 64 && b1 <= 127);
        }
        if (address instanceof Inet6Address) {
            // 唯一本地地址 fc00::/7（isSiteLocalAddress 只覆盖 fec0::/10）
            return (address.getAddress()[0] & 0xFE) != 0xFC;
        }
        return false;
    }
}
