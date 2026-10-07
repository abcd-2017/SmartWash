package com.smartwash.toolbox.toolkit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取（用于点击统计的 ip_hash 摘要）。
 * 与 LoginController 的风控取 IP 保持同一安全哲学：应用层头不可无条件采信——
 * 仅当 TCP 直连地址为回环/内网（本机反代、局域网网关等可信形态）时，
 * 才采信 X-Forwarded-For 首个 IP / X-Real-IP；直连部署（当前 demo 形态）一律用直连地址，
 * 防止公网客户端伪造头部污染统计归属。
 */
public final class ClientIpUtil {

    private ClientIpUtil() {
    }

    public static String get(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int commaIdx = xff.indexOf(',');
            return (commaIdx > 0 ? xff.substring(0, commaIdx) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return remoteAddr;
    }

    /** 直连地址为回环或 RFC1918 内网时，视为存在可信反向代理 */
    private static boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        return remoteAddr.startsWith("127.")
                || remoteAddr.equals("::1")
                || remoteAddr.startsWith("10.")
                || remoteAddr.startsWith("192.168.")
                || remoteAddr.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }
}
